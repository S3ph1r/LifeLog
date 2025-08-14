package com.example.lifelog

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lifelog.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager.getInstance(this)

        loadCurrentSettings()

        // --- INIZIO MODIFICA ---
        // Ho cambiato il riferimento per usare l'ID corretto dal tuo XML
        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
        }
        // --- FINE MODIFICA ---
    }

    private fun loadCurrentSettings() {
        // I nomi qui devono corrispondere agli ID del tuo XML
        binding.editTextFirstName.setText(settingsManager.userFirstName.value)
        binding.editTextLastName.setText(settingsManager.userLastName.value)
        binding.editTextAlias.setText(settingsManager.userAlias.value)
        binding.editTextServerUrl.setText(settingsManager.serverUrl.value)
        binding.editTextPassword.setText(settingsManager.getPassword())
    }

    private fun saveSettings() {
        // I nomi qui devono corrispondere agli ID del tuo XML
        val firstName = binding.editTextFirstName.text.toString().trim()
        val lastName = binding.editTextLastName.text.toString().trim()
        val alias = binding.editTextAlias.text.toString().trim()
        val url = binding.editTextServerUrl.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        if (alias.isBlank() || url.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Alias, URL e Password sono obbligatori.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            settingsManager.saveGeneralSettings(firstName, lastName, alias, url)
            settingsManager.savePassword(password)

            Toast.makeText(this@SettingsActivity, "Impostazioni salvate", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}