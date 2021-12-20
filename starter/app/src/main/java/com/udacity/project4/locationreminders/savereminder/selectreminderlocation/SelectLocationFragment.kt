package com.udacity.project4.locationreminders.savereminder.selectreminderlocation


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.BuildConfig
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.*

// note: all three concrete viewModels (RemindersList, SaveReminders, SelectLocation) inherit from
//       a common "base viewModel" (BaseViewModel)
//       ... which defines the LiveData/Event elements shared by all three (derived) viewModels
class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {

    // use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding

    // permissions (user location, background and foreground)
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>

    // map support
    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // default user location: Café Cortadio, Schwabing, Munich, Germany
    private var userLatitude = 48.18158973840496
    private var userLongitude = 11.581632522306991
    private var zoomLevel = 12f

    // last marker data...
    private var lastMarker: Marker? = null
    private var lastMarkerTitle: String? = _viewModel.reminderTitle.value
        ?: "Exciting..."  // default
    private var lastMarkerDescription: String? = _viewModel.reminderDescription.value
        ?: "Something is happening"  // default
    private var lastMarkerLocation: String? = _viewModel.reminderSelectedLocationStr.value


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        // inflate layout and get binding object
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)

        // associate injected viewModel with layout (data binding)
        binding.viewModel = _viewModel

        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).also {
            it.getMapAsync(this)
        }

        // handling of user location (foreground access - in map)
        registerForegroundLocationAccessPermissionCheck()

        // instantiate last know location client
        // ... see: https://developer.android.com/training/location/retrieve-current
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())


        // install OK button listener
        binding.btnOk.setOnClickListener {

            // location selected... set viewModel variables and navigate back
            onLocationSelected()

        }

        // install Cancel button listener
        binding.btnCancel.setOnClickListener {

            // remove last marker
            lastMarker?.remove()

            // no marker dropped - hide  OK/Cancel buttons & edit text box
            hideUiControls()

        }

        // install Edit text box handler (to close soft keyboard upon enter)
        // ... see: https://android--code.blogspot.com/2020/08/android-kotlin-edittext-hide-keyboard_28.html
        //
        // define listener for keystrokes in the edit text box (trying to catch ENTER / ESC)
        //
        // Note:
        // ESC key appears to not get forwarded on the Android emulator --> only works on
        // real devices, see: answer to https://stackoverflow.com/questions/48202883/recognising-escape-key
        val keyListener = View.OnKeyListener { etView, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_ESCAPE
                    )
                ) {
                // hide virtual keyboard
                closeSoftKeyboard(requireContext(), etView)
                true
            } else {
                false
            }
        }

        // install keyListener
        binding.etLocationName.setOnKeyListener(keyListener)

        // return inflated fragment view object
        return binding.root
    }


    // initialize map
    override fun onMapReady(googleMap: GoogleMap) {

        // fetch map instance
        map = googleMap

        // apply styling
        setMapStyle(map)

        // install long click listener and POI listener
        setMapLongClick(map)
        setPoiClick(map)

        // trigger permission check for (foreground) access to location information
        requestForegroundAccessToLocation()

        // let's fly to a default location
        val youAreHere = LatLng(userLatitude, userLongitude)
        map.addMarker(MarkerOptions().position(youAreHere).title(getString(R.string.anonymous_place)))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(youAreHere, zoomLevel))

        // ask user to click on the map to select a location for the location reminder
        _viewModel.showSnackBar.value = getString(R.string.map_user_prompt)

    }  // onMapReady


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }


    // user confirmation of selected location (on map)
    // --> store lat/long in viewModel (location name/description set via data binding, see layout)
    //     and navigate to previous fragment
    private fun onLocationSelected() {

        // store latitude / longitude in viewModel
        _viewModel.latitude.value = lastMarker?.position?.latitude
        _viewModel.longitude.value = lastMarker?.position?.longitude

        // use the navigationCommand live data to navigate between the fragments
        _viewModel.navigationCommand.value = NavigationCommand.Back
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }


    // handle long clicks (to identify locations for which we wanna define a reminder)
    private fun setMapLongClick(map:GoogleMap) {
        map.setOnMapLongClickListener { latLng ->

            // remove previous marker - there shall be only one
            deleteLastMarker()

            // location defined by lat/long
            lastMarkerLocation = String.format(
                Locale.getDefault(),
                getString(R.string.lat_long_snippet),
                latLng.latitude,
                latLng.longitude
            )

            // now set the marker at the identified location
            lastMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    //.title(getString(R.string.dropped_pin))
                    .title(lastMarkerTitle)
                    .snippet("$lastMarkerDescription (at $lastMarkerLocation)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )

            // show info about selected location
            lastMarker?.showInfoWindow()

            // display OK/Cancel buttons and edit text box (location name)
            activateUiControls()

        }
    }  // setMapLongClick

    // POI listener
    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->

            // remove previous marker - there shall be only one
            deleteLastMarker()

            // set marker at POI
            lastMarker = map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(lastMarkerTitle)
                    .snippet("$lastMarkerDescription (at ${poi.name})")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )

            // show info about selected POI
            lastMarker?.showInfoWindow()

            // display OK/Cancel buttons and edit text box (location name)
            activateUiControls(poi.name)

        }
    }  // setPoiClick

    // display OK/Cancel buttons and edit text box (location name)
    private fun activateUiControls(location: String? = null) {

        // set location string, if provided (POI)
        location?.let { _viewModel.reminderSelectedLocationStr.value = it }

        // marker dropped - reveal OK/Cancel buttons and Name edit textbox
        binding.etLocationName.visibility = EditText.VISIBLE
        binding.btnOk.visibility = TextView.VISIBLE
        binding.btnCancel.visibility = TextView.VISIBLE

        // set focus to edit text box
        openSoftKeyboard(requireContext(), binding.etLocationName)
    }

    // hide OK/Cancel buttons and edit text box (location name)
    private fun hideUiControls() {
        // marker dropped - reveal OK/Cancel buttons and Name edit textbox
        binding.etLocationName.visibility = EditText.GONE
        binding.btnOk.visibility = TextView.GONE
        binding.btnCancel.visibility = TextView.GONE

        // set focus to edit text box
        closeSoftKeyboard(requireContext(), binding.etLocationName)
    }

    // remove latest set marker and clear associated viewModel variables (coords only - keep name)
    private fun deleteLastMarker() {
        lastMarker?.remove()
        _viewModel.latitude.value = null
        _viewModel.longitude.value = null
    }

    // request access to user location and, if granted, fly to current location
    // ... otherwise a default location is used instead
    //
    // This is using Google Play services APIs
    // ... see: https://developer.android.com/training/location/permissions
    // For best practices about requesting permissions, see:
    //     https://developer.android.com/training/permissions/requesting
    private fun registerForegroundLocationAccessPermissionCheck() {

        // register handler (lambda) for permission launcher
        //
        // user location permission checker as recommended for fragments from androidx.fragment
        // (= JetPack libraries), version 1.3.0 on (using 1.3.6). Motivated by...
        // https://developer.android.com/training/permissions/requesting#allow-system-manage-request-code
        locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) &&
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // Foreground and background location access granted
                    // --> allow for location tracking and use of the location for geoFencing
                    enableMyLocation()

                } else -> {
                    // No (or insufficient) location access granted
                    // --> inform user and send them to settings
                    Snackbar.make(
                        binding.clSelectLocationFragment,
                        R.string.fine_location_permission_denied_consequence,
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
                }

            }  // when (permissions)

        }  // activityResult (lambda)

    }  // handleForeGroundLocationAccess


    // apply custom map styling
    private fun setMapStyle(map: GoogleMap) {
        try {
            // Customize the styling of the base map using a JSON object defined
            // in a raw resource file.
            val success = map.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.map_style
                )
            )

            // provided JSON formatted correctly?
            if (!success) {
                // nope
                Timber.i("Style parsing failed.")
            }

        } catch (e: Resources.NotFoundException) {
            Timber.e("Can't find style. Error: ", e)
        }
    }


    // trigger permission check for (foreground) access to location information
    private fun requestForegroundAccessToLocation() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        )
    }


    // check existing permissions and, if granted, enable access to the user's location
    // ... this is slightly elaborate, as we're only calling this, once the permissions have already
    //     been granted by the user --> can do away with else branch (at least)
    private fun enableMyLocation() {

        // permission check for location (fine/coarse)
        // ... this will always return true - just needed, as android forces us to make this check
        //     consequently, no else branch is needed (to request permissions)
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            ||
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // access to the user's location granted (at some level)
            // --> allow the app to use the user's location data
            map.isMyLocationEnabled = true

            // install 'last location' listener to update user position variables
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location : Location? ->

                    // fetch last known location - if any
                    location?.let {

                        // update current user location and zoom in
                        userLatitude = it.latitude
                        userLongitude = it.longitude
                        zoomLevel = 15f

                        // fly away...
                        map.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(userLatitude, userLongitude),
                                zoomLevel,
                            )
                        )

                    }  // fetch user location

                }  // lastlocation listener (lamda)

        }  // permissions checked

    }  // enableMyLocation


    // programmatically open keyboard to allow focus on EditText box to be set automatically
    // ... see: https://stackoverflow.com/questions/50743467/focus-edit-text-programmatically-kotlin
    private fun openSoftKeyboard(context: Context, view: View) {
        view.requestFocus()
        // open the soft keyboard
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    // programmatically close keyboard
    // ... see: https://stackoverflow.com/questions/1109022/how-do-you-close-hide-the-android-soft-keyboard-programmatically
    private fun closeSoftKeyboard(context: Context, view: View) {
        // close the soft keyboard
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

}
