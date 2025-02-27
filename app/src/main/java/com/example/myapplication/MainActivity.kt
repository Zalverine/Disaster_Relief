package com.example.myapplication

import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.app.Activity

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var gMap: GoogleMap
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        searchInput = findViewById(R.id.search_location)
        searchButton = findViewById(R.id.search_button)

        // Initialize Map
        val mapFragment = supportFragmentManager.findFragmentById(R.id._map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

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

    override fun onMapReady(googleMap: GoogleMap) {
        gMap = googleMap
    }

    private fun searchLocation(locationName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                val addressList = geocoder.getFromLocationName(locationName, 1)
                if (!addressList.isNullOrEmpty()) {
                    val address = addressList[0]
                    val location = LatLng(address.latitude, address.longitude)
                    withContext(Dispatchers.Main) {
                        // Clear previous markers and update the map on the main thread
                        gMap.clear()
                        gMap.addMarker(MarkerOptions().position(location).title(locationName))
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 12f))
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Location not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error getting location", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
