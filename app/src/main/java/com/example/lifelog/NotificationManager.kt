// Percorso: app/src/main/java/com/example/lifelog/notification/AppNotificationManager.kt

package com.example.lifelog.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.lifelog.MainActivity
import com.example.lifelog.R
import com.example.lifelog.AudioRecorderService

/**
 * Gestisce la creazione e l'aggiornamento della notifica persistente per AudioRecorderService.
 * Incapsula tutta la logica relativa ai canali di notifica e alle azioni dei pulsanti.
 */
class AppNotificationManager(private val context: Context) {

    private val NOTIFICATION_CHANNEL_ID = "AudioRecorderChannel"
    private val NOTIFICATION_CHANNEL_NAME = "Canale Servizio Registrazione"
    private val NOTIFICATION_ID = 1

    init {
        createNotificationChannel()
    }

    /**
     * Crea e restituisce l'oggetto Notification per il servizio in primo piano.
     * @param isRecording true se il servizio è in registrazione attiva, false altrimenti.
     */
    fun createForegroundNotification(): Notification {
        // Intent per aprire la MainActivity quando l'utente clicca sulla notifica
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent per l'azione di STOP del servizio
        val stopServiceIntent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_STOP_SERVICE
        }
        val stopServicePendingIntent = PendingIntent.getService(
            context, 1, stopServiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // L'icona del microfono
        val icon = android.R.drawable.ic_btn_speak_now // O R.drawable.ic_mic se preferisci

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("LifeLog in esecuzione")
            .setContentText("Registrazione e geolocalizzazione attive...")
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Rende la notifica persistente
            .addAction(0, "Stop", stopServicePendingIntent) // Aggiunge il pulsante Stop
            .build()
    }

    /**
     * Crea il canale di notifica per Android 8.0 (Oreo) e superiori.
     * È necessario per visualizzare le notifiche.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // L'importanza bassa minimizza l'interruzione
            ).apply {
                description = "Notifica persistente per il servizio di registrazione LifeLog"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Restituisce l'ID della notifica.
     */
    fun getNotificationId(): Int = NOTIFICATION_ID
}