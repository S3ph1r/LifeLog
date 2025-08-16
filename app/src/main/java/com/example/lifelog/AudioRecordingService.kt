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
import kotlinx.coroutines.delay
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
    @Volatile
    private var isServiceActive = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private val audioSegmentDao by lazy { AppDatabase.getDatabase(this).audioSegmentDao() }
    // --- MODIFICA CHIAVE ---
    private val appPreferences by lazy { AppPreferences.getInstance(this) }
    private val cryptoManager by lazy { CryptoManager() }

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
            else -> startRecordingLoop()
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
                val tempFile = createTempFile()
                if (tempFile == null) {
                    Log.e(TAG, "Impossibile creare il file temporaneo. Interrompo il ciclo.")
                    break
                }

                Log.d(TAG, "Inizio nuovo segmento: ${tempFile.name}")
                val success = recordSingleSegment(tempFile)

                if (success) {
                    processSegment(tempFile)
                } else {
                    Log.w(TAG, "Registrazione del segmento fallita o interrotta.")
                    tempFile.delete()
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
        stopForeground(true)
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

    private suspend fun processSegment(tempFile: File) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            Log.w(TAG, "File temporaneo vuoto o non esistente.")
            return
        }

        // --- MODIFICA CHIAVE ---
        val password = appPreferences.password
        if (password.isBlank()) {
            Log.e(TAG, "Password non trovata! Impossibile criptare.")
            tempFile.delete()
            return
        }

        val encryptedFile = getEncryptedFilePath()
        try {
            cryptoManager.encrypt(password, tempFile.inputStream(), encryptedFile.outputStream())
            Log.d(TAG, "File criptato con successo in: ${encryptedFile.name}")

            val segment = AudioSegment(
                filePath = encryptedFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                isUploaded = false,
                isVoiceprint = false
            )
            audioSegmentDao.insert(segment)
            Log.d(TAG, "Nuovo segmento salvato nel DB.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la crittografia o il salvaggio nel DB.", e)
            encryptedFile.delete()
        } finally {
            tempFile.delete()
        }
    }

    private fun createTempFile(): File? {
        return try {
            File.createTempFile("segment_", ".tmp", cacheDir)
        } catch (e: IOException) {
            Log.e(TAG, "Errore nella creazione del file temporaneo", e)
            null
        }
    }

    private fun getEncryptedFilePath(): File {
        val timeStamp: String = dateFormat.format(Date())
        val fileName = "segment_$timeStamp.m4a.enc"
        val storageDir = filesDir
        if (!storageDir.exists()) {
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