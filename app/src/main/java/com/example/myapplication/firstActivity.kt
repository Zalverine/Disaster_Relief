package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class firstActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.loginpage_main)

      val button = findViewById<Button>(R.id.button2)

        // Set click listener
        button.setOnClickListener {
            // Navigate to SecondActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }



    }
}