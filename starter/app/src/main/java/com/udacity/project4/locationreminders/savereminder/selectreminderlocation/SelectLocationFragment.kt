package com.udacity.project4.locationreminders.savereminder.selectreminderlocation


import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.*
import android.widget.Toast
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
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject


// note: all three concrete viewModels (RemindersList, SaveReminders, SelectLocation) inherit from
//       a common "base viewModel" (BaseViewModel)
//       ... which defines the LiveData/Event elements shared by all three (derived) viewModels
class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {

    // use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding

    // map support
    private lateinit var map: GoogleMap
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // user location - default is: Café Cortadio, Schwabing, Munich, Germany
    private var userLatitude = 48.18158973840496
    private var userLongitude = 11.581632522306991
    private var zoomLevel = 12f


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

        // handling of user location
        handleUserLocationAccess()

//        TODO: add style to the map
//        TODO: put a marker to location that the user selected


//        TODO: call this function after the user confirms on the selected location
        onLocationSelected()

        return binding.root
    }

    // request access to user location and, if granted, fly to current location
    // ... otherwise a default location is used instead
    private fun handleUserLocationAccess() {

        // instantiate last know location client
        // ... see: https://developer.android.com/training/location/retrieve-current
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // register handler (lambda) for permission launcher
        //
        // user location permission checker as recommended for fragments from androidx.fragment
        // version 1.3.0 on (using 1.3.6). Motivated by...
        // https://developer.android.com/training/permissions/requesting#allow-system-manage-request-code
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // user has just granted the app access to their location (at FINE level)
                // --> allow the app to use the user's location data
                enableMyLocation()
            } else {
                // No permissions granted --> inform da user
                Toast.makeText(
                    context,
                    "Suit yourself - not using your location then!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    // initialize map
    override fun onMapReady(googleMap: GoogleMap) {

        // fetch map instance
        map = googleMap

        // activate permission checker - this
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        // let's fly to a default location
        val youAreHere = LatLng(userLatitude, userLongitude)
        map.addMarker(MarkerOptions().position(youAreHere).title("A nice place"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(youAreHere, zoomLevel))

    }

    private fun onLocationSelected() {
        //        TODO: When the user confirms on the selected location,
        //         send back the selected location details to the view model
        //         and navigate back to the previous fragment to save the reminder and add the geofence
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
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
            map.setMyLocationEnabled(true)

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

}
