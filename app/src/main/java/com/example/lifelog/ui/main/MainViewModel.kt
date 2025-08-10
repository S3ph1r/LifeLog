// Percorso: app/src/main/java/com/example/lifelog/ui/main/MainViewModel.kt

package com.example.lifelog.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileDao
import com.example.lifelog.data.db.AudioFileEntity

/**
 * ViewModel per la MainActivity.
 *
 * Estende AndroidViewModel invece di ViewModel perché abbiamo bisogno del contesto
 * dell'applicazione per accedere al database.
 *
 * Responsabilità:
 * - Mantenere lo stato della UI e sopravvivere ai cambi di configurazione.
 * - Fornire alla UI i dati necessari (es. la lista dei file da caricare).
 * - Gestire le interazioni dell'utente e comunicare con i layer di dati (DB, Service).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Riferimento al nostro DAO per interagire con il database.
    private val audioFileDao: AudioFileDao

    /**
     *  --- LiveData Pubblico per la UI ---
     *
     *  Questo LiveData contiene la lista dei file in attesa di upload.
     *  La MainActivity osserverà questo LiveData. Grazie a Room, questo
     *  si aggiornerà automaticamente ogni volta che un file viene aggiunto
     *  o rimosso dallo stato PENDING_UPLOAD nel database.
     *
     *  È di tipo 'LiveData<List<AudioFileEntity>>' e non 'MutableLiveData'
     *  perché il suo valore non viene impostato manualmente da noi, ma proviene
     *  direttamente dal database. Questo lo rende "read-only" per la UI,
     *  una buona pratica architetturale.
     */
    val pendingFiles: LiveData<List<AudioFileEntity>>

    init {
        // Otteniamo l'istanza del nostro DAO usando il Singleton del database.
        // Lo facciamo nel blocco init, così è disponibile per tutto il ciclo di vita del ViewModel.
        audioFileDao = AppDatabase.getInstance(application).audioFileDao()

        // Inizializziamo il nostro LiveData pubblico facendolo puntare
        // al LiveData fornito dal DAO. Da questo momento in poi,
        // la connessione tra la UI e il database è stabilita.
        pendingFiles = audioFileDao.getPendingFilesForUploadLiveData()
    }

    // Aggiungeremo qui altre funzioni e LiveData più avanti...
    // Esempio:
    // val uiState: LiveData<UiState> = ...
    // fun onRecordButtonPressed() { ... }
    // fun onForceUploadClicked() { ... }
}