package com.example.factcheckoverlay

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.factcheckoverlay.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)
        binding.baseUrlInput.setText(preferences.baseUrl)
        binding.bearerTokenInput.setText(preferences.bearerToken)

        binding.saveButton.setOnClickListener {
            preferences.baseUrl = binding.baseUrlInput.text?.toString().orEmpty()
            preferences.bearerToken = binding.bearerTokenInput.text?.toString().orEmpty()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
