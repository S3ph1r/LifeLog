package com.example.lifelog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Controlliamo che l'azione sia effettivamente quella di avvio completato
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Dispositivo avviato.")

            // Controlliamo una preferenza per vedere se l'utente ha completato l'onboarding.
            // Se non l'ha completato, non dobbiamo avviare il servizio.
            val sharedPrefs = context.getSharedPreferences("app_status", Context.MODE_PRIVATE)
            val onboardingCompleted = sharedPrefs.getBoolean("onboarding_completed", false)

            if (onboardingCompleted) {
                Log.d("BootReceiver", "Onboarding completato, avvio il servizio di registrazione.")

                // Creiamo l'intent per avviare il servizio con l'azione START
                val serviceIntent = Intent(context, AudioRecordingService::class.java).apply {
                    action = AudioRecordingService.ACTION_START
                }

                // Avviamo il servizio in foreground
                context.startForegroundService(serviceIntent)
            } else {
                Log.d("BootReceiver", "Onboarding non completato, il servizio non verrà avviato.")
            }
        }
    }
}