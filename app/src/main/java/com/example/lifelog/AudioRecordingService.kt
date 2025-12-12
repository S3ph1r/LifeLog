package com.example.lifelog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
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

class AudioRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    @Volatile
    private var isServiceActive = false

    private val scope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private lateinit var audioManager: AudioManager

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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
            // Tentativo iniziale di setup
            manageBluetoothConnection()

            while (isActive) {
                val m4aFile = createM4aFile()
                if (m4aFile == null) {
                    Log.e(TAG, "Impossibile creare il file .m4a. Interrompo il ciclo.")
                    break
                }

                Log.d(TAG, "Preparazione nuovo segmento: ${m4aFile.name}")

                // 1. GESTIONE DINAMICA: Controlliamo cosa è connesso ADESSO.
                // Questa funzione ora restituisce TRUE se stiamo usando il Bluetooth, FALSE se usiamo il telefono.
                val isUsingBluetooth = manageBluetoothConnection()

                // 2. Passiamo questa informazione al registratore per scegliere la config giusta
                val success = recordSingleSegment(m4aFile, isUsingBluetooth)

                // NOTA: Non disattiviamo il Bluetooth qui per evitare attacca-stacca continui.
                // Lo gestisce manageBluetoothConnection() al giro successivo se le cuffie spariscono.

                if (success) {
                    processSegment(m4aFile)
                } else {
                    Log.w(TAG, "Registrazione del segmento fallita o interrotta.")
                    m4aFile.delete()
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
        disableBluetoothSco() // Pulizia finale
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Gestisce la connessione Bluetooth e restituisce TRUE se SCO è attivo.
     */
    private suspend fun manageBluetoothConnection(): Boolean {
        return try {
            if (audioManager.isBluetoothScoAvailableOffCall) {
                if (!audioManager.isBluetoothScoOn) {
                    Log.i(TAG, "Cuffie rilevate ma SCO spento. Attivo connessione...")
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    Log.d(TAG, "Attesa tecnica 2s per handshake Bluetooth...")
                    delay(2000)
                    true // Bluetooth ATTIVO
                } else {
                    Log.v(TAG, "Cuffie rilevate e SCO già attivo. Procedo.")
                    true // Bluetooth ATTIVO
                }
            } else {
                // Se le cuffie non ci sono più, ma SCO era rimasto acceso, spegniamolo.
                if (audioManager.isBluetoothScoOn) {
                    Log.i(TAG, "Cuffie disconnesse. Disattivo SCO per usare microfono telefono.")
                    audioManager.isBluetoothScoOn = false
                    audioManager.stopBluetoothSco()
                }
                false // Bluetooth NON ATTIVO (Uso Telefono)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore gestione Bluetooth: ${e.message}")
            false
        }
    }

    private fun disableBluetoothSco() {
        try {
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                Log.d(TAG, "Bluetooth SCO disattivato definitivamente.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore disattivazione Bluetooth SCO: ${e.message}")
        }
    }

    /**
     * Registra un segmento applicando la configurazione ottimale in base alla sorgente.
     */
    private suspend fun recordSingleSegment(outputFile: File, useBluetooth: Boolean): Boolean {
        return try {
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {

                // --- CONFIGURAZIONE DINAMICA ---
                if (useBluetooth) {
                    // CASO A: CUFFIE BLUETOOTH
                    // Usiamo VOICE_RECOGNITION per dire alle cuffie di attivare i loro filtri (ANC/Beamforming)
                    // e mandarci la voce pulita.
                    Log.i(TAG, "Configurazione: BLUETOOTH (Source: VOICE_RECOGNITION)")
                    setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                } else {
                    // CASO B: MICROFONO TELEFONO (TCL)
                    // Usiamo UNPROCESSED per evitare che il secondo microfono del telefono
                    // cancelli la voce per sbaglio (problema "sott'acqua").
                    Log.i(TAG, "Configurazione: TELEFONO (Source: UNPROCESSED)")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                    } else {
                        setAudioSource(MediaRecorder.AudioSource.MIC)
                    }
                }
                // -------------------------------

                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setAudioChannels(1) // Mono è fondamentale per BT SCO, va bene anche per unprocessed

                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            Log.d(TAG, "MediaRecorder avviato.")
            delay(SEGMENT_DURATION)
            Log.d(TAG, "Durata del segmento raggiunta.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Registrazione interrotta (switch sorgente o errore).", e)
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
        }
        mediaRecorder = null
    }

    private fun processSegment(m4aFile: File) {
        if (!m4aFile.exists() || m4aFile.length() == 0L) {
            Log.w(TAG, "File .m4a vuoto o non esistente. Annullamento.")
            return
        }
        Log.i(TAG, "Segmento ${m4aFile.name} registrato. Avvio catena worker.")

        val processingInputData = workDataOf(ProcessingWorker.KEY_RAW_AUDIO_PATH to m4aFile.absolutePath)

        val processingWorkRequest = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(processingInputData)
            .addTag("PROCESSING_TAG")
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .addTag("UPLOAD_TAG")
            .build()

        WorkManager.getInstance(applicationContext)
            .beginWith(processingWorkRequest)
            .then(uploadWorkRequest)
            .enqueue()
    }

    private fun createM4aFile(): File? {
        return try {
            val timeStamp: String = dateFormat.format(Date())
            val fileName = "segment_$timeStamp.m4a"
            val storageDir = filesDir
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
        disableBluetoothSco()
        if (recordingJob?.isActive == true) {
            recordingJob?.cancel()
        }
        Log.d(TAG, "Servizio distrutto.")
    }
}