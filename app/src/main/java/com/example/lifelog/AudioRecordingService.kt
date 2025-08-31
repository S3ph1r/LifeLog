package com.example.lifelog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Servizio in foreground con una singola responsabilità: registrare segmenti audio.
 * Una volta che un segmento è salvato come file .m4a, delega tutto il lavoro
 * di processamento (GPS, crittografia) e upload a una catena di WorkManager workers.
 */
class AudioRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    @Volatile
    private var isServiceActive = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // Riferimenti a DAO, Preferences e CryptoManager sono stati rimossi.
    // Il servizio non ne ha più bisogno.

    companion object {
        const val ACTION_START = "com.example.lifelog.ACTION_START"
        const val ACTION_STOP = "com.example.lifelog.ACTION_STOP"
        private const val TAG = "AudioRecService"
        private const val CHANNEL_ID = "AudioRecordingChannel"
        private const val NOTIFICATION_ID = 1
        private const val SEGMENT_DURATION = 300_000L // 5 minuti
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Servizio creato.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand ricevuto con azione: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> startRecordingLoop()
            ACTION_STOP -> stopServiceAndRecording()
            else -> startRecordingLoop() // Comportamento di default
        }
        return START_STICKY
    }

    private fun startRecordingLoop() {
        if (isServiceActive) {
            Log.w(TAG, "startRecordingLoop chiamato ma il servizio è già attivo.")
            return
        }
        Log.d(TAG, "Avvio del ciclo di registrazione.")
        isServiceActive = true

        startForeground(NOTIFICATION_ID, createNotification("Registrazione in corso..."))

        recordingJob = scope.launch {
            while (isActive) {
                // Ora creiamo un file .m4a direttamente nella directory interna
                val m4aFile = createM4aFile()
                if (m4aFile == null) {
                    Log.e(TAG, "Impossibile creare il file .m4a. Interrompo il ciclo.")
                    break // Esce dal ciclo while
                }

                Log.d(TAG, "Inizio nuovo segmento: ${m4aFile.name}")
                val success = recordSingleSegment(m4aFile)

                if (success) {
                    // La funzione di processamento ora avvia la catena di worker
                    processSegment(m4aFile)
                } else {
                    Log.w(TAG, "Registrazione del segmento fallita o interrotta.")
                    m4aFile.delete() // Pulisce il file .m4a se la registrazione fallisce
                }
            }
            Log.d(TAG, "Il ciclo di registrazione è terminato.")
        }
    }

    private fun stopServiceAndRecording() {
        Log.d(TAG, "Comando di stop ricevuto. Interrompo il servizio.")
        isServiceActive = false
        recordingJob?.cancel()
        recordingJob = null
        stopMediaRecorder()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun recordSingleSegment(outputFile: File): Boolean {
        return try {
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            Log.d(TAG, "MediaRecorder avviato.")
            delay(SEGMENT_DURATION)
            Log.d(TAG, "Durata del segmento raggiunta.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Registrazione interrotta (potrebbe essere intenzionale).", e)
            false
        } finally {
            stopMediaRecorder()
        }
    }

    private fun stopMediaRecorder() {
        mediaRecorder?.runCatching {
            stop()
            release()
            Log.d(TAG, "MediaRecorder fermato e rilasciato.")
        }?.onFailure { e ->
            Log.w(TAG, "Eccezione durante lo stop di MediaRecorder: ${e.message}")
        }
        mediaRecorder = null
    }

    /**
     * NUOVA LOGICA: Questa funzione non fa più lavoro pesante.
     * Il suo unico scopo è avviare la catena di worker per processare il file registrato.
     */
    private fun processSegment(m4aFile: File) {
        if (!m4aFile.exists() || m4aFile.length() == 0L) {
            Log.w(TAG, "File .m4a vuoto o non esistente. Annullamento del processamento.")
            return
        }

        Log.i(TAG, "Segmento ${m4aFile.name} registrato. Avvio la catena di worker (Processing -> Upload).")

        // 1. Prepara i dati di input per il primo worker della catena (ProcessingWorker)
        val processingInputData = workDataOf(ProcessingWorker.KEY_RAW_AUDIO_PATH to m4aFile.absolutePath)

        // 2. Costruisci la richiesta per il ProcessingWorker
        val processingWorkRequest = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(processingInputData)
            .addTag("PROCESSING_TAG") // Aggiungiamo un tag per il debug
            .build()

        // 3. Costruisci la richiesta per l'UploadWorker (non ha input diretti, li prenderà dal precedente)
        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .addTag("UPLOAD_TAG") // Aggiungiamo un tag per il debug
            .build()

        // 4. Concatena e metti in coda le richieste a WorkManager
        WorkManager.getInstance(applicationContext)
            .beginWith(processingWorkRequest) // Inizia con il ProcessingWorker
            .then(uploadWorkRequest)           // E POI, se il primo ha successo, esegui l'UploadWorker
            .enqueue()
    }

    /**
     * Crea un file .m4a vuoto nella directory interna dell'app (filesDir).
     * Questa directory è persistente e sicura.
     */
    private fun createM4aFile(): File? {
        return try {
            val timeStamp: String = dateFormat.format(Date())
            val fileName = "segment_$timeStamp.m4a" // Nome del file non criptato
            val storageDir = filesDir // Usiamo la directory interna sicura
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }
            File(storageDir, fileName)
        } catch (e: IOException) {
            Log.e(TAG, "Errore nella creazione del file .m4a", e)
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Registrazione Audio in Background",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeLog in esecuzione")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (recordingJob?.isActive == true) {
            recordingJob?.cancel()
        }
        Log.d(TAG, "Servizio distrutto.")
    }
}