package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
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

@SuppressLint("UnspecifiedImmutableFlag")
class SaveReminderFragment : BaseFragment() {

    // get the view model (from Koin) this time as a singleton to be shared with another fragment
    override val _viewModel: SaveReminderViewModel by inject()

    // data binding of underlying layout
    private lateinit var binding: FragmentSaveReminderBinding

    // permissions (user location, background and foreground)
    private lateinit var locationPermissionRequest: ActivityResultLauncher<String>
    private lateinit var locationPermission: String

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

        // get access to location information when the app is in the background (geoFencing)
        registerBackgroundLocationAccessPermissionCheck()

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
                NavigationCommand.To(SaveReminderFragmentDirections.actionSaveReminderFragmentToSelectLocationFragment())
        }

        // clicking on the 'saveReminder' FAB...
        // ... installs the geofencing request and triggers the saving to DB
        binding.saveReminder.setOnClickListener {

            // initialize data record to be written to DB
            daReminder = ReminderDataItem(
                _viewModel.reminderTitle.value ?: "mystery",
                _viewModel.reminderDescription.value ?: "something happening here",
                _viewModel.reminderSelectedLocationStr.value ?: "mystery location",
                _viewModel.latitude.value ?: -1.0,
                _viewModel.longitude.value ?: -1.0,
            )

            // ask for permission to access location information when the app is in the background
            // ... saving of the reminder in the local DB is only triggered when permissions for
            //     background access to the user's location have been granted
            checkPermissionsAndAddGeofencingRequest(daReminder)

        }  // onClickListener (FAB - save)

    }  // onViewCreated


    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }


    // request access to user location when the app is in the background (geoFencing)
    // ... see:
    //     https://developer.android.com/training/permissions/requesting
    private fun registerBackgroundLocationAccessPermissionCheck() {

        // ask (at least) for access to FINE location info
        locationPermission = Manifest.permission.ACCESS_FINE_LOCATION

        // from Android API "Q" (28) on, we must ask for BACKGROUND access to
        // location tracking (= always, not just "when using the app")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            locationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }

        // use RequestPermission contract to register a handler (lambda) for permission launcher
        // ... here: a single permission is requested (BACKGROUND access to location info)
        //     --> use RequestPermission (instead of RequestMultiplePermissions)
        //
        // user location permission checker as recommended for fragments from androidx.fragment
        // (= JetPack libraries), version 1.3.0 on (using 1.3.6). Motivated by...
        // https://developer.android.com/training/permissions/requesting#allow-system-manage-request-code
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            // inform about the result of (now decided) permission check
            when {
                isGranted -> {

                    // background access to location granted --> needed for geoFencing
                    Timber.i("Background access to location granted (needed for geoFencing).")

                    // set geoFence - reminder data is pre-initialized
                    checkPermissionsAndAddGeofencingRequest(daReminder)

                } else -> {

                    // No background access to location information granted --> explain consequence
                    _viewModel.showToast.value =
                        "Note: geofencing disabled (needs background access to location info)."

                }  // else: background access to location information not granted

            }  // when (isGranted)

        }  // activityResult (lambda)

    }  // handleBackGroundLocationAccess


    // check permissions which are needed for GeoFencing - if not already given, request permission
    //
    // - flag 'askUserIfNeeded' : is used to stop the recursion after having asked the user for
    //                            permission once
    // - ret value ('state')    : true if user has JUST been asked for permissions, false otherwise
    //
    // suspendable, as we want to wait for the user decision on granting permissions or not
    // --> do this off the UI thread
    private fun checkPermissionsAndAddGeofencingRequest(
        reminder: ReminderDataItem
    ) {

        // check permission status
        when (PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                locationPermission
            ) -> {

                // permission has been granted --> add geoFencing request to location
                Timber.i("Access to BACKGROUND location granted --> add geoFencing request")

                // define geoFence perimeter in geoFencing object
                val geoFenceObj = Geofence.Builder()

                    // Set the request ID of the geofence - use location name as ID (string)
                    .setRequestId(reminder.id)

                    // Set the circular region of this geofence
                    // ... safe args (!!) OK, as reminder is pre-initialized in calling function
                    .setCircularRegion(
                        reminder.latitude!!,
                        reminder.longitude!!,
                        GEOFENCE_RADIUS_IN_METERS
                    )

                    // Set the expiration duration of the geofence. This geofence gets automatically
                    // removed after this period of time
                    .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)

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
                            "GeoFence added for reminder location ${reminder.location}"

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

                }  // addGeofences (lambda)

            }
            else -> {

                // ask for permission... after having provided an explanation as to why
                getBackgroundAccessLocationPermission()

            }  // else-branch in when

        }  // when

    }  // checkPermissionsAndAddGeofencingRequest()


    // inform user and trigger background access to location permission request dialog
    private fun getBackgroundAccessLocationPermission() {
        Snackbar.make(
            binding.clSaveReminderFragment,
            R.string.permission_denied_explanation,
            Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.settings) {

            // ask for permission
            locationPermissionRequest.launch(locationPermission)

        }.show()
    }


    // define internally used constants (PendingIntent ID)
    companion object {
        internal const val ACTION_GEOFENCE_EVENT =
            "SaveReminderFragment.ACTION_GEOFENCE_EVENT"
        internal const val GEOFENCE_RADIUS_IN_METERS = 100F
        internal const val GEOFENCE_EXPIRATION_IN_MILLISECONDS = 1000L*60*60*12    // 12 hours
    }
}
