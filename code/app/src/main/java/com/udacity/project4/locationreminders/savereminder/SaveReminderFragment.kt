package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.location.Geofence.NEVER_EXPIRE
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.geofence.GeofenceBroadcastReceiver
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import timber.log.Timber
import com.google.android.gms.location.LocationServices


@SuppressLint("UnspecifiedImmutableFlag")
class SaveReminderFragment : BaseFragment() {

    // get the view model (from Koin) this time as a singleton to be shared with another fragment
    override val _viewModel: SaveReminderViewModel by inject()

    // data binding of underlying layout
    private lateinit var binding: FragmentSaveReminderBinding

    // permission checking request
    // ... need foreground and background access to user location information
    private lateinit var permissionsToBeChecked: List<String>
    private lateinit var permissionsToBeGranted: MutableList<String>

    // register lambda for when the permissions CHECKING activity returns
    private lateinit var activityResultLauncherForLocationPermissionCheck: ActivityResultLauncher<String>

    // lambda for contract 'StartIntentSenderForResult', which is used (with the PendingIntent
    // 'ResolvableException') to resolve a 'resolvable exception' (...)
    // ... see: https://stackoverflow.com/questions/65158308/deprecated-onactivityresult-in-androidx
    private val resolutionForResult =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {

                activityResult ->

            // check if the user has clicked on "No thanks" or "OK"
            when(activityResult.resultCode) {

                    Activity.RESULT_OK -> {
                        // location settings now on --> continue in flow
                        checkDeviceLocationSettingsAndStartGeofence()
                    }

                else -> {

                    // user declined (location settings remains 'off')
                    // --> re-check without resolution
                    checkDeviceLocationSettingsAndStartGeofence(false)

                }

            }

        }

    // geoFencing
    private lateinit var geofencingClient: GeofencingClient

    // assemble reminder data item - this creates the ID we can use as geoFence ID
    private lateinit var daReminder: ReminderDataItem


    // A PendingIntent for the Broadcast Receiver that handles geofence transitions.
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEOFENCE_EVENT
        // Use FLAG_UPDATE_CURRENT so that you get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }


