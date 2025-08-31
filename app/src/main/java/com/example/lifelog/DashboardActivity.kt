package com.example.lifelog

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIRST_RUN = "EXTRA_FIRST_RUN"
        private const val TAG = "DashboardActivity"
    }

    private lateinit var imageViewBackground: ImageView // <-- NUOVO: Riferimento allo sfondo
    private lateinit var recyclerViewSegments: RecyclerView
    private lateinit var buttonToggleService: Button
    private lateinit var buttonSettings: ImageView
    private lateinit var buttonSync: ImageView
    private lateinit var serviceStatusText: TextView
    private lateinit var footerText: TextView

    private lateinit var audioSegmentAdapter: AudioSegmentAdapter
    private val audioSegmentDao by lazy { AppDatabase.getDatabase(this).audioSegmentDao() }
    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        appPreferences = AppPreferences.getInstance(this)

        // Inizializzazione di tutte le view, inclusa la nuova ImageView
        imageViewBackground = findViewById(R.id.image_view_background)
        recyclerViewSegments = findViewById(R.id.recycler_view_segments)
        buttonToggleService = findViewById(R.id.button_toggle_service)
        buttonSettings = findViewById(R.id.button_settings)
        buttonSync = findViewById(R.id.button_sync)
        serviceStatusText = findViewById(R.id.text_view_service_status)
        footerText = findViewById(R.id.footer_text)

        setupRecyclerView()
        setupClickListeners()
        observeUnuploadedSegments()
    }

    override fun onResume() {
        super.onResume()
        // Ogni volta che l'activity torna visibile:
        // 1. Controlla e avvia il servizio se necessario.
        // 2. Aggiorna lo sfondo dinamico.
        // 3. Aggiorna il testo dei pulsanti e dello stato.
        checkServiceStatusAndStartIfNeeded()
        updateDynamicBackground()
        updateUi()
    }

    /**
     * Imposta l'immagine di sfondo in base all'ora del giorno.
     */
    private fun updateDynamicBackground() {
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        val backgroundResource = when (hourOfDay) {
            in 6..17 -> { // Dalle 6:00 alle 17:59 (giorno)
                Log.d(TAG, "Impostazione sfondo: Giorno")
                R.drawable.background_morning
            }
            else -> { // Dalle 18:00 alle 5:59 (sera/notte)
                Log.d(TAG, "Impostazione sfondo: Sera/Notte")
                R.drawable.background_evening
            }
        }
        imageViewBackground.setImageResource(backgroundResource)
    }

    private fun checkServiceStatusAndStartIfNeeded() {
        val onboardingCompleted = appPreferences.isOnboardingCompleted
        val serviceShouldBeActive = appPreferences.isServiceActive

        Log.d(TAG, "Controllo stato servizio: Onboarding completato? $onboardingCompleted, Il servizio dovrebbe essere attivo? $serviceShouldBeActive")

        if (onboardingCompleted && serviceShouldBeActive) {
            if (!isServiceRunning(AudioRecordingService::class.java)) {
                Log.i(TAG, "Onboarding completato e servizio richiesto, ma non è in esecuzione. Lo avvio ora.")
                startRecordingService()
            } else {
                Log.d(TAG, "Servizio già in esecuzione, tutto corretto.")
            }
        } else {
            Log.d(TAG, "Il servizio non verrà avviato (onboarding non completo o servizio in pausa).")
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }

    private fun setupRecyclerView() {
        audioSegmentAdapter = AudioSegmentAdapter()
        recyclerViewSegments.apply {
            adapter = audioSegmentAdapter
            layoutManager = LinearLayoutManager(this@DashboardActivity)
        }
    }

    private fun setupClickListeners() {
        buttonToggleService.setOnClickListener {
            val isCurrentlyActive = appPreferences.isServiceActive
            if (isCurrentlyActive) {
                stopRecordingService()
            } else {
                startRecordingService()
            }
            appPreferences.isServiceActive = !isCurrentlyActive
            updateUi()
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        buttonSync.setOnClickListener {
            Log.i("DashboardActivity", "Pulsante Sync premuto. Avvio del ManualSyncWorker...")
            val syncWorkRequest = OneTimeWorkRequestBuilder<ManualSyncWorker>()
                .addTag(ManualSyncWorker.TAG)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(syncWorkRequest)
            Toast.makeText(this, "Sincronizzazione manuale avviata...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUi() {
        val isActive = appPreferences.isServiceActive
        if (isActive) {
            buttonToggleService.text = "Pausa"
            serviceStatusText.text = "Servizio Attivo"
        } else {
            buttonToggleService.text = "Avvia"
            serviceStatusText.text = "Servizio in Pausa"
        }
    }

    private fun observeUnuploadedSegments() {
        lifecycleScope.launch {
            audioSegmentDao.getUnuploadedSegmentsFlow().collectLatest { segments ->
                Log.d(TAG, "Ricevuti ${segments.size} segmenti non caricati.")
                audioSegmentAdapter.submitList(segments)
                footerText.text = "File in attesa di upload: ${segments.size}"
            }
        }
    }

    private fun startRecordingService() {
        val serviceIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START
        }
        startService(serviceIntent)
    }

    private fun stopRecordingService() {
        val serviceIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP
        }
        startService(serviceIntent)
    }
}