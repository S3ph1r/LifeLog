package com.example.lifelog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel condiviso per il flusso di onboarding.
 * Usa AndroidViewModel per avere accesso al contesto dell'applicazione,
 * necessario per inizializzare i repository.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    // --- 1. INIZIALIZZAZIONE DEI NUOVI GESTORI DI DATI ---
    private val userRepository: UserRepository
    private val appPreferences: AppPreferences

    init {
        // Otteniamo le istanze dei nostri DAO e gestori di preferenze
        val userDao = AppDatabase.getDatabase(application).userDao()
        userRepository = UserRepository(userDao)
        appPreferences = AppPreferences.getInstance(application)
    }


    // --- 2. MEMORIA TEMPORANEA PER I DATI (invariata) ---
    var firstName: String = ""
    var lastName: String = ""
    var alias: String = ""
    var serverUrl: String = ""
    var password: String = ""


    // --- 3. LOGICA DI VALIDAZIONE (invariata) ---
    fun areInputsValid(): Boolean {
        return alias.trim().isNotBlank() &&
                serverUrl.trim().isNotBlank() &&
                password.trim().isNotBlank()
    }


    // --- 4. NUOVA LOGICA DI SALVATAGGIO ---
    /**
     * Esegue tutte le operazioni di salvataggio necessarie alla fine dell'onboarding.
     * Questa funzione NON è più bloccante, ma orchestra i salvataggi.
     */
    fun finalizeOnboarding() {
        // A) Salva i dati utente nel database Room.
        //    Questa è un'operazione asincrona, la lanciamo in background.
        viewModelScope.launch {
            val user = User(
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                alias = alias.trim(),
                serverUrl = serverUrl.trim()
            )
            userRepository.saveUser(user)
        }

        // B) Salva la password nelle EncryptedSharedPreferences.
        //    Questa è un'operazione veloce, .apply() la esegue in background.
        appPreferences.password = password.trim()
    }
}