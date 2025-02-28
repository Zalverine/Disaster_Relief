package com.example.myapplication

import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


import com.google.firebase.database.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var gMap: GoogleMap
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        searchInput = findViewById(R.id.search_location)
        searchButton = findViewById(R.id.search_button)

        // Initialize Map Fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id._map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        database = FirebaseDatabase.getInstance().getReference("stampede")

        // Set up search button click listener
        searchButton.setOnClickListener {
            val locationName = searchInput.text.toString()
            if (locationName.isNotEmpty()) {
                searchLocation(locationName)
            } else {
                Toast.makeText(this, "Please enter a location", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun fetchFirebaseData() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("@@@@", "Update done")
                    gMap.clear()
                    for (x in snapshot.children){
                        val place = x.key
                        val density = x.child("Density").getValue(Double::class.java)
                        val lat = x.child("Latitude").getValue(Double::class.java) ?: 0.0
                        val lan = x.child("Longitude").getValue(Double::class.java) ?: 0.0
                        val loc = x.child("Location").getValue(String::class.java) ?: ""

                        val marker = gMap.addMarker(MarkerOptions().position(LatLng(lat, lan)).title(loc))
                        marker?.tag = density

                    }
                    //val density = snapshot.child("densityHuman").getValue(Double::class.java) ?: 0.0


                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseError", "Error fetching data: ${error.message}")
            }
        })
    }



    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
        // Set our custom info window adapter
        gMap.setInfoWindowAdapter(CustomInfoWindowAdapter())

        val defaultLocation = LatLng(28.7041, 77.1025)
        val zoomLevel = 10f // Adjust zoom level

        // Move camera to the default location
        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, zoomLevel))

        fetchFirebaseData()
    }

    private fun searchLocation(locationName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                val addressList = geocoder.getFromLocationName(locationName, 1)
                if (!addressList.isNullOrEmpty()) {
                    val address = addressList[0]
                    val location = LatLng(address.latitude, address.longitude)

                    // Generate a random safety rating from 1 to 5
                    val safetyRating = (1..5).random()

                    withContext(Dispatchers.Main) {
                        // Clear previous markers and add a new one
                        //gMap.clear()
//                        val marker = gMap.addMarker(
//                            MarkerOptions().position(location).title(locationName)
//                        )
//                        // Attach the safety rating to the marker as a tag
//                        marker?.tag = safetyRating

                        Log.d("BeforeMove", "Visited")
                        // Move the camera and show the custom info window
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 14f))
                        //marker?.showInfoWindow()
                        Log.d("AfterMove", "Visited")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Location not found",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error getting location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Define the custom info window adapter as an inner class at the class level.
    inner class CustomInfoWindowAdapter : GoogleMap.InfoWindowAdapter {
        override fun getInfoWindow(marker: Marker): View? {
            return null
        }

        override fun getInfoContents(marker: Marker): View {
            val view = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.custom_info_window, null)
            val ratingTextView = view.findViewById<TextView>(R.id.safety_rating_text)

            // Retrieve the density attached as a tag (default to 0.0)
            val density = marker.tag as? Double ?: 0.0

            ratingTextView.text = "Density: ${String.format("%.2f", density)}"
            ratingTextView.setTextColor(getDensityColor(density))

            return view
        }

        private fun getDensityColor(density: Double): Int {
            return when {
                density < 0.3 -> Color.parseColor("#90EE90") // Green (Safe)
                density < 0.6 -> Color.parseColor("#FFFF00") // Yellow (Moderate)
                density < 0.8 -> Color.parseColor("#FFA500") // Orange (Risky)
                density < 1.0 -> Color.parseColor("#FF0000") // Red (High Risk)
                else -> Color.parseColor("#000000") // Black (Stampede Likely)
            }
        }
    }
}
