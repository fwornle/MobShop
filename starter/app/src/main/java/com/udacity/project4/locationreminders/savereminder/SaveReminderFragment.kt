package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.udacity.project4.BuildConfig
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
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
    //     --> ask for multiple results (launcher w/h lambda which receives an array of results)
    private lateinit var activityResultLauncherForLocationPermissionCheck: ActivityResultLauncher<Array<String>>
    private lateinit var permissionsToBeChecked: Array<String>

    // register lambda for settings activity
    private var resultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // check settings again, without resolution
            checkDeviceLocationSettingsAndStartGeofence(false)
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

        // install lambda function to be called when we return from the permission settings activity
        registerActivityResultForLocationPermissionSettings()

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

            // check if required permissions have already been granted to this app (required for
            // geoFencing: access to location information, even when the app is in the background)
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


    // registration of lambda function to be called when the permission settings activity returns
    private fun registerActivityResultForLocationPermissionSettings() {

        // assemble array of permissions - always ask for ACCESS_FINE_LOCATION permission
        permissionsToBeChecked = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        // ... from Android API level "Q" on, this needs to be checked in addition to foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToBeChecked = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }

        // use RequestPermission contract to register a handler (lambda) for permission launcher
        // user location permission checker as recommended for fragments from androidx.fragment
        // (= JetPack libraries), version 1.3.0 on (using 1.3.6).
        //
        // Motivated by...
        // https://developer.android.com/training/permissions/requesting#allow-system-manage-request-code
        // also see:
        // https://medium.com/codex/android-runtime-permissions-using-registerforactivityresult-68c4eb3c0b61
        activityResultLauncherForLocationPermissionCheck = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            // lambda, which is called when the permissions setting activity 'returns' a result
            // ... replaces 'onRequestPermissionsResult'
            Timber.d("lambda function which is called when there's an ActivityResult")

            // evaluate the activity result (in this case --> activity: permission check)

            // start with assumption that all required permissions have been granted
            var locationPermissionGranted = true

            // reset locationPermissionGranted, as soon as any of them has not been granted
            permissions.entries.forEach {
                locationPermissionGranted = locationPermissionGranted && it.value
            }

            // continue with flow based on permission check result
            when (locationPermissionGranted) {

                true -> {

                    // permissions are now as needed --> continue with flow:
                    // ... check if access to location is ON
                    checkDeviceLocationSettingsAndStartGeofence(false)

                }

                else -> {

                    // at least one of the required permission is currently denied
                    // ... inform user & request them (again)
                    Snackbar.make(
                        binding.clSaveReminderFragment,
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.settings) {

                            // this doesn't seem to work inside a fragment (activity only)
                            checkDeviceLocationSettingsAndStartGeofence()

//                            // displays app settings screen
//                            startActivity(Intent().apply {
//                            //resultLauncher.launch(Intent().apply {
//                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
//                                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
//                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                            })

                        }.show()

                }  // when->else (permissions NOK)

            }  // when

        }  // activityResult (lambda)

    }


