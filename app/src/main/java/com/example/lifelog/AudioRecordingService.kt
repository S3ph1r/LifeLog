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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay // <-- IMPORT NECESSARIO AGGIUNTO
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private val audioSegmentDao by lazy { AppDatabase.getDatabase(this).audioSegmentDao() }
    private val settingsManager by lazy { SettingsManager.getInstance(this) }
    private val cryptoManager by lazy { CryptoManager() }

    companion object {
        const val ACTION_START = "com.example.lifelog.ACTION_START"
        const val ACTION_STOP = "com.example.lifelog.ACTION_STOP"
        private const val TAG = "AudioRecService"
        private const val CHANNEL_ID = "AudioRecordingChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Servizio creato.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand ricevuto con azione: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                if (!isRecording) {
                    startMainLoop()
                }
            }
            ACTION_STOP -> {
                stopMainLoop()
            }
        }
        return START_STICKY
    }

    private fun startMainLoop() {
        if (isRecording) {
            Log.w(TAG, "startMainLoop chiamato ma la registrazione è già attiva.")
            return
        }
        Log.d(TAG, "Avvio del ciclo di registrazione principale.")
        isRecording = true
        startForeground(NOTIFICATION_ID, createNotification("Servizio di registrazione attivo..."))
        recordingJob = scope.launch {
            while (isActive) {
                val tempFile = createTempFile()
                Log.d(TAG, "Inizio nuovo segmento, file temporaneo: ${tempFile.name}")
                try {
                    recordSegment(tempFile)
                    stopAndProcessSegment(tempFile)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore grave nel ciclo di registrazione, interruzione.", e)
                    stopMainLoop()
                }
            }
            Log.d(TAG, "Ciclo di registrazione terminato.")
        }
    }

    private fun stopMainLoop() {
        Log.d(TAG, "Interruzione del ciclo di registrazione principale.")
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        stopMediaRecorder()
        stopForeground(true)
        stopSelf()
    }

    private suspend fun recordSegment(outputFile: File) {
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(applicationContext)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        Log.d(TAG, "Registrazione avviata...")
        delay(300_000L) // Pausa di 5 minuti
        Log.d(TAG, "5 minuti passati, fermo la registrazione.")
        stopMediaRecorder()
    }

    private fun stopMediaRecorder() {
        mediaRecorder?.runCatching {
            stop()
            release()
        }?.onFailure { e ->
            Log.e(TAG, "Errore durante lo stop del MediaRecorder", e)
        }
        mediaRecorder = null
    }

    private suspend fun stopAndProcessSegment(tempFile: File) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            Log.w(TAG, "File temporaneo vuoto o non esistente. Salto il segmento.")
            return
        }
        val password = settingsManager.getPassword()
        if (password.isBlank()) {
            Log.e(TAG, "Password non trovata! Impossibile criptare il file. Salto il segmento.")
            tempFile.delete()
            return
        }
        val encryptedFile = getEncryptedFilePath()
        try {
            // Apriamo gli stream dai file per passarli al CryptoManager
            val inputStream = tempFile.inputStream()
            val outputStream = encryptedFile.outputStream()

            // Usiamo gli stream per la crittografia, chiudendoli automaticamente dopo l'uso
            inputStream.use { inStream ->
                outputStream.use { outStream ->
                    cryptoManager.encrypt(password, inStream, outStream)
                }
            }

            Log.d(TAG, "File criptato con successo in: ${encryptedFile.name}")
            val segment = AudioSegment(
                filePath = encryptedFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                isUploaded = false,
                latitude = null,
                longitude = null
            )
            audioSegmentDao.insert(segment)
            Log.d(TAG, "Record del segmento salvato nel DB.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la crittografia o il salvataggio nel DB", e)
            encryptedFile.delete()
        } finally {
            tempFile.delete()
        }
    }

    private fun createTempFile(): File {
        return File.createTempFile("segment_", ".tmp", cacheDir)
    }

    private fun getEncryptedFilePath(): File {
        val timeStamp: String = dateFormat.format(Date())
        val fileName = "audio_$timeStamp.m4a.encrypted"
        val storageDir = getExternalFilesDir("segments")
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File(storageDir, fileName)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Registrazione Audio in Background",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica persistente per il servizio di registrazione LifeLog."
            }
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
            .setSmallIcon(R.drawable.ic_mic) // Assicurati che questa icona esista
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        recordingJob?.cancel()
        Log.d(TAG, "Servizio distrutto.")
    }
}