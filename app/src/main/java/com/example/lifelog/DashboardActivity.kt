package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lifelog.databinding.ActivityDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var prefs: AppPreferences
    private val audioSegmentAdapter = AudioSegmentAdapter()

    private val audioSegmentDao: AudioSegmentDao by lazy {
        AppDatabase.getDatabase(application).audioSegmentDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = AppPreferences.getInstance(this)

        setupUI()
        observeUnsyncedSegments()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusUI()
    }

    private fun setupUI() {
        binding.recyclerViewSegments.adapter = audioSegmentAdapter

        binding.buttonToggleService.setOnClickListener {
            toggleServiceState()
        }

        // --- MODIFICA CHIAVE: Apriamo la SettingsActivity ---
        binding.buttonSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        binding.buttonSync.setOnClickListener {
            scheduleUploads()
        }
    }

    private fun observeUnsyncedSegments() {
        lifecycleScope.launch {
            audioSegmentDao.getUnsyncedSegments().collectLatest { segments ->
                Log.d("DashboardActivity", "UI aggiornata con ${segments.size} segmenti da caricare.")
                withContext(Dispatchers.Main) {
                    audioSegmentAdapter.submitList(segments)
                }
            }
        }
    }

    private fun scheduleUploads() {
        lifecycleScope.launch(Dispatchers.IO) {
            val unsyncedSegments = audioSegmentDao.getUnsyncedSegments().first()
            if (unsyncedSegments.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Nessun file da sincronizzare.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            Log.d("DashboardActivity", "Trovati ${unsyncedSegments.size} file. Avvio schedulazione lavori...")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            for (segment in unsyncedSegments) {
                val inputData = Data.Builder()
                    .putString(UploadWorker.KEY_FILE_PATH, segment.filePath)
                    .putLong(UploadWorker.KEY_SEGMENT_ID, segment.id)
                    .putLong(UploadWorker.KEY_TIMESTAMP, segment.timestamp)
                    .build()

                val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(applicationContext).enqueue(uploadWorkRequest)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "Sincronizzazione avviata per ${unsyncedSegments.size} file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateServiceStatusUI() {
        if (prefs.isServiceActive) {
            binding.buttonToggleService.text = "Pausa"
            binding.textViewServiceStatus.text = "Servizio Attivo"
        } else {
            binding.buttonToggleService.text = "Riprendi"
            binding.textViewServiceStatus.text = "Servizio in Pausa"
        }
        Log.d("DashboardActivity", "UI stato servizio aggiornata: ${if(prefs.isServiceActive) "Attivo" else "In Pausa"}")
    }

    private fun toggleServiceState() {
        val shouldBeActive = !prefs.isServiceActive
        prefs.isServiceActive = shouldBeActive

        if (shouldBeActive) {
            Log.d("DashboardActivity", "Avvio del servizio.")
            val serviceIntent = Intent(this, AudioRecordingService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            Log.d("DashboardActivity", "Arresto del servizio.")
            val serviceIntent = Intent(this, AudioRecordingService::class.java)
            stopService(serviceIntent)
        }

        updateServiceStatusUI()
    }
}