//    /*
//     * In all cases, we need to have the location permission.  On Android 10+ (Q) we need to have
//     * the background permission as well.
//     */
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray
//    ) {
//        Timber.d("onRequestPermissionResult")
//
//        when {
//            grantResults.isEmpty() ||
//            grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED ||
//            requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE &&
//            grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] ==
//            PackageManager.PERMISSION_DENIED -> {
//
//                // at least one of the required permission is currently denied
//                Snackbar.make(
//                    binding.clSaveReminderFragment,
//                    R.string.permission_denied_explanation,
//                    Snackbar.LENGTH_INDEFINITE
//                )
//                    .setAction(R.string.settings) {
//                        // displays app settings screen
//                        startActivity(
//                            Intent().apply {
//                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
//                                data = Uri.fromParts(
//                                    "package",
//                                    BuildConfig.APPLICATION_ID,
//                                    null
//                                )
//                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                            }
//                        )
//                    }.show()
//            }
//
//            else -> {
//
//                // required permissions are granted --> proceed in flow
//                checkDeviceLocationSettingsAndStartGeofence()
//
//            }
//
//        }  // when
//
//    }  // onRequestPermissionsResult



    // ENTRY POINT of the following flow (triggered by click on 'save'):
    //
    // (1) permission checking (foreground & background access to user location)
    //     (1b) permission request
    // (2) check if location is enabled (on the device)
    // (3) set geoFence at reminder location
    // (4) save reminder location in DB
    private fun checkPermissionsAndStartGeofencing() {

        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartGeofence()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }


    /*
     *  Determines whether the app has the appropriate permissions across Android 10+ and all other
     *  Android versions.
     */
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION))
        val backgroundPermissionApproved =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PackageManager.PERMISSION_GRANTED ==
                        ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved
    }


    /*
     *  Requests ACCESS_FINE_LOCATION and (on Android 10+ (Q) ACCESS_BACKGROUND_LOCATION.
     */
    private fun requestForegroundAndBackgroundLocationPermissions() {

        // skip this, if the permissions have already been approved
        if (foregroundAndBackgroundLocationPermissionApproved())
            return

        // request access to user location when the app is in the background (geoFencing)
        // ... see: https://developer.android.com/training/permissions/requesting
        //
        // launch permission check activity
        activityResultLauncherForLocationPermissionCheck.launch(permissionsToBeChecked)

    }


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
                    // show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(requireActivity(),
                        REQUEST_TURN_DEVICE_LOCATION_ON)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Timber.d("Error getting location settings resolution: " + sendEx.message)
                }

            } else {

                // first time in, we issue an explanation as to why location access is needed
                // (this is done by setting 'resolve' to 'false' when calling this method)
                //
                // ... once they have read this, the user needs to click on 'SETTINGS', which takes
                //     them back to this method (= a one-time recursion) to display the system's
                //     settings activity and let the user make a(n informed) choice
                // inform user and take them to settings
                Snackbar.make(
                    binding.clSaveReminderFragment,
                    R.string.location_access_explanation,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.settings) {

                    // need permissions for foreground and background access to location
                    checkDeviceLocationSettingsAndStartGeofence()

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

        // check state of required permissions
        var permissionsGranted: Boolean = ActivityCompat.checkSelfPermission(
            requireContext(),
            permissionsToBeChecked[0]
        ) == PackageManager.PERMISSION_GRANTED

        // also need to check for background access?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // yup
            permissionsGranted = permissionsGranted &&
                    ActivityCompat.checkSelfPermission(
                        requireContext(),
                        permissionsToBeChecked[1]
                    ) == PackageManager.PERMISSION_GRANTED
        }

        // check permission status
        if (!permissionsGranted) {

            // need permissions for foreground and background access to location
            requestForegroundAndBackgroundLocationPermissions()

        } else {

            // required permissions have been granted
            //
            // --> add geoFencing request to location
            Timber.i("Access to BACKGROUND location granted --> add geoFencing request")

            // initialize data record to be written to DB
            daReminder = ReminderDataItem(
                _viewModel.reminderTitle.value ?: "mystery",
                _viewModel.reminderDescription.value ?: "something happening here",
                _viewModel.reminderSelectedLocationStr.value ?: "mystery location",
                _viewModel.latitude.value ?: -1.0,
                _viewModel.longitude.value ?: -1.0,
            )

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
        const val REQUEST_TURN_DEVICE_LOCATION_ON = 29

        // this is called from the activity...
        // ... https://stackoverflow.com/questions/22602988/google-login-not-working-properly-on-android-fragment
        fun myOnActivityResult(requestCode: Int) {
            if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
                // we don't rely on the result code, but just check the location setting again
                //checkDeviceLocationSettingsAndStartGeofence(false)
            }
        }

    }

}

//private const val LOCATION_PERMISSION_INDEX = 0
//private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
//private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1
//private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
