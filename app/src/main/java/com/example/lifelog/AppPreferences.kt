package com.example.lifelog

import android.content.Context
import android.content.SharedPreferences

class AppPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    // Flag per sapere se l'utente ha completato la configurazione iniziale.
    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    // --- NUOVA VARIABILE ---
    // Flag per sapere se il servizio deve essere attivo.
    // Di default, dopo l'onboarding, è attivo.
    var isServiceActive: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ACTIVE, false) // Default a false
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, value).apply()


    companion object {
        private const val PREFS_FILENAME = "lifelog_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        // --- NUOVA CHIAVE ---
        private const val KEY_SERVICE_ACTIVE = "service_active"

        @Volatile
        private var instance: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}