// Percorso: app/src/main/java/com/example/lifelog/BootCompletedReceiver.kt
package com.example.lifelog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.lifelog.data.SettingsManager // MODIFICA: Import di SettingsManager

class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED && action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        // MODIFICA CHIAVE: Controlla se l'onboarding è completo
        // Chiamiamo initialize() per essere sicuri che SettingsManager sia pronto
        SettingsManager.initialize(context.applicationContext)
        if (!SettingsManager.isOnboardingComplete) {
            Log.d(TAG, "Onboarding non completato. Non avvio AudioRecorderService al boot.")
            return // Non avviare il servizio
        }

        Log.d(TAG, "Evento di boot ricevuto: $action. Si tenta di avviare AudioRecorderService.")

        val serviceIntent = Intent(context, AudioRecorderService::class.java).apply {
            this.action = AudioRecorderService.ACTION_START_RECORDING
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}