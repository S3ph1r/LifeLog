package com.example.lifelog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Il telefono ha completato l'avvio.")

            // Usiamo una coroutine per poter inserire un ritardo
            scope.launch {
                Log.d(TAG, "In attesa di 15 secondi prima di avviare il servizio...")
                delay(15_000L) // 15 secondi di ritardo

                val prefs = AppPreferences.getInstance(context)
                val settings = SettingsManager.getInstance(context)

                // Controlliamo TUTTE le condizioni necessarie
                if (prefs.isOnboardingCompleted && prefs.isServiceActive && settings.encryptionKey.isNotEmpty()) {
                    Log.d(TAG, "Condizioni soddisfatte. Avvio AudioRecordingService.")
                    val serviceIntent = Intent(context, AudioRecordingService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    Log.d(TAG, "Il servizio non verrà avviato (Onboarding: ${prefs.isOnboardingCompleted}, ServiceActive: ${prefs.isServiceActive}, PwdSet: ${settings.encryptionKey.isNotEmpty()})")
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}