// Percorso: app/src/main/java/com/example/lifelog/MainActivity.kt

package com.example.lifelog

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lifelog.data.SettingsManager
import com.example.lifelog.ui.main.MainViewModel
import com.google.android.material.button.MaterialButton
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 101
    private val TAG = "MainActivity"

    private val viewModel: MainViewModel by viewModels()

    private lateinit var toolbar: Toolbar
    private lateinit var buttonToggleRec: MaterialButton
    private lateinit var buttonForceUpload: Button
    private lateinit var recyclerViewFiles: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var fileListAdapter: FileListAdapter

    private var audioService: AudioRecorderService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "ServiceConnection: Connesso al servizio.")
            val binder = service as AudioRecorderService.AudioServiceBinder
            audioService = binder.getService()
            isServiceBound = true
            observeServiceState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "ServiceConnection: Disconnesso dal servizio.")
            isServiceBound = false
            audioService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- MODIFICA CHIAVE: Logica del "Guardiano" ---
        if (!SettingsManager.isOnboardingComplete) {
            // Se l'onboarding non è completo, avvia l'OnboardingActivity
            // e interrompi l'esecuzione di onCreate per MainActivity.
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish() // Chiude MainActivity per evitare che rimanga nello stack
            return   // Esce dal metodo onCreate
        }
        // --- FINE MODIFICA ---

        // Questo codice verrà eseguito solo se l'onboarding è già stato completato.
        setContentView(R.layout.activity_main)

        setupViews()
        setupButtons()
        observeViewModel()
        schedulePeriodicUploader()

        if (savedInstanceState == null) {
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
            }
        }
    }

    // ... (tutto il resto del codice rimane invariato)

    override fun onStart() {
        super.onStart()
        // Controlliamo che l'onboarding sia completo prima di fare il bind al servizio
        if (SettingsManager.isOnboardingComplete) {
            Intent(this, AudioRecorderService::class.java).also { intent ->
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        statusText = findViewById(R.id.textViewStatus)
        buttonToggleRec = findViewById(R.id.buttonToggleRec)
        val buttonSettings: Button = findViewById(R.id.buttonSettings)
        buttonForceUpload = findViewById(R.id.buttonForceUpload)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = insets.top)
            view.findViewById<RecyclerView>(R.id.recyclerViewFiles).updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        fileListAdapter = FileListAdapter()
        recyclerViewFiles = findViewById(R.id.recyclerViewFiles)
        recyclerViewFiles.adapter = fileListAdapter
    }

    private fun setupButtons() {
        buttonToggleRec.setOnClickListener {
            val currentState = audioService?.recordingState?.value
            when (currentState) {
                RecordingState.IDLE, RecordingState.PAUSED, null -> checkAndRequestPermissions()
                RecordingState.RECORDING -> {
                    startService(Intent(this, AudioRecorderService::class.java).apply {
                        action = AudioRecorderService.ACTION_PAUSE_RECORDING
                    })
                }
            }
        }
        findViewById<Button>(R.id.buttonSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        buttonForceUpload.setOnClickListener {
            triggerUploadWorker()
        }
    }

    private fun observeViewModel() {
        viewModel.pendingFiles.observe(this) { fileList ->
            val displayList = fileList.map { entity ->
                RecordingFile(name = entity.fileName, sizeInKb = entity.sizeInBytes / 1024)
            }
            fileListAdapter.submitList(displayList)
            buttonForceUpload.isEnabled = fileList.isNotEmpty()
        }
    }

    private fun observeServiceState() {
        audioService?.recordingState?.observe(this) { state ->
            updateRecordingUI(state)
        }
    }

    private fun updateRecordingUI(state: RecordingState?) {
        when (state) {
            RecordingState.IDLE -> {
                statusText.text = "Stato: In attesa"
                buttonToggleRec.text = "Avvio Registrazione"
                buttonToggleRec.setIconResource(android.R.drawable.ic_media_play)
                buttonToggleRec.isEnabled = true
            }
            RecordingState.RECORDING -> {
                statusText.text = "Stato: Registrazione in corso..."
                buttonToggleRec.text = "Pausa Registrazione"
                buttonToggleRec.setIconResource(android.R.drawable.ic_media_pause)
                buttonToggleRec.isEnabled = true
            }
            RecordingState.PAUSED -> {
                statusText.text = "Stato: Registrazione in pausa"
                buttonToggleRec.text = "Riprendi Registrazione"
                buttonToggleRec.setIconResource(android.R.drawable.ic_media_play)
                buttonToggleRec.isEnabled = true
            }
            null -> {
                statusText.text = "Stato: Inizializzazione..."
                buttonToggleRec.isEnabled = false
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        AlertDialog.Builder(this)
            .setTitle("Ottimizzazione Batteria Richiesta")
            .setMessage("Per funzionare correttamente in background senza interruzioni, LifeLog " +
                    "ha bisogno di essere esclusa dalle ottimizzazioni della batteria.\n\n" +
                    "Nella schermata successiva, potrebbe essere necessario cercare LifeLog " +
                    "e selezionare l'opzione 'Non ottimizzare' o 'Nessuna restrizione'.")
            .setPositiveButton("Vai alle Impostazioni") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Impossibile aprire la schermata ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.", e)
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Impossibile aprire anche la schermata di fallback.", e2)
                        Toast.makeText(this, "Impossibile aprire automaticamente le impostazioni della batteria.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .setCancelable(false)
            .show()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(this)
                    .setTitle("Permesso Posizione in Background")
                    .setMessage("LifeLog necessita di accedere alla posizione in background per associare i ricordi audio al luogo di registrazione. Seleziona 'Consenti sempre' nella schermata successiva.")
                    .setPositiveButton("Vai alle Impostazioni") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                        startActivity(intent)
                    }
                    .setNegativeButton("Annulla", null).show()
            } else {
                startRecordingService()
            }
        } else {
            startRecordingService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkBackgroundLocationPermission()
            } else {
                Toast.makeText(this, "Permessi necessari negati.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRecordingService() {
        val serviceIntent = Intent(this, AudioRecorderService::class.java).apply { action = AudioRecorderService.ACTION_START_RECORDING }
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun schedulePeriodicUploader() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val uploadWorkRequest = PeriodicWorkRequestBuilder<UploaderWorker>(1, TimeUnit.HOURS).setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("PeriodicLifeLogUploader", ExistingPeriodicWorkPolicy.KEEP, uploadWorkRequest)
    }

    private fun triggerUploadWorker() {
        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploaderWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(uploadWorkRequest)
        Toast.makeText(this, "Avvio upload forzato...", Toast.LENGTH_SHORT).show()
    }
}