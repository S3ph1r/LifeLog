package com.example.lifelog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lifelog.databinding.FragmentConfigurationBinding

class ConfigurationFragment : Fragment() {

    private var _binding: FragmentConfigurationBinding? = null
    private val binding get() = _binding!!

    private lateinit var settingsManager: SettingsManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        settingsManager = SettingsManager.getInstance(requireContext())
        loadSettings()
        return binding.root
    }

    private fun loadSettings() {
        binding.editTextFirstName.setText(settingsManager.userFirstName)
        binding.editTextLastName.setText(settingsManager.userLastName)
        binding.editTextAlias.setText(settingsManager.userAlias)
        binding.editTextServerUrl.setText(settingsManager.serverUrl)
        // Carica la password/chiave nel campo corretto
        binding.textInputLayoutPassword.editText?.setText(settingsManager.encryptionKey)
    }

    fun saveSettings(): Boolean {
        val url = binding.editTextServerUrl.text.toString().trim()
        val alias = binding.editTextAlias.text.toString().trim()
        // Recuperiamo la password (non va trimmata)
        val password = binding.textInputLayoutPassword.editText?.text.toString()

        // Resettiamo gli errori precedenti
        binding.editTextServerUrl.error = null
        binding.editTextAlias.error = null
        binding.textInputLayoutPassword.error = null

        // Validazione dei campi obbligatori
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
            // L'errore va impostato sul TextInputLayout, non sull'EditText interno
            binding.textInputLayoutPassword.error = "Questo campo è obbligatorio"
            isValid = false
        }

        if (!isValid) return false

        // Se tutti i dati sono validi, salviamo
        settingsManager.userFirstName = binding.editTextFirstName.text.toString().trim()
        settingsManager.userLastName = binding.editTextLastName.text.toString().trim()
        settingsManager.userAlias = alias
        settingsManager.serverUrl = url
        settingsManager.encryptionKey = password // Salviamo la password

        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}