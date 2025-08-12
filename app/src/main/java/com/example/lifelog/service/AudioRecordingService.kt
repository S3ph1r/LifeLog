package com.example.lifelog.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.compose.foundation.text2.input.insert
import com.example.lifelog.R // Per le risorse come icone e stringhe
import com.example.lifelog.audio.AudioRecorderManager
import com.example.lifelog.audio.RecordingState
import com.example.lifelog.data.AppDatabase
import com.example.lifelog.data.AudioSegment
import com.example.lifelog.notification.AppNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class AudioRecordingService : Service() {

    private val TAG = "AudioRecordingService"

    // Scope per le coroutine del servizio. Viene cancellato in onDestroy.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var audioRecorderManager: AudioRecorderManager
    private lateinit var appNotificationManager: AppNotificationManager // Istanza del gestore notifiche
    private var wakeLock: PowerManager.WakeLock? = null

    // LiveData o StateFlow per osservare lo stato dall'esterno (es. MainActivity)
    companion object {
        private val _serviceIsRunning = MutableStateFlow(false)
        val serviceIsRunning: StateFlow<Boolean> = _serviceIsRunning.asStateFlow()

        private val _currentRecordingState = MutableStateFlow(RecordingState.IDLE)
        val currentRecordingState: StateFlow<RecordingState> = _currentRecordingState.asStateFlow()

        const val ACTION_START_RECORDING = "com.example.lifelog.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.lifelog.ACTION_STOP_RECORDING"
        // const val ACTION_PAUSE_RECORDING = "com.example.lifelog.ACTION_PAUSE_RECORDING" // Se vuoi implementare la pausa
        const val RECORDING_NOTIFICATION_ID = 1337 // ID univoco per la notifica del foreground service
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        _serviceIsRunning.value = true

        appNotificationManager = AppNotificationManager // Inizializza il gestore
        appNotificationManager.createNotificationChannel(this) // Crea il canale (safe da chiamare più volte)

        audioRecorderManager = AudioRecorderManager(
            applicationContext = applicationContext,
            onSegmentReady = { segmentFile ->
                handleSegmentReady(segmentFile)
            },
            onRecordingStateChange = { newState ->
                Log.d(TAG, "Service observed recording state change: $newState")
                _currentRecordingState.value = newState
                updateNotification(newState) // Aggiorna la notifica in base allo stato
                if (newState == RecordingState.IDLE || newState == RecordingState.ERROR) {
                    // Se la registrazione si ferma (IDLE o ERROR), considera di rilasciare il wakelock
                    // e fermare il servizio se non ci sono altri task.
                    // Per ora, lo gestiamo esplicitamente con ACTION_STOP_RECORDING
                }
            },
            externalScope = serviceScope // Passa lo scope del servizio
        )

        // Acquisisci un partial WakeLock per mantenere la CPU attiva durante la registrazione
        // Questo è importante per operazioni in background di lunga durata.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LifeLog::AudioRecordingWakeLock").apply {
            // setReferenceCounted(false) // se vuoi gestire manualmente il conteggio
        }
    }

    private fun handleSegmentReady(segmentFile: File) {
        Log.i(TAG, "Service received segment: ${segmentFile.name} - Size: ${segmentFile.length()}")
        // Qui puoi elaborare ulteriormente il segmento, ad esempio:
        // 1. Spostarlo in una directory permanente se RAW_AUDIO_SUBDIR è temporanea
        // 2. Comprimerlo
        // 3. Caricarlo su un server
        // 4. Salvare i metadati nel database

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val audioSegment = AudioSegment(
                    filePath = segmentFile.absolutePath,
                    timestamp = Date().time, // O un timestamp più preciso dall'inizio del segmento
                    durationMs = AudioRecorderManager.SEGMENT_DURATION_MS, // Durata prevista
                    latitude = null, // TODO: Aggiungere se disponibile
                    longitude = null, // TODO: Aggiungere se disponibile
                    isProcessed = false, // Imposta a true se/quando viene elaborato
                    sizeBytes = segmentFile.length()
                )
                db.audioSegmentDao().insert(audioSegment)
                Log.d(TAG, "Segment metadata saved to DB: ${segmentFile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving segment metadata to DB for ${segmentFile.name}", e)
            }
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "Service onStartCommand, Action: $action")

        when (action) {
            ACTION_START_RECORDING -> {
                if (_currentRecordingState.value != RecordingState.RECORDING) {
                    Log.d(TAG, "Starting recording from service command...")
                    startForegroundServiceWithNotification("Inizializzazione registrazione...")
                    acquireWakeLock()
                    audioRecorderManager.startRecordingCycle()
                } else {
                    Log.d(TAG, "Recording already active. Ignoring START_RECORDING action.")
                }
            }
            ACTION_STOP_RECORDING -> {
                Log.d(TAG, "Stopping recording from service command...")
                audioRecorderManager.stopRecordingCycle() // Questo dovrebbe portare a IDLE
                // Il cambio di stato a IDLE gestirà la notifica e il wakelock
                // e alla fine fermerà il servizio.
                stopForegroundService()
            }
            // case ACTION_PAUSE_RECORDING -> { /* TODO: Logica per mettere in pausa */ }
            else -> {
                Log.w(TAG, "Unknown or null action received: $action")
                // Se il servizio viene riavviato dal sistema dopo essere stato killato,
                // potresti voler riprendere la registrazione se era attiva.
                if (_currentRecordingState.value == RecordingState.RECORDING) {
                    Log.d(TAG, "Service restarted, attempting to resume recording.")
                    startForegroundServiceWithNotification("Ripresa registrazione...")
                    acquireWakeLock()
                    audioRecorderManager.startRecordingCycle() // Tenta di riavviare
                } else {
                    // Se non c'è azione e non stavamo registrando, forse fermare il servizio
                    if (action == null && !_serviceIsRunning.value) { // Potrebbe essere un riavvio senza un'azione specifica
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY // Riavvia il servizio se viene killato, riconsegna l'ultimo intent
        // o START_NOT_STICKY se non vuoi che si riavvii automaticamente
    }

    private fun startForegroundServiceWithNotification(contentText: String) {
        val notification = appNotificationManager.buildRecordingNotification(this, contentText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(RECORDING_NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(RECORDING_NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Service started in foreground.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
            // Fallback o gestione dell'errore (es. se manca il permesso FOREGROUND_SERVICE_MICROPHONE su A14+)
        }
    }

    private fun updateNotification(state: RecordingState) {
        val contentText = when (state) {
            RecordingState.INITIALIZING -> "Inizializzazione..."
            RecordingState.RECORDING -> "Registrazione audio attiva..."
            RecordingState.IDLE -> "Registrazione inattiva."
            RecordingState.ERROR -> "Errore di registrazione."
            else -> "Stato sconosciuto." // RecordingState.PAUSED
        }
        // Se lo stato è IDLE o ERROR e il servizio non dovrebbe più essere in foreground
        if (state == RecordingState.IDLE || state == RecordingState.ERROR) {
            if (_serviceIsRunning.value) { // Solo se il servizio è ancora considerato in esecuzione
                // Mantieni la notifica ma rendila non "ongoing" e forse meno prioritaria
                // oppure rimuovila se lo stop è definitivo.
                // Per ora, la lasciamo con il testo aggiornato se il servizio non è stato fermato.
                val notification = appNotificationManager.buildRecordingNotification(this, contentText)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(RECORDING_NOTIFICATION_ID, notification)
            }
        } else if (state == RecordingState.RECORDING || state == RecordingState.INITIALIZING) {
            startForegroundServiceWithNotification(contentText) // Assicura che sia foreground con testo aggiornato
        }
    }

    private fun stopForegroundService() {
        Log.i(TAG, "Stopping foreground service and releasing resources.")
        releaseWakeLock()
        // audioRecorderManager.stopRecordingCycle() // Già chiamato da chi invoca STOP_RECORDING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE) // Rimuove la notifica
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true) // Rimuove la notifica
        }
        stopSelf() // Ferma il servizio
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(AudioRecorderManager.SEGMENT_DURATION_MS + 60000) // Acquisisci per un po' più della durata del segmento
            Log.d(TAG, "WakeLock acquired.")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d(TAG, "WakeLock released.")
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Service onDestroy")
        _serviceIsRunning.value = false
        _currentRecordingState.value = RecordingState.IDLE // Stato finale pulito
        releaseWakeLock() // Assicurati che il wakelock sia rilasciato
        audioRecorderManager.cleanup() // Chiama la cleanup del manager
        serviceScope.cancel("Service is being destroyed") // Cancella lo scope e tutte le coroutine al suo interno
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Non forniamo binding per questo servizio, quindi restituiamo null.
        return null
    }
}
