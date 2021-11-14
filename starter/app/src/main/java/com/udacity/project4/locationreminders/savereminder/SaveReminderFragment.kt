package com.udacity.project4.locationreminders.savereminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSaveReminderBinding
import com.udacity.project4.locationreminders.reminderslist.ReminderDataItem
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject

class SaveReminderFragment : BaseFragment() {

    // get the view model (from Koin) this time as a singleton to be shared with another fragment
    override val _viewModel: SaveReminderViewModel by inject()

    // data binding of underlying layout
    private lateinit var binding: FragmentSaveReminderBinding

    // permissions (user location, background and foreground)
    private lateinit var locationPermissionRequest: ActivityResultLauncher<String>


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

            // activate permission checker (for all required location tracking permissions)
            // TODO: inform user, then ask for access, only then go on saving the marker in the DB
            requestBackgroundAccessToLocation()

//            TODO: use the user entered reminder details to:
//             1) add a geofencing request
//             2) save the reminder to the local db

            // store reminder in DB
            _viewModel.saveReminder(
                ReminderDataItem(
                    title,
                    description,
                    location,
                    latitude,
                    longitude
                )
            )

        }

    }  // onViewCreated


    override fun onDestroy() {
        super.onDestroy()
        //make sure to clear the view model after destroy, as it's a single view model.
        _viewModel.onClear()
    }


    // with Android API from "Q" (28) on, we SEPARATELY need to ask for BACKGROUND location access
    private fun requestBackgroundAccessToLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                locationPermissionRequest.launch(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            )
        }
    }  // activateBackgroundLocationAccess


    // request access to user location when the app is in the background (geoFencing)
    // ... see:
    //     https://developer.android.com/training/permissions/requesting
    private fun registerBackgroundLocationAccessPermissionCheck() {

        // register handler (lambda) for permission launcher
        //
        // user location permission checker as recommended for fragments from androidx.fragment
        // (= JetPack libraries), version 1.3.0 on (using 1.3.6). Motivated by...
        // https://developer.android.com/training/permissions/requesting#allow-system-manage-request-code
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            when {
                isGranted -> {

                    // background location access granted
                    // --> allow for location tracking and use of the location for geoFencing
                    enableGeoFencing()

                } else -> {

                    // No background access to location information granted
                    // --> inform user and send them to settings
                    Snackbar.make(
                        binding.clSaveReminderFragment,
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(R.string.settings) {
                        startActivity(
                            Intent().apply {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    }.show()

                }  // else: background access to location information not granted

            }  // when (isGranted)

        }  // activityResult (lambda)

    }  // handleBackGroundLocationAccess


    // permission for GeoFencing granted - enable it
    private fun enableGeoFencing() {

        // from Android API "Q" (28) on, we must ask for BACKGROUND access to
        // location tracking (= always, not just "when using the app")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            // set flag in viewModel to indicate if geoFencing is supported (allowed) or not
            _viewModel.appReadyForGeoFencing = ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        } else {

            // on older models, this permission is given as long as at least COARSE location
            // permission has been given
            _viewModel.appReadyForGeoFencing = ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        }

    }  // enableGeoFencing()

}
