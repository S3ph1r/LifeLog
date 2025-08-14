package com.example.lifelog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.lifelog.databinding.FragmentConfigurationBinding

class ConfigurationFragment : Fragment() {

    private var _binding: FragmentConfigurationBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    // Usiamo una data class per restituire i dati in modo pulito all'Activity
    data class SettingsData(
        val firstName: String,
        val lastName: String,
        val alias: String,
        val url: String,
        val password: String
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigurationBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Metodo pubblico per verificare se i campi obbligatori sono stati compilati.
     * Chiamato dall'OnboardingActivity prima di cambiare pagina.
     * I nomi dei binding qui sotto devono corrispondere agli ID del tuo file XML.
     */
    fun areInputsValid(): Boolean {
        val alias = binding.editTextAlias.text.toString().trim()
        val url = binding.editTextServerUrl.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()
        return alias.isNotBlank() && url.isNotBlank() && password.isNotBlank()
    }

    /**
     * Metodo pubblico per recuperare tutti i dati inseriti.
     * Chiamato dall'OnboardingActivity al momento del completamento finale.
     * I nomi dei binding qui sotto devono corrispondere agli ID del tuo file XML.
     */
    fun getSettingsData(): SettingsData {
        return SettingsData(
            firstName = binding.editTextFirstName.text.toString().trim(),
            lastName = binding.editTextLastName.text.toString().trim(),
            alias = binding.editTextAlias.text.toString().trim(),
            url = binding.editTextServerUrl.text.toString().trim(),
            password = binding.editTextPassword.text.toString().trim()
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}