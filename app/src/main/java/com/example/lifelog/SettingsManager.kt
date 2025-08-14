package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestisce la persistenza di tutte le impostazioni dell'applicazione.
 * - Usa Room per le impostazioni generali (alias, url, etc.).
 * - Usa EncryptedSharedPreferences per la password di crittografia.
 */
class SettingsManager private constructor(context: Context) {

    // --- Sezione Room per le Impostazioni Generali ---
    private val settingsDao = AppDatabase.getDatabase(context).settingsDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _userFirstName = MutableStateFlow("")
    private val _userLastName = MutableStateFlow("")
    private val _userAlias = MutableStateFlow("")
    private val _serverUrl = MutableStateFlow("")

    val userFirstName: StateFlow<String> = _userFirstName.asStateFlow()
    val userLastName: StateFlow<String> = _userLastName.asStateFlow()
    val userAlias: StateFlow<String> = _userAlias.asStateFlow()
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // --- Sezione EncryptedSharedPreferences per la Password ---
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "secure_prefs", // Usiamo un nome di file diverso per sicurezza
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    companion object {
        private const val KEY_ENCRYPTION_PASSWORD = "encryption_password"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
    }


    init {
        // Avvia l'osservazione delle impostazioni da Room
        scope.launch {
            settingsDao.getSettingsFlow().collect { settings ->
                if (settings != null) {
                    _userFirstName.value = settings.userFirstName
                    _userLastName.value = settings.userLastName
                    _userAlias.value = settings.userAlias
                    _serverUrl.value = settings.serverUrl
                } else {
                    _userFirstName.value = ""
                    _userLastName.value = ""
                    _userAlias.value = ""
                    _serverUrl.value = ""
                }
                Log.d("SettingsManager", "Impostazioni (da Room) caricate: Alias=${_userAlias.value}, ServerUrl=${_serverUrl.value}")
            }
        }
    }

    /**
     * Salva le impostazioni generali (non sensibili) nel database Room.
     */
    suspend fun saveGeneralSettings(firstName: String, lastName: String, alias: String, url: String) {
        withContext(Dispatchers.IO) {
            val settings = Settings(
                userFirstName = firstName,
                userLastName = lastName,
                userAlias = alias,
                serverUrl = url
            )
            settingsDao.insertOrUpdate(settings)
            Log.d("SettingsManager", "Impostazioni generali salvate nel DB.")
        }
    }

    /**
     * Salva la password di crittografia nelle EncryptedSharedPreferences.
     */
    fun savePassword(password: String) {
        encryptedPrefs.edit().putString(KEY_ENCRYPTION_PASSWORD, password).apply()
        Log.d("SettingsManager", "Password salvata in modo sicuro.")
    }

    /**
     * Legge la password di crittografia dalle EncryptedSharedPreferences.
     * Questa funzione viene chiamata al momento del bisogno per avere sempre il valore più recente.
     */
    fun getPassword(): String {
        return encryptedPrefs.getString(KEY_ENCRYPTION_PASSWORD, "") ?: ""
    }
}