    // create fragment view
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // inflate fragment layout and return binding object
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_save_reminder, container, false)

        setDisplayHomeAsUpEnabled(true)

        // provide (injected) viewModel as data source for data binding
        binding.viewModel = _viewModel

        // initialize geoFencing
        // ... see: https://developer.android.com/training/location/geofencing
        geofencingClient = LocationServices.getGeofencingClient(requireActivity())

        // install lambda function to be called upon return from activities launched by this code
        registerActivityResultForLocationPermissionChecking()

        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.lifecycleOwner = this

        // clicking on the 'selectLocation' textView takes you to the fragment "select location"
        // ... by means of the observer function of MutableLiveData element 'navigationCommand'
        //     --> see BaseFragment.kt... where the observer (lambda) is installed
        binding.selectLocation.setOnClickListener {
            //            Navigate to another fragment to get the user location
            _viewModel.navigationCommand.value =
                NavigationCommand.To(
                    SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment()
                )
        }

        // clicking on the 'saveReminder' FAB...
        // ... installs the geofencing request and triggers the saving to DB
        binding.saveReminder.setOnClickListener {

            // initialize data record to be written to DB
            // ... if no better values have been provided by the user (taken from viewModel), this
            //     is going to be the data record written to the DB
            daReminder = ReminderDataItem(
                _viewModel.reminderTitle.value ?: "mystery",
                _viewModel.reminderDescription.value ?: "something happening here",
                _viewModel.reminderSelectedLocationStr.value ?: "mystery location",
                _viewModel.latitude.value ?: -1.0,
                _viewModel.longitude.value ?: -1.0,
            )

            // check if required permissions have already been granted to this code (required for
            // geoFencing: access to location information, even when the code is in the background)
            //
            // ... if so, proceed with checking if the user has switched on access to location info
            // ... ultimately, proceed to saving the reminder in the local DB
            checkPermissionsAndStartGeofencing()

        }  // onClickListener (FAB - save)

    }  // onViewCreated


    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }


    // registration of lambda function to be called when the permission CHECKING activity returns
    private fun registerActivityResultForLocationPermissionChecking() {

        // assemble array of permissions - always ask for ACCESS_FINE_LOCATION permission
        permissionsToBeChecked = listOf(Manifest.permission.ACCESS_FINE_LOCATION)

        // ... from Android API level "Q" on, this needs to be checked in addition to foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToBeChecked = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }

        // starting with a full list to be granted - ticked off one by one
        permissionsToBeGranted = permissionsToBeChecked.toMutableList()


        // use RequestPermission contract to register a handler (lambda) for permission launcher
        // user location permission checker as recommended for fragments from androidx.fragment
        // (= JetPack libraries), version 1.3.0 on (using 1.3.6).
        //
        // Motivated by...
        // https://developer.android.com/training/permissions/requesting#allow-system-manage-request-code
        // also see:
        // https://medium.com/codex/android-runtime-permissions-using-registerforactivityresult-68c4eb3c0b61
        activityResultLauncherForLocationPermissionCheck = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
                granted ->

            // lambda, which is called when the permissions setting activity 'returns' a result
            // ... replaces 'onRequestPermissionsResult'
            Timber.d("in lambda function which is called upon a Permission Check activity result.")

            // evaluate the activity result (in this case --> activity: single permission check)


            // continue with flow based on permission check result
            when (granted) {

                true -> {

                    // tick off permission from list of permissions to be checked
                    permissionsToBeGranted.removeAt(0)

                    // keep checking, until there's nothing more to be checked
                    checkPermissionsAndStartGeofencing()

                }

                else -> {

                    // at least one of the required permission is currently denied
                    // inform user about consequences and activate geoFence by-pass
                    Snackbar.make(
                        binding.clSaveReminderFragment,
                        R.string.full_location_permission_denied_consequence,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.ok) {

                            // activate geoFence by-pass
                            _viewModel.geoFencingOn.value = false

                            // continue flow - (will now omit the geoFencing part)
                            checkPermissionsAndStartGeofencing()

                        }.show()

                }  // when->else (permissions NOK)

            }  // when

        }  // activityResult (lambda)

    }


    // ENTRY POINT of the following flow (triggered by click on 'save'):
    //
    // (1) permission checking (foreground & background access to user location)
    //     (1b) permission request
    // (2) check if location is enabled (on the device)
    //     (2b) (attempt to) resolve missing location
    // (3) set geoFence at reminder location
    // (4) save reminder location in DB
    private fun checkPermissionsAndStartGeofencing() {

        // respect the user's choices --> they didn't wanna share their location
        if (_viewModel.geoFencingOn.value == false) {

            // user doesn't wanna share necessary location info --> by-pass geoFencing
            _viewModel.showToast.value =
                "Not GeoFencing reminder ${daReminder.title} at ${daReminder.location}"

            // store reminder in DB
            // ... this also takes the user back to the ReminderListFragment
            _viewModel.validateAndSaveReminder(daReminder)

        } else {

            // geoFencing not ruled out (by the user, who doesn't wanna share their location)

            //  Android 11 on, permissions need to be checked in an incremental way
            // ... foreground access --> background access
            // ... remove granted permissions from the array to be checked
            //
            // ultimately, we should return to here with no more permissions to be granted
            if (permissionsToBeGranted.isEmpty()) {

                // all permissions have been checked/granted --> continue in flow
                // ... pass 'geoFencingOn' in as 'resolve' parameter --> avoid bugging the user
                //     with repeated resolution prompts (when they have already consciously taken
                //     an action that inhibits geoFencing - permissions or settings)
                checkDeviceLocationSettingsAndStartGeofence(
                    _viewModel.geoFencingOn.value ?: true
                )

            } else {

                // still some permissions missing --> fetch next permission
                val daPermission = permissionsToBeGranted.first()

                // check permission - possibly 'educate user' - request permission - continue flow
                when {

                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        daPermission
                    ) == PackageManager.PERMISSION_GRANTED -> {
                        // keep checking until all required permissions have been granted
                        Timber.i("Permission already granted: $daPermission")

                        // remove already granted permission
                        permissionsToBeGranted.removeAt(0)

                        // keep going until we're through the entire list to be granted
                        checkPermissionsAndStartGeofencing()
                    }

                    shouldShowRequestPermissionRationale(daPermission) -> {
                        // in an educational UI, explain to the user why your code requires this
                        // permission for a specific feature to behave as expected. In this UI,
                        // include a "cancel" or "no thanks" button that allows the user to
                        // continue using your code without granting the permission.
                        Snackbar.make(
                            binding.clSaveReminderFragment,
                            R.string.full_location_permission_explanation,
                            Snackbar.LENGTH_INDEFINITE
                        )
                            .setAction(R.string.settings) {

                                // displays system dialog (settings) to invite the user to set the
                                // right permissions
                                // ... still have to request them one by one from Android 11 on
                                activityResultLauncherForLocationPermissionCheck.launch(daPermission)

                            }.show()
                    }

                    else -> {
                        // you can directly ask for the permission.
                        // The registered ActivityResultCallback gets the result of this request.
                        activityResultLauncherForLocationPermissionCheck.launch(daPermission)
                    }

                }  // when

            }  // else: still permissions missing

        }  // else: user doesn't wanna share their location (--> by-pass geoFencing)

    }  // checkPermissionsAndStartGeofencing


    // check if access to user location is currently enabled
    //
    // ... if so, trigger registration of geoFence
    // ... if not, inform user that this is needed to register a geoFence
    //
    // ... flag "resolve" allows this function to be used in two forms:
    //     resolve = true:   (= default) try to automatically resolve access to user location
    //     resolve = false:  prompt user and take them to settings
    //
    private fun checkDeviceLocationSettingsAndStartGeofence(resolve:Boolean = true) {

        // check current location settings - install callback handlers for 'location on/off'
        // ... ref-1: https://developer.android.com/training/location/change-location-settings
        // ... ref-2: https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        // get gms SettingsClient
        val client = LocationServices.getSettingsClient(requireActivity())

        // trigger location settings check - creates a task that can be waited on
        val task = client.checkLocationSettings(builder.build())


        // register listener to handle the case that user location settings are not satisfied
        task.addOnFailureListener { exception ->

            // showing the user a dialog to fix incorrec settings
            // ... this can be bypassed by setting 'resolve' to 'false' when calling this method
            if (exception is ResolvableApiException && resolve) {

                // resolution by user interaction (settings)
                try {

                    // show dialog by calling startIntentSenderForResult with the resolution from
                    // the exception (here: location settings 'off')
                    val intentSenderRequest =
                        IntentSenderRequest.Builder(exception.resolution).build()
                    resolutionForResult.launch(intentSenderRequest)

                } catch (sendEx: IntentSender.SendIntentException) {

                    // exception --> issue debug message
                    Timber.d("Error getting location settings resolution: " + sendEx.message)

                }

            } else {

                // issue an explanation about the consequences and try again
                Snackbar.make(
                    binding.clSaveReminderFragment,
                    R.string.location_access_explanation,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.ok) {

                    // continue in flow (by-passing geoFencing)
                    _viewModel.geoFencingOn.value = false
                    checkPermissionsAndStartGeofencing()

                }.show()


            }  // else: issue explanation, prior to taking the user to settings

        }  // user location currently OFF


        // register listener to handle the case that user location is currently ON
        task.addOnSuccessListener  {
            // ready to add geofence
            addGeofencingRequest()
        }

    }


    // check permissions which are needed for GeoFencing
    // ... if not already given, request permission
    private fun addGeofencingRequest() {

        // Android Studio issue:
        // ... Android Studio suggests an error on adding the geoFence (below), if this 'explicit'
        //     check is replaced by a call to 'foregroundAndBackgroundLocationPermissionApproved()'
        //     (... which is the same code!)
        // ... the IDE obviously needs to see an explicit call to 'checkSelfPermission' in the
        //     execution flow prior to the call to 'addGeoFences'

        // start with assumption that all required permissions have been granted
        // ... innocent until proven guilty
        var permissionsGranted = true

        // any missing permission invalidates the aggregated result
        permissionsToBeChecked.forEach {
            permissionsGranted = permissionsGranted && ActivityCompat.checkSelfPermission(
                requireContext(),
                it, // the permission string
            ) == PackageManager.PERMISSION_GRANTED
        }

        // check permission status
        if (!permissionsGranted) {

            // need permissions for foreground and background access to location
            checkPermissionsAndStartGeofencing()

        } else {

            // required permissions have been granted
            //
            // --> add geoFencing request to location
            Timber.i("Access to BACKGROUND location granted --> add geoFencing request")

            // define geoFence perimeter in geoFencing object
            val geoFenceObj = Geofence.Builder()

                // Set the request ID of the geofence - use location name as ID (string)
                .setRequestId(daReminder.id)

                // Set the circular region of this geofence
                // ... safe args (!!) OK, as reminder is pre-initialized in calling function
                .setCircularRegion(
                    daReminder.latitude!!,
                    daReminder.longitude!!,
                    GEOFENCE_RADIUS_IN_METERS
                )

                // Set the expiration duration of the geofence. This geofence gets automatically
                // removed after this period of time
                .setExpirationDuration(NEVER_EXPIRE)

                // Set the transition types of interest. Alerts are only generated for these
                // transition. We track entry and exit transitions in this sample
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER /* or Geofence.GEOFENCE_TRANSITION_EXIT */)

                // Create the geofence
                .build()

            // create geoFencing request for this location and set triggers
            val geoFencingRequest = GeofencingRequest.Builder().apply {
                // trigger the request, if user is inside the perimeter of this location (for some time)
                setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                addGeofences(listOf(geoFenceObj))
            }.build()


            // add geoFence (by registering it with the system via the geofencing client)
            geofencingClient.addGeofences(geoFencingRequest, geofencePendingIntent).run {

                addOnSuccessListener {
                    // Geofences added
                    _viewModel.showToast.value =
                        "GeoFence added for reminder location ${daReminder.location}"

                    // store reminder in DB
                    // ... this also takes the user back to the ReminderListFragment
                    _viewModel.validateAndSaveReminder(daReminder)

                }

                addOnFailureListener {

                    // Failed to add geofences
                    when (it.message) {
                        "1000: " -> {
                            // ... might be thrown on older devices (< Android "Q") when gms
                            //     'Improve Location Accuracy' has been disabled
                            // see: https://stackoverflow.com/questions/53996168/geofence-not-avaible-code-1000-while-trying-to-set-up-geofence/53998150
                            _viewModel.showErrorMessage.value =
                                "Location Reminder needs 'Improve Location Accuracy' enabled. Go to settings 'Security & Location > Location > Mode' to enable this."
                        }
                        else -> {
                            _viewModel.showErrorMessage.value =
                                "Error adding geoFence: ${it.message}"
                        }
                    }  // when

                }  // onFailureListener

            }  // addGeofence (lambda)

        }  // check permissions: granted

    }  // checkPermissionsAndAddGeofencingRequest()


    // define internally used constants (PendingIntent ID)
    companion object {
        internal const val ACTION_GEOFENCE_EVENT =
            "SaveReminderFragment.ACTION_GEOFENCE_EVENT"
        internal const val GEOFENCE_RADIUS_IN_METERS = 100F
    }

}

