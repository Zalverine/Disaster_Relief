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

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var gMap: GoogleMap
    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var database: DatabaseReference
    private var searchMarker: Marker? = null
    private var searchCircle: Circle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI Elements
        searchInput = findViewById(R.id.search_location)
        searchButton = findViewById(R.id.search_button)

        // Initialize Map Fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id._map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        // Initialize Firebase Database reference (update the node as needed)
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
                    gMap.clear()  // Clears existing markers and circles

                    // Iterate over each place in the Firebase node
                    for (x in snapshot.children) {
                        // Read data from Firebase
                        val density = x.child("Density").getValue(Double::class.java) ?: 0.0
                        val lat = x.child("Latitude").getValue(Double::class.java) ?: 0.0
                        val lng = x.child("Longitude").getValue(Double::class.java) ?: 0.0
                        val loc = x.child("Location").getValue(String::class.java) ?: ""

                        val position = LatLng(lat, lng)
                        // Add marker with density tag
                        val marker = gMap.addMarker(
                            MarkerOptions().position(position).title(loc)
                        )
                        marker?.tag = density

                        // Add a circle around the marker
                        gMap.addCircle(
                            CircleOptions()
                                .center(position)
                                .radius(300.0)  // Radius in meters; adjust as needed
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

                val random_number = Random.nextDouble(0.0,2.0)
                val format_number = String.format("%.2f",random_number).toDouble()
                if (!addressList.isNullOrEmpty()) {
                    val address = addressList[0]
                    val location = LatLng(address.latitude, address.longitude)


                    withContext(Dispatchers.Main) {
                        Log.d("BeforeMove", "Visited")
                        searchMarker?.remove()
                        searchMarker = gMap.addMarker(MarkerOptions().position(location).title(locationName))
                        searchMarker?.tag = format_number

                        searchCircle?.remove()
                        searchCircle = gMap.addCircle(
                            CircleOptions()
                                .center(location)
                                .radius(300.0)  // Radius in meters; adjust as needed
                                .strokeColor(getDensityColor(format_number))
                                .strokeWidth(4f)
                                .fillColor(adjustAlpha(getDensityColor(format_number), 0.3f)))
                        // Move the camera to the searched location
                        gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 14f))
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

    // Helper function to adjust the alpha (transparency) of a color.
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    // Define the custom info window adapter as an inner class.
    inner class CustomInfoWindowAdapter : GoogleMap.InfoWindowAdapter {
        override fun getInfoWindow(marker: Marker): View? {
            // Use the default info window frame
            return null
        }

        override fun getInfoContents(marker: Marker): View {
            val view = LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.custom_info_window, null)
            val ratingTextView = view.findViewById<TextView>(R.id.safety_rating_text)

            // Retrieve the density attached as a tag (default to 0.0)
            val density = marker.tag as? Double ?: 0.0
            var smth = ""
            if (density < 0.4
                ){
                smth = "Safe"
            }
            else if(density <0.5){
                smth = "Moderate"
            }
            else if(density < 0.6){
                smth = "Risky"
            }
            else if(density < 1.5){
                smth = "Dangerous"
            }
            else{
                smth = "stampede likely"
            }

            ratingTextView.text = "Density: ${String.format("%.2f", density)} "+smth
            ratingTextView.setTextColor(getDensityColor(density))

            return view
        }
    }

    // Returns a color based on the density value.
    private fun getDensityColor(density: Double): Int {
        return when {
            density < 0.4 -> Color.parseColor("#90EE90") // Green (Safe)
            density < 0.5 -> Color.parseColor("#f5b807") // Yellow (Moderate)
            density < 0.6 -> Color.parseColor("#FFA500") // Orange (Risky)
            density < 1.5 -> Color.parseColor("#FF0000") // Red (High Risk)
            else -> Color.parseColor("#000000") // Black (Stampede Likely)
        }
    }
}
