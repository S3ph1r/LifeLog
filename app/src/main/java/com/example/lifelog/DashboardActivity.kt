package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FIRST_RUN = "EXTRA_FIRST_RUN"
    }

    private lateinit var recyclerViewSegments: RecyclerView
    private lateinit var buttonToggleService: Button
    private lateinit var buttonSettings: ImageView
    private lateinit var serviceStatusText: TextView
    private lateinit var footerText: TextView

    private lateinit var audioSegmentAdapter: AudioSegmentAdapter
    private val audioSegmentDao by lazy { AppDatabase.getDatabase(this).audioSegmentDao() }
    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        appPreferences = AppPreferences.getInstance(this)

        recyclerViewSegments = findViewById(R.id.recycler_view_segments)
        buttonToggleService = findViewById(R.id.button_toggle_service)
        buttonSettings = findViewById(R.id.button_settings)
        serviceStatusText = findViewById(R.id.text_view_service_status)
        footerText = findViewById(R.id.footer_text)

        setupRecyclerView()
        setupClickListeners()
        observeUnuploadedSegments()
        updateUi() // Chiamata iniziale per impostare la UI

        handleFirstRun()
    }

    override fun onResume() {
        super.onResume()
        // Aggiorna la UI ogni volta che l'activity torna in primo piano,
        // per essere sicuri che mostri sempre lo stato corretto.
        updateUi()
    }

    private fun handleFirstRun() {
        if (intent.getBooleanExtra(EXTRA_FIRST_RUN, false)) {
            Log.d("DashboardActivity", "Primo avvio: avvio automatico del servizio.")
            startRecordingService() // <-- CORREZIONE APPLICATA QUI
            appPreferences.isServiceActive = true
            updateUi()
        }
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
            // Salviamo il nuovo stato e aggiorniamo la UI
            appPreferences.isServiceActive = !isCurrentlyActive
            updateUi()
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
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
                Log.d("DashboardActivity", "Ricevuti ${segments.size} segmenti non caricati.")
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