package com.example.lifelog

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class AudioRecordingService : LifecycleService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaRecorder: MediaRecorder? = null
    private val recordingIntervalMs = 5 * 60 * 1000L

    private val audioSegmentDao: AudioSegmentDao by lazy { AppDatabase.getDatabase(application).audioSegmentDao() }
    private val settingsManager: SettingsManager by lazy { SettingsManager.getInstance(applicationContext) }
    private val cryptoManager = CryptoManager()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Manteniamo le variabili di stato per il segmento corrente
    private var tempFilePath: String? = null
    private var finalEncryptedFilePath: String? = null
    private var currentTimestamp: Long = 0
    private var currentLocation: Location? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startForeground(NOTIFICATION_ID, createNotification("Avvio del servizio..."))

        // La logica è semplice: chi ci avvia (BootReceiver/MainActivity)
        // si è già assicurato che l'onboarding sia completo.
        serviceScope.launch {
            if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
                Log.d(TAG, "Avvio da boot. Attesa di 15 secondi...")
                delay(15_000L)
            }

            // Un ultimo controllo per sicurezza non fa mai male.
            if (settingsManager.encryptionKey.isEmpty()) {
                Log.e(TAG, "Chiave di criptazione non trovata. Arresto servizio.")
                stopSelf()
                return@launch
            }

            Log.d(TAG, "Avvio ciclo di registrazione.")
            while (isActive) {
                startRecordingSegment()
                delay(recordingIntervalMs)
                stopAndEncryptSegment()
            }
        }
        return START_STICKY
    }

    private suspend fun startRecordingSegment() {
        currentLocation = fetchCurrentLocation()
        currentTimestamp = System.currentTimeMillis()

        try {
            val tempFile = File.createTempFile("rec_temp_", ".m4a", cacheDir)
            tempFilePath = tempFile.absolutePath

            val lat = currentLocation?.latitude?.toString()?.replace(".", "_") ?: "NO_LAT"
            val lon = currentLocation?.longitude?.toString()?.replace(".", "_") ?: "NO_LON"
            val finalFileName = "${currentTimestamp}_${lat}_${lon}.m4a"
            finalEncryptedFilePath = File(getExternalFilesDir(null), finalFileName).absolutePath

            Log.d(TAG, "Avvio registrazione su file temporaneo: $tempFilePath")
            updateNotification("Registrazione in corso: $finalFileName")

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(tempFilePath)
                prepare()
                start()
                Log.d(TAG, "Registrazione su file temporaneo avviata.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la preparazione della registrazione", e)
            cleanUpCurrentSegmentState()
            stopSelf()
        }
    }
    private fun stopAndEncryptSegment() {
        // --- MODIFICA CHIAVE PER RISOLVERE LA RACE CONDITION ---
        // 1. Copiamo i valori delle variabili di stato in costanti locali.
        // Questo "congela" i valori al momento dello stop.
        val tempPath = tempFilePath
        val finalPath = finalEncryptedFilePath
        val timestamp = currentTimestamp
        val location = currentLocation

        mediaRecorder?.let {
            try {
                it.stop()
                it.reset()
                it.release()
                Log.d(TAG, "Registrazione su file temporaneo fermata.")

                // 2. Lanciamo la coroutine di criptazione passando i valori locali.
                // Ora questa coroutine ha la sua copia dei dati e non si preoccupa
                // se le variabili di classe vengono azzerate dal ciclo successivo.
                serviceScope.launch(Dispatchers.IO) {
                    encryptFileAndSaveToDb(tempPath, finalPath, timestamp, location)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Errore durante stop() di MediaRecorder", e)
                tempPath?.let { path -> File(path).delete() }
            }
        }
        // 3. Azzeriamo le variabili di stato, pronti per il prossimo ciclo.
        cleanUpCurrentSegmentState()
    }

    // La funzione ora riceve tutto ciò di cui ha bisogno come parametri.
    private suspend fun encryptFileAndSaveToDb(
        tempPath: String?,
        finalPath: String?,
        timestamp: Long,
        location: Location?
    ) {
        val password = settingsManager.encryptionKey

        if (tempPath == null || finalPath == null || password.isEmpty()) {
            Log.e(TAG, "Impossibile criptare: dati mancanti (path='$tempPath', finalPath='$finalPath', isPasswordEmpty=${password.isEmpty()}).")
            tempPath?.let { File(it).delete() }
            return
        }

        val tempFile = File(tempPath)
        if (!tempFile.exists()) {
            Log.e(TAG, "File temporaneo non trovato, impossibile criptare.")
            return
        }

        val finalFile = File(finalPath)
        try {
            FileInputStream(tempFile).use { inputStream ->
                FileOutputStream(finalFile).use { outputStream ->
                    cryptoManager.encrypt(password, inputStream, outputStream)
                }
            }
            Log.d(TAG, "File criptato con successo in: $finalPath")

            val segment = AudioSegment(
                filePath = finalPath,
                timestamp = timestamp,
                latitude = location?.latitude,
                longitude = location?.longitude
            )
            audioSegmentDao.insert(segment)
            Log.d(TAG, "Segmento salvato nel database.")

        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la criptazione o il salvataggio nel DB", e)
            finalFile.delete()
        } finally {
            tempFile.delete()
            Log.d(TAG, "File temporaneo cancellato: $tempPath")
        }
    }

    private fun cleanUpCurrentSegmentState() {
        mediaRecorder = null
        tempFilePath = null
        finalEncryptedFilePath = null
        currentLocation = null
    }

    private suspend fun fetchCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permesso di localizzazione non concesso.")
            return null
        }
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile ottenere la posizione.", e)
            null
        }
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(contentText: String): android.app.Notification {
        val notificationIntent = Intent(this, DashboardActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("LifeLog Attivo")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        tempFilePath?.let {
            mediaRecorder?.release()
            File(it).delete()
            Log.d(TAG, "Pulizia finale del file temporaneo in onDestroy.")
        }
        Log.d(TAG, "onDestroy: Servizio distrutto.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Canale Registrazione Audio",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val TAG = "AudioRecordingService"
        const val NOTIFICATION_CHANNEL_ID = "AudioRecordingChannel"
        const val NOTIFICATION_ID = 1
    }
}