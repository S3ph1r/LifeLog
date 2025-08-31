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

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIRST_RUN = "EXTRA_FIRST_RUN"
        private const val TAG = "DashboardActivity" // Aggiunto TAG per i log
    }

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

        // L'inizializzazione delle view rimane invariata
        recyclerViewSegments = findViewById(R.id.recycler_view_segments)
        buttonToggleService = findViewById(R.id.button_toggle_service)
        buttonSettings = findViewById(R.id.button_settings)
        buttonSync = findViewById(R.id.button_sync)
        serviceStatusText = findViewById(R.id.text_view_service_status)
        footerText = findViewById(R.id.footer_text)

        setupRecyclerView()
        setupClickListeners()
        observeUnuploadedSegments()

        // La logica di primo avvio ora è gestita in onResume, quindi possiamo rimuoverla da qui
        // handleFirstRun()
    }

    override fun onResume() {
        super.onResume()
        // --- NUOVA LOGICA DI CONTROLLO ROBUSTA ---
        // Ogni volta che l'activity diventa visibile, controlliamo lo stato.
        checkServiceStatusAndStartIfNeeded()
        updateUi()
    }

    /**
     * NUOVA FUNZIONE: Il cuore della nostra logica di avvio affidabile.
     */
    private fun checkServiceStatusAndStartIfNeeded() {
        val onboardingCompleted = appPreferences.isOnboardingCompleted
        val serviceShouldBeActive = appPreferences.isServiceActive

        Log.d(TAG, "Controllo stato servizio: Onboarding completato? $onboardingCompleted, Il servizio dovrebbe essere attivo? $serviceShouldBeActive")

        if (onboardingCompleted && serviceShouldBeActive) {
            // Se l'utente ha completato la configurazione e desidera che il servizio sia attivo,
            // controlliamo se è GIA' in esecuzione per non avviarne duplicati.
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

    /**
     * Funzione di utilità per verificare se un servizio è attualmente in esecuzione.
     */
    @Suppress("DEPRECATION") // Necessario per le vecchie versioni di Android
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }

    // La funzione handleFirstRun non è più necessaria, la sua logica è ora in checkServiceStatusAndStartIfNeeded
    /*
    private fun handleFirstRun() {
        if (intent.getBooleanExtra(EXTRA_FIRST_RUN, false)) {
            Log.d("DashboardActivity", "Primo avvio: avvio automatico del servizio.")
            startRecordingService()
            appPreferences.isServiceActive = true
            updateUi()
        }
    }
    */

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
            // Salviamo il nuovo stato desiderato e aggiorniamo la UI
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