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

            val title = _viewModel.reminderTitle.value
            val description = _viewModel.reminderDescription.value
            val location = _viewModel.reminderSelectedLocationStr.value
            val latitude = _viewModel.latitude.value
            val longitude = _viewModel.longitude.value

            // assemble reminder data item - this creates the ID we can use as geoFence ID
            val daReminder = ReminderDataItem(
                title,
                description,
                location,
                latitude,
                longitude
            )

            // ask for permission to access location information when the app is in the background
            // (needed for GeoFencing)
            checkPermissionsAndAddGeofencingRequest(
                daReminder.id,
                location ?: "mistery location",
                latitude!!,     // safe - if location is defined (via map), we have lat and long
                longitude!!     // safe - if location is defined (via map), we have lat and long
            )

            // store reminder in DB
            _viewModel.saveReminder(daReminder)

        }

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

        // as (at least) for access to COARSE location info
        locationPermission = Manifest.permission.ACCESS_COARSE_LOCATION

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
            when {
                isGranted -> {

                    // background access to location granted --> needed for geoFencing
                    Timber.i("Background access to location granted (needed for geoFencing).")

                } else -> {

                    // No background access to location information granted
                    // --> request required permissions again, after explanation
                    Snackbar.make(
                        binding.clSaveReminderFragment,
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.settings) {

                        // ... we really need those permissions... --> insist :)
                        locationPermissionRequest.launch(locationPermission)

                    }.show()

                }  // else: background access to location information not granted

            }  // when (isGranted)

        }  // activityResult (lambda)

    }  // handleBackGroundLocationAccess


    // check permissions which are needed for GeoFencing - if not already given, request permission
    private fun checkPermissionsAndAddGeofencingRequest(
        locId: String,
        locationName: String,
        locLatitude: Double,
        locLongitude: Double,
    ) {

        // check permission status
        when (PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                locationPermission
            ) -> {

                // required permission already granted --> add geoFencing request to location
                Timber.i("Access to BACKGROUND location granted --> add geoFencing request")

                // define geoFence perimeter in geoFencing object
                val geoFenceObj = Geofence.Builder()

                    // Set the request ID of the geofence - use location name as ID (string)
                    .setRequestId(locId)

                    // Set the circular region of this geofence
                    .setCircularRegion(
                        locLatitude,
                        locLongitude,
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
                    setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
                    addGeofences(listOf(geoFenceObj))
                }.build()

                // add geoFence (by registering it with the system via the geofencing client)
                geofencingClient.addGeofences(geoFencingRequest, geofencePendingIntent).run {
                    addOnSuccessListener {
                        // Geofences added
                        _viewModel.showToast.value = "GeoFence added for reminder location $locationName"
                    }
                    addOnFailureListener {
                        // Failed to add geofences
                        _viewModel.showErrorMessage.value =
                            "Error adding geoFence: ${it.message}"
                    }
                }

            }
            else -> {

                // permission not yet granted --> request permission...
                // ... after providing an explanation
                Snackbar.make(
                    binding.clSaveReminderFragment,
                    R.string.permission_denied_explanation,
                    Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.settings) {

                    // ... we really need those permissions... --> insist :)
                    locationPermissionRequest.launch(locationPermission)

                }.show()

            }

        }  // when

    }  // enableGeoFencing()


    // define internally used constants (PendingIntent ID)
    companion object {
        internal const val ACTION_GEOFENCE_EVENT =
            "SaveReminderFragment.ACTION_GEOFENCE_EVENT"
        internal const val GEOFENCE_RADIUS_IN_METERS = 100F
        internal const val GEOFENCE_EXPIRATION_IN_MILLISECONDS = 1000L*60*60*12    // 12 hours
    }
}
