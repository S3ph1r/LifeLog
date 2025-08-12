package com.example.lifelog

import android.os.Bundle
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.example.lifelog.data.SettingsDataStore // <-- IMPORT

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Collega questo fragment al nostro ConfigManager
        preferenceManager.preferenceDataStore = SettingsDataStore

        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val passwordPreference: EditTextPreference? = findPreference("encryption_password")
        passwordPreference?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }
}