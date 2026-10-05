package com.example.expensetracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class UsernameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_username)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val btnGoAhead = findViewById<Button>(R.id.btnGoAhead)

        // Pre-fill if already set
        val existing = AppPreferences.getUsername(this)
        if (existing.isNotEmpty()) etUsername.setText(existing)

        btnGoAhead.setOnClickListener {
            val name = etUsername.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPreferences.setUsername(this, name)
            // Go to country selection
            startActivity(Intent(this, CountryActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }
}
