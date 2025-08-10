// Percorso: app/src/main/java/com/example/lifelog/data/SettingsManager.kt

package com.example.lifelog.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SettingsManager {

    private const val TAG = "SettingsManager"
    private const val PREFERENCES_FILE_NAME = "lifelog_secret_prefs"

    const val KEY_ONBOARDING_COMPLETE = "is_onboarding_complete"
    const val KEY_SERVER_ADDRESS = "server_address"
    const val KEY_ENCRYPTION_PASSWORD = "encryption_password"
    const val KEY_USER_FIRST_NAME = "user_first_name"
    const val KEY_USER_LAST_NAME = "user_last_name"
    const val KEY_USER_ALIAS = "user_alias"

    private var sharedPreferences: SharedPreferences? = null

    fun initialize(context: Context) {
        if (sharedPreferences != null) return
        try {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            sharedPreferences = EncryptedSharedPreferences.create(context, PREFERENCES_FILE_NAME, masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            Log.d(TAG, "EncryptedSharedPreferences inizializzate con successo.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore grave durante l'inizializzazione di EncryptedSharedPreferences.", e)
            sharedPreferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(): SharedPreferences {
        return sharedPreferences ?: throw IllegalStateException("SettingsManager deve essere inizializzato.")
    }

    val serverAddress: String
        get() = getPrefs().getString(KEY_SERVER_ADDRESS, "") ?: ""

    val encryptionPassword: String
        get() = getPrefs().getString(KEY_ENCRYPTION_PASSWORD, "") ?: ""

    var isOnboardingComplete: Boolean
        get() = getPrefs().getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = getPrefs().edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    var userFirstName: String
        get() = getPrefs().getString(KEY_USER_FIRST_NAME, "") ?: ""
        set(value) = getPrefs().edit().putString(KEY_USER_FIRST_NAME, value).apply()

    var userLastName: String
        get() = getPrefs().getString(KEY_USER_LAST_NAME, "") ?: ""
        set(value) = getPrefs().edit().putString(KEY_USER_LAST_NAME, value).apply()

    var userAlias: String
        get() = getPrefs().getString(KEY_USER_ALIAS, "") ?: ""
        set(value) = getPrefs().edit().putString(KEY_USER_ALIAS, value).apply()

    // --- MODIFICA: Aggiunti setter espliciti per aggirare il bug del compilatore ---
    fun setServerAddress(address: String) {
        getPrefs().edit().putString(KEY_SERVER_ADDRESS, address).apply()
    }

    fun setEncryptionPassword(password: String) {
        getPrefs().edit().putString(KEY_ENCRYPTION_PASSWORD, password).apply()
    }
}