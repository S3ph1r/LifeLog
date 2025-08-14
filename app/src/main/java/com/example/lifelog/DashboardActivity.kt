package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var recyclerViewSegments: RecyclerView
    private lateinit var textViewNoFiles: TextView
    private lateinit var textViewQueueTitle: TextView
    private lateinit var buttonStartService: Button
    private lateinit var buttonStopService: Button
    private lateinit var buttonSettings: Button

    private lateinit var audioSegmentAdapter: AudioSegmentAdapter
    private val audioSegmentDao by lazy { AppDatabase.getDatabase(this).audioSegmentDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // --- INIZIO CORREZIONE ---
        // Ho corretto la sintassi per usare R.id.nome_id
        recyclerViewSegments = findViewById(R.id.recycler_view_segments)
        textViewNoFiles = findViewById(R.id.text_view_no_files)
        textViewQueueTitle = findViewById(R.id.text_view_queue_title)
        buttonStartService = findViewById(R.id.button_start_service)
        buttonStopService = findViewById(R.id.button_stop_service)
        buttonSettings = findViewById(R.id.button_settings)
        // --- FINE CORREZIONE ---

        setupRecyclerView()
        setupClickListeners()
        observeUnuploadedSegments()
    }

    private fun setupRecyclerView() {
        audioSegmentAdapter = AudioSegmentAdapter()
        recyclerViewSegments.apply {
            adapter = audioSegmentAdapter
            layoutManager = LinearLayoutManager(this@DashboardActivity)
        }
    }

    private fun setupClickListeners() {
        buttonStartService.setOnClickListener {
            startRecordingService()
            Toast.makeText(this, "Servizio di registrazione avviato", Toast.LENGTH_SHORT).show()
        }

        buttonStopService.setOnClickListener {
            stopRecordingService()
            Toast.makeText(this, "Servizio di registrazione fermato", Toast.LENGTH_SHORT).show()
        }

        buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeUnuploadedSegments() {
        lifecycleScope.launch {
            audioSegmentDao.getUnuploadedSegmentsFlow().collectLatest { segments ->
                Log.d("DashboardActivity", "Ricevuti ${segments.size} segmenti non caricati dal DB.")

                if (segments.isEmpty()) {
                    textViewNoFiles.visibility = View.VISIBLE
                    recyclerViewSegments.visibility = View.GONE
                    textViewQueueTitle.text = "Coda di upload (0 file)"
                } else {
                    textViewNoFiles.visibility = View.GONE
                    recyclerViewSegments.visibility = View.VISIBLE
                    textViewQueueTitle.text = "Coda di upload (${segments.size} file)"
                }

                audioSegmentAdapter.submitList(segments)
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