package com.example.lifelog

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Gestisce le preferenze e gli stati dell'applicazione usando SharedPreferences.
 * È un Singleton per garantire una sola istanza in tutta l'app.
 *
 * RESPONSABILITÀ:
 * 1. Gestire la PASSWORD in modo sicuro (con EncryptedSharedPreferences).
 * 2. Gestire gli STATI dell'app (flag booleani) in modo veloce (con SharedPreferences standard).
 */
class AppPreferences private constructor(context: Context) {

    // --- 1. GESTIONE STATI (SharedPreferences standard) ---
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_states", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_SERVICE_ACTIVE = "service_active"
        private const val KEY_ENCRYPTION_PASSWORD = "encryption_password"

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var isServiceActive: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, value).apply()


    // --- 2. GESTIONE PASSWORD (EncryptedSharedPreferences) ---
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "secure_user_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var password: String
        get() = encryptedPrefs.getString(KEY_ENCRYPTION_PASSWORD, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_ENCRYPTION_PASSWORD, value).apply()


    // --- 3. METODO DI SALVATAGGIO BLOCCANTE PER L'ONBOARDING ---
    /**
     * Esegue il salvataggio DEGLI STATI in modo sincrono e bloccante.
     * Perfetto per la transizione critica alla fine dell'onboarding.
     * Restituisce 'true' se il salvataggio ha avuto successo.
     */
    fun saveOnboardingCompletionStateBlocking(): Boolean {
        return prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).commit()
    }
}