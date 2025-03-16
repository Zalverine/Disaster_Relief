package com.example.myapplication

import android.Manifest
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.database.*
import kotlinx.coroutines.*

import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var gMap: GoogleMap
    private lateinit var searchButton: Button
    private lateinit var database: DatabaseReference
    private var searchMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchButton = findViewById(R.id.search_button)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id._map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        database = FirebaseDatabase.getInstance().getReference("disaster")

        searchButton.setOnClickListener {
            getCurrentLocation()
        }
    }

    private fun fetchFirebaseData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("@@@@", "Firebase Data Updated")

                    searchMarker?.remove()

                    for (x in snapshot.children) {
                        val density = x.child("food").getValue(Double::class.java) ?: 0.0
                        val lat = x.child("Latitude").getValue(Double::class.java) ?: 0.0
                        val lng = x.child("Longitude").getValue(Double::class.java) ?: 0.0
                        val loc = x.child("name").getValue(String::class.java) ?: ""

                        val position = LatLng(lat, lng)
                        val marker = gMap.addMarker(MarkerOptions().position(position).title(loc))
                        marker?.tag = density

                        gMap.addCircle(
                            CircleOptions()
                                .center(position)
                                .radius(300.0)
                                .strokeColor(getDensityColor(density))
                                .strokeWidth(4f)
                                .fillColor(adjustAlpha(getDensityColor(density), 0.3f))
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

        val defaultLocation = LatLng(28.7041, 77.1025)
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))

        fetchFirebaseData()
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            gMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    val locationName = getAddress(it.latitude, it.longitude)

                    searchMarker?.remove()
                    searchMarker = gMap.addMarker(
                        MarkerOptions().position(currentLatLng).title("You are here: $locationName")
                    )

                    gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    startRippleEffect(currentLatLng)

                    Toast.makeText(this, "Current Location: $locationName", Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(this, "Unable to fetch location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
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
            if (!addresses.isNullOrEmpty()) {
                addresses[0].getAddressLine(0)
            } else {
                "Unknown Location"
            }
        } catch (e: IOException) {
            Log.e("MapsActivity", "Geocoder failed", e)
            "Geocoder Error"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            getCurrentLocation()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    inner class CustomInfoWindowAdapter : GoogleMap.InfoWindowAdapter {
        override fun getInfoWindow(marker: Marker): View? = null
        override fun getInfoContents(marker: Marker): View {
            val view = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.custom_info_window, null)
            val ratingTextView = view.findViewById<TextView>(R.id.safety_rating_text)

            val density = marker.tag as? Double ?: 0.0
            val densityStatus = when {
                density < 0.4 -> "Safe"
                density < 0.5 -> "Moderate"
                density < 0.6 -> "Risky"
                density < 1.5 -> "Dangerous"
                else -> "Stampede Likely"
            }

            ratingTextView.text = "Density: ${String.format("%.2f", density)} $densityStatus"
            ratingTextView.setTextColor(getDensityColor(density))

            return view
        }
    }

    private fun getDensityColor(density: Double): Int {
        return when {
            density < 0.4 -> Color.parseColor("#90EE90")
            density < 0.5 -> Color.parseColor("#f5b807")
            density < 0.6 -> Color.parseColor("#FFA500")
            density < 1.5 -> Color.parseColor("#FF0000")
            else -> Color.parseColor("#000000")
        }
    }
}
