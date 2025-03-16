package com.example.myapplication

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*
import kotlinx.coroutines.*
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var gMap: GoogleMap
    private lateinit var sosButton: Button
    private lateinit var volunteerButton: Button
    private lateinit var database: DatabaseReference
    private var searchMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isSosPressed = false
    var why: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sosButton = findViewById(R.id.search_button)
        volunteerButton = findViewById(R.id.button3)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id._map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        database = FirebaseDatabase.getInstance().getReference("disaster")

        sosButton.setOnClickListener {
            isSosPressed = true
            why = true
            getCurrentLocation(why)
        }

        volunteerButton.setOnClickListener {
            isSosPressed = false
            why = false
            getCurrentLocation(why)
        }
    }

    private fun fetchFirebaseData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("@@@@", "Firebase Data Updated")
                    // Loop through all shelter markers from firebase
                    for (x in snapshot.children) {
                        val name = x.child("name").getValue(String::class.java) ?: "Unknown Place"
                        val capacity = x.child("capacity").getValue(String::class.java) ?: "N/A"
                        val food = x.child("food").getValue(Double::class.java) ?: 0.0
                        val medicalKits = x.child("medical kits").getValue(Int::class.java) ?: 0
                        val lat = x.child("Latitude").getValue(Double::class.java) ?: 0.0
                        val lng = x.child("Longitude").getValue(Double::class.java) ?: 0.0

                        val position = LatLng(lat, lng)

                        // Add marker for the shelter (non-SOS markers)
                        val markerOptions = MarkerOptions()
                            .position(position)
                            .title(name)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.shelter))

                        val marker = gMap.addMarker(markerOptions)

                        // Store additional info (for custom info window)
                        marker?.tag = mapOf(
                            "name" to name,
                            "capacity" to capacity,
                            "food" to food,
                            "medicalKits" to medicalKits
                        )

                        // Add translucent black circle around the marker
                        gMap.addCircle(
                            CircleOptions()
                                .center(position)
                                .radius(300.0)
                                .strokeColor(Color.BLACK)
                                .strokeWidth(4f)
                                .fillColor(Color.argb(70, 0, 0, 0))
                        )
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error fetching data: ${error.message}")
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        gMap.setInfoWindowAdapter(CustomInfoWindowAdapter())

        // Set an onMarkerClickListener to handle SOS marker clicks
        gMap.setOnMarkerClickListener { marker ->
            if (marker.title?.startsWith("You are here:") == true) {
                // SOS marker detected, show AlertDialog with densityHuman info
                val tagMap = marker.tag as? Map<*, *>
                val densityHuman = tagMap?.get("densityHuman") ?: "N/A"
                AlertDialog.Builder(this)
                    .setTitle("SOS signal")
                    .setMessage("Humans in distress: $densityHuman")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
                true // consume the click
            } else {
                false // let default behavior happen for non-SOS markers
            }
        }

        val defaultLocation = LatLng(28.6096, 77.3303)
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 13f))
        fetchFirebaseData()
    }

    private fun getCurrentLocation(why: Boolean) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            gMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    val locationName = getAddress(it.latitude, it.longitude)
                    val bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.sos)
                    val markerOptions = MarkerOptions().position(currentLatLng)
                        .title("You are here: $locationName")
                        .icon(bitmapDescriptor)

                    if (why) {
                        // Fetch densityHuman from Firebase and add SOS marker with tag
                        getDensityHuman { density ->
                            searchMarker = gMap.addMarker(markerOptions)
                            searchMarker?.tag = mapOf("densityHuman" to density)
                        }
                    }

                    gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    if (isSosPressed) {
                        startRippleEffect(currentLatLng)
                    }
                    Toast.makeText(this, "Current Location: $locationName", Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(this, "Unable to fetch location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun getDensityHuman(callback: (Int) -> Unit) {
        // Retrieve densityHuman value from Firebase node "density/densityHuman"
        FirebaseDatabase.getInstance().getReference("density/densityHuman")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val density = snapshot.getValue(Int::class.java) ?: 0
                    callback(density)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseError", "Error fetching densityHuman: ${error.message}")
                    callback(0)
                }
            })
    }

    private fun startRippleEffect(latLng: LatLng) {
        CoroutineScope(Dispatchers.Main).launch {
            for (i in 1..3) {
                val circle = gMap.addCircle(
                    CircleOptions()
                        .center(latLng)
                        .radius(1.0)
                        .strokeColor(Color.RED)
                        .strokeWidth(5f)
                        .fillColor(Color.argb(100, 255, 144, 30))
                )
                for (r in 1..30) {
                    circle.radius = r * 20.0
                    circle.fillColor = adjustAlpha(Color.RED, (1f - r / 30f))
                    delay(30)
                }
                circle.remove()
            }
        }
    }

    private fun getAddress(latitude: Double, longitude: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Unknown Location"
        } catch (e: IOException) {
            Log.e("MapsActivity", "Geocoder failed", e)
            "Geocoder Error"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation(why)
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    // Custom info window for non-SOS markers (shelters)
    inner class CustomInfoWindowAdapter : GoogleMap.InfoWindowAdapter {
        override fun getInfoWindow(marker: Marker): View? = null
        override fun getInfoContents(marker: Marker): View? {
            // Do not show info window for SOS markers (which have title starting with "You are here:")
            if (marker.title?.startsWith("You are here:") == true) return null

            val view = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.custom_info_window, null)
            val info = marker.tag as? Map<String, Any> ?: return null

            view.findViewById<TextView>(R.id.textView_name).text = info["name"] as String
            view.findViewById<TextView>(R.id.textView_capacity).text = "Capacity: ${info["capacity"]}"
            view.findViewById<TextView>(R.id.textView_food).text = "Food: ${info["food"]}"
            view.findViewById<TextView>(R.id.textView_medical_kits).text = "Medical Kits: ${info["medicalKits"]}"
            return view
        }
    }
}
