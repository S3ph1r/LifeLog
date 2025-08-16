package com.example.lifelog

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lifelog.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    // --- MODIFICHE CHIAVE ---
    private lateinit var appPreferences: AppPreferences
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- MODIFICHE CHIAVE ---
        appPreferences = AppPreferences.getInstance(this)
        val userDao = AppDatabase.getDatabase(this).userDao()
        userRepository = UserRepository(userDao)

        loadCurrentSettings()

        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            // Carica i dati utente da Room
            val user = userRepository.user.first() // Prende il primo valore disponibile dal Flow
            if (user != null) {
                binding.editTextFirstName.setText(user.firstName)
                binding.editTextLastName.setText(user.lastName)
                binding.editTextAlias.setText(user.alias)
                binding.editTextServerUrl.setText(user.serverUrl)
            }
            // Carica la password da EncryptedSharedPreferences
            binding.editTextPassword.setText(appPreferences.password)
        }
    }

    private fun saveSettings() {
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
            // Salva i dati utente in Room
            val updatedUser = User(firstName = firstName, lastName = lastName, alias = alias, serverUrl = url)
            userRepository.saveUser(updatedUser)

            // Salva la password in EncryptedSharedPreferences
            appPreferences.password = password

            Toast.makeText(this@SettingsActivity, "Impostazioni salvate", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}