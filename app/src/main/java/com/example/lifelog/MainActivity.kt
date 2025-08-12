package com.example.lifelog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.error
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifelog.audio.RecordingState
import com.example.lifelog.service.AudioRecordingService
// Importa il tuo tema se ne hai uno definito
// import com.example.lifelog.ui.theme.LifeLogTheme  // Esempio di import del tema

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    // Launcher per la richiesta dei permessi
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach { entry ->
                Log.d(TAG, "Permission ${entry.key} granted: ${entry.value}")
                if (!entry.value) {
                    allGranted = false
                }
            }

            if (allGranted) {
                Log.d(TAG, "All necessary permissions granted by user.")
                startRecording()
            } else {
                Log.w(TAG, "Not all permissions were granted by user.")
                // Qui potresti mostrare una UI per spiegare perché i permessi sono necessari
                // e magari un pulsante per riprovare o andare alle impostazioni.
                // Esempio: showPermissionsRationaleSnackbar()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // LifeLogTheme { // Applica il tuo tema personalizzato se disponibile
            MainScreen()
            // }
        }
        Log.d(TAG, "onCreate completed")
    }

    override fun onResume() {
        super.onResume()
        // Lo stato del servizio e della registrazione viene già osservato
        // in modo reattivo dal Composable MainScreen usando collectAsStateWithLifecycle.
        // Puoi aggiungere qui log specifici per onResume se necessario.
        Log.d(TAG, "onResume - Current service state (from static flow immediately): ${AudioRecordingService.currentRecordingState.value}")
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking permissions...")
        val permissionsToRequest = mutableListOf<String>()

        // 1. Permesso RECORD_AUDIO (sempre necessario)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // 2. Permesso POST_NOTIFICATIONS (per Android 13/API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 3. Permesso FOREGROUND_SERVICE_MICROPHONE (per Android 14/API 34+)
        // Questo è necessario se il tuo servizio foreground usa il microfono.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
            }
        }
        // Aggiungi qui altri permessi se necessario (es. LOCATION)

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "All necessary permissions already granted.")
            startRecording() // Tutti i permessi sono già concessi
        }
    }

    private fun startRecording() {
        Log.i(TAG, "Permissions check passed or all granted. Attempting to start service for recording.")
        val serviceIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_START_RECORDING
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
            Log.i(TAG, "Sent ACTION_START_RECORDING to AudioRecordingService.")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting foreground service. Missing FOREGROUND_SERVICE permission in Manifest or other restriction.", e)
            // Mostra un messaggio all'utente o gestisci l'errore
        }
        catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service from MainActivity", e)
            // Gestisci altri errori, es. se il sistema operativo impedisce l'avvio del foreground service
        }
    }

    private fun stopRecording() {
        Log.i(TAG, "Attempting to stop service for recording.")
        val serviceIntent = Intent(this, AudioRecordingService::class.java).apply {
            action = AudioRecordingService.ACTION_STOP_RECORDING
        }
        // startService è sufficiente per inviare un comando a un servizio già in esecuzione.
        startService(serviceIntent)
        Log.i(TAG, "Sent ACTION_STOP_RECORDING to AudioRecordingService.")
    }

    // --- Composable UI ---
    @androidx.compose.runtime.Composable
    fun MainScreen() {
        val context = LocalContext.current // Ottieni il contesto per Intent, ecc.

        // Osserva gli StateFlow dal servizio in modo lifecycle-aware
        val serviceIsRunning by AudioRecordingService.serviceIsRunning.collectAsStateWithLifecycle()
        val currentRecordingState by AudioRecordingService.currentRecordingState.collectAsStateWithLifecycle()

        // LaunchedEffect per loggare i cambiamenti di stato (opzionale, per debug)
        androidx.compose.runtime.LaunchedEffect(serviceIsRunning) {
            Log.d(TAG, "UI Observed service running state: $serviceIsRunning")
        }
        androidx.compose.runtime.LaunchedEffect(currentRecordingState) {
            Log.d(TAG, "UI Observed recording state: $currentRecordingState")
        }

        androidx.compose.material3.Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    title = { androidx.compose.material3.Text("LifeLog Audio Recorder") },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding) // Applica il padding della Scaffold
                    .padding(16.dp), // Aggiungi ulteriore padding interno
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                androidx.compose.material3.Text(
                    text = "Stato Servizio: ${if (serviceIsRunning) "Attivo" else "Non Attivo"}",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                androidx.compose.material3.Text(
                    text = "Stato Registrazione: ${currentRecordingState.name}",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                val buttonEnabled = true // Puoi usare questo per disabilitare i bottoni durante transizioni di stato se necessario

                if (currentRecordingState == RecordingState.IDLE || currentRecordingState == RecordingState.ERROR) {
                    androidx.compose.material3.Button(
                        onClick = {
                            Log.d(TAG, "Start Recording button clicked.")
                            checkAndRequestPermissions()
                        },
                        enabled = buttonEnabled,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        androidx.compose.material3.Text("Start Recording")
                    }
                } else { // RECORDING o INITIALIZING
                    androidx.compose.material3.Button(
                        onClick = {
                            Log.d(TAG, "Stop Recording button clicked.")
                            stopRecording()
                        },
                        enabled = buttonEnabled,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        androidx.compose.material3.Text("Stop Recording")
                    }
                }

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

                // Pulsante per aprire le impostazioni dell'app (utile se i permessi sono negati permanentemente)
                androidx.compose.material3.Button(
                    onClick = {
                        Log.d(TAG, "Open App Settings button clicked.")
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(this)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    androidx.compose.material3.Text("Apri Impostazioni App")
                }
            }
        }
    }
}
