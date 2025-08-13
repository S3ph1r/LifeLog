package com.example.lifelog

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsManager private constructor(context: Context) {

    private val keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC
    private val mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        PREFS_FILENAME,
        mainKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var userAlias: String
        get() = prefs.getString(KEY_USER_ALIAS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_ALIAS, value).apply()

    var userFirstName: String
        get() = prefs.getString(KEY_USER_FIRST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_FIRST_NAME, value).apply()

    var userLastName: String
        get() = prefs.getString(KEY_USER_LAST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_LAST_NAME, value).apply()

    // --- NUOVA PROPRIETÀ ---
    var encryptionKey: String
        get() = prefs.getString(KEY_ENCRYPTION_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ENCRYPTION_KEY, value).apply()


    companion object {
        private const val PREFS_FILENAME = "lifelog_secure_settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_ALIAS = "user_alias"
        private const val KEY_USER_FIRST_NAME = "user_first_name"
        private const val KEY_USER_LAST_NAME = "user_last_name"
        // --- NUOVA CHIAVE ---
        private const val KEY_ENCRYPTION_KEY = "encryption_key"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}