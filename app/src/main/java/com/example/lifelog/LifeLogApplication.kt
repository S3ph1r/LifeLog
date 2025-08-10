// Percorso: app/src/main/java/com/example/lifelog/LifeLogApplication.kt

package com.example.lifelog

import android.app.Application
import com.example.lifelog.data.SettingsManager

/**
 * Classe Application personalizzata per LifeLog.
 *
 * Il suo metodo onCreate() viene eseguito una sola volta quando il processo
 * dell'applicazione viene creato. È il posto ideale per inizializzare
 * componenti globali e singleton che devono essere disponibili per tutta
 * la durata dell'app.
 */
class LifeLogApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inizializziamo il nostro SettingsManager.
        // Da questo momento in poi, sarà possibile accedere alle preferenze
        // criptate da qualsiasi punto dell'applicazione.
        SettingsManager.initialize(this)
    }
}