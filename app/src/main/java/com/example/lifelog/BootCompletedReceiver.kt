package com.example.lifelog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
// Importa il NUOVO servizio dal suo package corretto
import com.example.lifelog.service.AudioRecordingService
// Importa ConfigManager se lo stai usando per l'onboarding
// import com.example.lifelog.data.ConfigManager // Se hai già questa classe

class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG = "BootCompletedReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED && intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        // TODO: Integrare la logica di ConfigManager per l'onboarding
        // ConfigManager.initialize(context.applicationContext)
        // if (!ConfigManager.getConfig().isOnboardingComplete) {
        //     Log.d(TAG, "Onboarding non completato. Non avvio il servizio al boot.")
        //     return
        // }
        // Per ora, lo avviamo sempre per testare, poi aggiungeremo il controllo onboarding
        Log.d(TAG, "Boot completato. Tentativo di avvio AudioRecordingService.")

        // Usa il NOME COMPLETO DELLA CLASSE del servizio e le ACTION definite nel companion object
        val serviceIntent = Intent(context, com.example.lifelog.service.AudioRecordingService::class.java).apply {
            // Usa l'ACTION definita nel companion object del tuo servizio
            action = com.example.lifelog.service.AudioRecordingService.ACTION_START_RECORDING
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
