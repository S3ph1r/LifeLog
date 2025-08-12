package com.example.lifelog.data

import androidx.preference.PreferenceDataStore

object SettingsDataStore : PreferenceDataStore() {

    override fun putString(key: String?, value: String?) {
        if (key == null) return
        when (key) {
            "server_address" -> ConfigManager.updateServerAddress(value ?: "")
            "encryption_password" -> ConfigManager.updateEncryptionPassword(value ?: "")
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        val config = ConfigManager.getConfig()
        return when (key) {
            "server_address" -> config.serverAddress
            "encryption_password" -> config.encryptionPassword
            else -> defValue
        }
    }
}