package com.example.lifelog.notification // Assicurati che il package sia corretto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.lifelog.MainActivity // Assumendo che esista
import com.example.lifelog.R
// Importa il NUOVO servizio dal suo package corretto
import com.example.lifelog.service.AudioRecordingService

object AppNotificationManager { // Se è un object (singleton)

    private const val RECORDING_NOTIFICATION_CHANNEL_ID = "RecordingServiceChannel"
    const val RECORDING_NOTIFICATION_ID = 101 // Usa un ID diverso da quello del servizio se necessario

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Registrazione Audio Background"
            val descriptionText = "Notifiche per il servizio di registrazione audio"
            val importance = NotificationManager.IMPORTANCE_LOW // Low per non fare suono/vibrazione
            val channel = NotificationChannel(RECORDING_NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildRecordingNotification(context: Context, contentText: String): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            pendingIntentFlags
        )

        // Esempio di action per fermare la registrazione dalla notifica
        val stopServiceIntent = Intent(context, com.example.lifelog.service.AudioRecordingService::class.java).apply {
            // Usa l'ACTION definita nel companion object del tuo servizio
            action = com.example.lifelog.service.AudioRecordingService.ACTION_STOP_RECORDING
        }
        val stopServicePendingIntent = PendingIntent.getService(
            context,
            0, // Potrebbe essere necessario un requestCode diverso se hai più pending intent per lo stesso servizio
            stopServiceIntent,
            pendingIntentFlags
        )

        return NotificationCompat.Builder(context, RECORDING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Registrazione Audio Attiva")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mic_active) // Assicurati di avere questa icona
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop_placeholder, "Ferma", stopServicePendingIntent) // Aggiungi icona per ic_stop_placeholder
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // Aggiungi altre funzioni di notifica se necessario
}
