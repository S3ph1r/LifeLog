// Percorso: app/src/main/java/com/example/lifelog/AudioRecorderService.kt

package com.example.lifelog

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.UserManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lifelog.audio.AudioRecorderManager
import com.example.lifelog.audio.AudioSegmentProcessor
import com.example.lifelog.data.SettingsManager
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileEntity
import com.example.lifelog.data.db.AudioFileStatus
import com.example.lifelog.notification.AppNotificationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AudioRecorderService : Service() {

    private var isUserUnlocked = false

    inner class AudioServiceBinder : Binder() {
        fun getService(): AudioRecorderService = this@AudioRecorderService
    }

    private val binder = AudioServiceBinder()
    private val _recordingState = MutableLiveData(RecordingState.IDLE)
    val recordingState: LiveData<RecordingState> = _recordingState

    // Componenti rifattorizzati
    private lateinit var audioRecorderManager: AudioRecorderManager
    private lateinit var audioSegmentProcessor: AudioSegmentProcessor
    private lateinit var appNotificationManager: AppNotificationManager

    // Componenti di sistema
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastKnownLocation: Location? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var locationUpdateJob: Job? = null

    // Receiver per eventi di sblocco utente
    private val userUnlockReceiver = object : BroadcastReceiver() {
        // CORREZIONE: onOnReceive -> onReceive
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                Log.d(TAG, "Utente ha sbloccato il dispositivo. Aggiorno stato e avvio worker.")
                isUserUnlocked = true
                val audioSegmentProcessorWithUnlockedContext = AudioSegmentProcessor(context, serviceScope)
                audioSegmentProcessor = audioSegmentProcessorWithUnlockedContext
                triggerPendingEncryptionWorker()
            }
        }
    }

    // Costanti del Servizio
    companion object {
        const val ACTION_START_RECORDING = "com.example.lifelog.action.START_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.example.lifelog.action.PAUSE_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.lifelog.action.STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "com.example.lifelog.action.STOP_SERVICE"
        private const val NOTIFICATION_CHANNEL_ID = "AudioRecorderChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "AudioRecorderService"
    }

    // Ciclo di Vita del Servizio
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servizio creato.")

        SettingsManager.initialize(applicationContext)

        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
        isUserUnlocked = userManager.isUserUnlocked
        Log.d(TAG, "Servizio creato. Stato utente sbloccato: $isUserUnlocked")

        val contextForComponents = if (isUserUnlocked) this else createDeviceProtectedStorageContext()

        audioSegmentProcessor = AudioSegmentProcessor(contextForComponents, serviceScope)
        audioRecorderManager = AudioRecorderManager(
            contextForComponents,
            serviceScope,
            onSegmentReady = { rawFile ->
                audioSegmentProcessor.processAudioSegment(rawFile)
            },
            onRecordingStateChange = { newState ->
                _recordingState.postValue(newState)
            }
        )
        appNotificationManager = AppNotificationManager(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationUpdates()

        registerReceiver(userUnlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))

        if (!SettingsManager.isOnboardingComplete) {
            Log.d(TAG, "Onboarding non completato. Non avvio registrazione automatica.")
            _recordingState.postValue(RecordingState.IDLE)
            return
        }

        if (isUserUnlocked) {
            triggerPendingEncryptionWorker()
        }

        audioRecorderManager.startRecordingCycle()
        startForegroundServiceNotification()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                if (!SettingsManager.isOnboardingComplete) {
                    Log.w(TAG, "Tentato avvio registrazione con onboarding incompleto. Ignorato.")
                    return START_STICKY
                }
                if (audioRecorderManager.getRecordingState() == RecordingState.IDLE ||
                    audioRecorderManager.getRecordingState() == RecordingState.PAUSED) {
                    audioRecorderManager.startRecordingCycle()
                    startForegroundServiceNotification()
                }
            }
            ACTION_PAUSE_RECORDING -> {
                audioRecorderManager.stopRecordingCycle(isPaused = true)
                stopForeground(false)
            }
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "Ricevuta azione di STOP_SERVICE. Termino il servizio.")
                audioRecorderManager.stopRecordingCycle(isPaused = false)
                stopForeground(true)
                stopLocationUpdates()
                stopSelf()
            }
            ACTION_STOP_RECORDING -> {
                audioRecorderManager.stopRecordingCycle(isPaused = false)
                stopForeground(true)
                stopLocationUpdates()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(userUnlockReceiver)
        audioRecorderManager.stopRecordingCycle(isPaused = false)
        stopLocationUpdates()
        serviceScope.cancel()
        Log.i(TAG, "onDestroy chiamato. Pulizia finale del servizio.")
    }

    // Funzioni di Sistema e Utilita'
    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                lastKnownLocation = locationResult.lastLocation
                audioRecorderManager.lastKnownLocation = lastKnownLocation
            }
        }
        locationUpdateJob = serviceScope.launch {
            if (ActivityCompat.checkSelfPermission(this@AudioRecorderService, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Permesso di posizione negato, impossibile avviare gli aggiornamenti.")
                return@launch
            }
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, TimeUnit.MINUTES.toMillis(1))
                .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(30))
                .build()
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "Richiesta di aggiornamenti posizione avviata.")
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdateJob?.cancel()
            Log.d(TAG, "Aggiornamenti posizione fermati.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la rimozione degli aggiornamenti di posizione.", e)
        }
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                appNotificationManager.getNotificationId(),
                appNotificationManager.createForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(
                appNotificationManager.getNotificationId(),
                appNotificationManager.createForegroundNotification()
            )
        }
    }

    private fun triggerPendingEncryptionWorker() {
        val workRequest = OneTimeWorkRequestBuilder<PendingEncryptionWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }
}