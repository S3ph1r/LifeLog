// Percorso: app/src/main/java/com/example/lifelog/LifeLogApplication.kt

package com.example.lifelog

import android.app.Application
import com.example.lifelog.data.ConfigManager // <-- NUOVO IMPORT
// import com.example.lifelog.data.SettingsManager // <-- VECCHIO IMPORT, ORA RIMOSSO

/**
 * Classe Application personalizzata per LifeLog.
 *
 * Il suo metodo onCreate() viene eseguito una sola volta quando il processo
 * dell'applicazione viene creato. È il posto ideale per inizializzare
 * componenti globali e singleton come il nostro nuovo ConfigManager.
 */
class LifeLogApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // --- MODIFICA CHIAVE ---
        // Inizializziamo il nostro nuovo ConfigManager basato su file JSON.
        // Da questo momento in poi, sarà possibile accedere alla configurazione
        // da qualsiasi punto dell'applicazione.
        ConfigManager.initialize(this)

        // SettingsManager.initialize(this) // <-- RIMOSSA LA VECCHIA INIZIALIZZAZIONE
    }
}