package com.example.lifelog

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.lifelog.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager.getInstance(this)

        loadSettings()

        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        binding.editTextFirstName.setText(settingsManager.userFirstName)
        binding.editTextLastName.setText(settingsManager.userLastName)
        binding.editTextAlias.setText(settingsManager.userAlias)
        binding.editTextServerUrl.setText(settingsManager.serverUrl)
        // Carica la password
        binding.textInputLayoutPassword.editText?.setText(settingsManager.encryptionKey)
    }

    private fun saveSettings() {
        val url = binding.editTextServerUrl.text.toString().trim()
        val alias = binding.editTextAlias.text.toString().trim()
        val password = binding.textInputLayoutPassword.editText?.text.toString()

        // Resetta errori precedenti
        binding.editTextServerUrl.error = null
        binding.editTextAlias.error = null
        binding.textInputLayoutPassword.error = null

        // Validazione
        var isValid = true
        if (url.isEmpty()) {
            binding.editTextServerUrl.error = "Questo campo è obbligatorio"
            isValid = false
        }
        if (alias.isEmpty()) {
            binding.editTextAlias.error = "Questo campo è obbligatorio"
            isValid = false
        }
        if (password.isEmpty()) {
            binding.textInputLayoutPassword.error = "Questo campo è obbligatorio"
            isValid = false
        }

        if (!isValid) return

        // Salvataggio
        settingsManager.userFirstName = binding.editTextFirstName.text.toString().trim()
        settingsManager.userLastName = binding.editTextLastName.text.toString().trim()
        settingsManager.userAlias = alias
        settingsManager.serverUrl = url
        settingsManager.encryptionKey = password // Salva la password

        Toast.makeText(this, "Impostazioni salvate", Toast.LENGTH_SHORT).show()
        finish()
    }
}