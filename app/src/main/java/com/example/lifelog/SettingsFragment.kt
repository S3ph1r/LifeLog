package com.example.lifelog

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Imposta i valori di default la prima volta che l'app viene avviata.
        // Lo facciamo qui perché non possiamo più usare defaultValue nell'XML.
        PreferenceManager.setDefaultValues(requireContext(), R.xml.root_preferences, false)

        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // --- CONFIGURAZIONE DINAMICA ---

        // 1. Configura il campo della password
        val passwordPreference: EditTextPreference? = findPreference("encryption_password")
        passwordPreference?.setOnBindEditTextListener { editText ->
            // Imposta il tipo di input per mascherare il testo (mostra i pallini)
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        // 2. Configura il campo della durata del segmento
        val durationPreference: EditTextPreference? = findPreference("segment_duration")
        durationPreference?.setOnBindEditTextListener { editText ->
            // Imposta il tipo di input per mostrare solo la tastiera numerica
            editText.inputType = InputType.TYPE_CLASS_NUMBER
        }

        // Imposta un valore di default programmaticamente se non è già presente.
        // Questo è più robusto di setDefaultValues.
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (!sharedPreferences.contains("segment_duration")) {
            durationPreference?.text = "15"
        }
    }
}