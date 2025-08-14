package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    // Dichiarazione delle View con i nomi corretti
    private lateinit var recyclerViewSegments: RecyclerView
    private lateinit var buttonToggleService: Button
    private lateinit var buttonSettings: ImageView // Nel tuo layout è una ImageView
    private lateinit var serviceStatusText: TextView
    private lateinit var footerText: TextView // Useremo questo per il conteggio

    private lateinit var audioSegmentAdapter: AudioSegmentAdapter
    private val audioSegmentDao by lazy { AppDatabase.getDatabase(this).audioSegmentDao() }

    // Variabile per tenere traccia dello stato del servizio (da migliorare in futuro)
    private var isServiceRunning = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Inizializzazione con i tuoi ID corretti
        recyclerViewSegments = findViewById(R.id.recycler_view_segments)
        buttonToggleService = findViewById(R.id.button_toggle_service)
        buttonSettings = findViewById(R.id.button_settings)
        serviceStatusText = findViewById(R.id.text_view_service_status)
        footerText = findViewById(R.id.footer_text)

        setupRecyclerView()
        setupClickListeners()
        observeUnuploadedSegments()
        updateUi() // Chiamata iniziale per impostare la UI
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
            isServiceRunning = !isServiceRunning // Inverti lo stato
            if (isServiceRunning) {
                startRecordingService()
            } else {
                stopRecordingService()
            }
            updateUi() // Aggiorna la UI dopo il click
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateUi() {
        if (isServiceRunning) {
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
                Log.d("DashboardActivity", "Ricevuti ${segments.size} segmenti non caricati dal DB.")

                // Non abbiamo un text_view_no_files, quindi gestiamo solo l'adapter
                audioSegmentAdapter.submitList(segments)

                // Aggiorniamo il footer con il conteggio
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