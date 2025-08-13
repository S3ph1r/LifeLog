package com.example.lifelog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // --- NUOVO: Gestore per il permesso BACKGROUND_LOCATION ---
    private val requestBackgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "Permesso BACKGROUND_LOCATION concesso.")
                startAudioService() // Tutti i permessi sono stati concessi, avviamo il servizio.
            } else {
                Log.d("MainActivity", "Permesso BACKGROUND_LOCATION negato.")
                Toast.makeText(this, "Senza accesso in background, il GPS non funzionerà a schermo spento.", Toast.LENGTH_LONG).show()
                startAudioService() // Avviamo il servizio comunque, registrerà senza GPS.
            }
        }

    // --- NUOVO: Gestore per il permesso FINE_LOCATION ---
    private val requestFineLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "Permesso FINE_LOCATION concesso.")
                // Se concesso, procediamo a chiedere il permesso per il background.
                checkBackgroundLocationPermission()
            } else {
                Log.d("MainActivity", "Permesso FINE_LOCATION negato.")
                Toast.makeText(this, "Permesso di localizzazione negato.", Toast.LENGTH_SHORT).show()
                startAudioService() // Avviamo il servizio comunque, registrerà senza GPS.
            }
        }

    // Gestore per il permesso RECORD_AUDIO
    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "Permesso RECORD_AUDIO concesso.")
                checkFineLocationPermission() // Prossimo passo: chiedere la posizione.
            } else {
                Log.d("MainActivity", "Permesso RECORD_AUDIO negato.")
                Toast.makeText(this, "Permesso per registrare l'audio negato. L'app non può funzionare.", Toast.LENGTH_LONG).show()
            }
        }

    // Gestore per il permesso POST_NOTIFICATIONS
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("MainActivity", "Permesso NOTIFICHE concesso.")
            } else {
                Log.d("MainActivity", "Permesso NOTIFICHE negato.")
            }
            checkAudioPermission() // Indipendentemente dall'esito, procediamo con il prossimo permesso.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton: Button = findViewById(R.id.btn_start_service)
        startButton.setOnClickListener {
            checkNotificationPermission()
        }
    }

    // La catena di controlli ora è più lunga:
    // 1. Notifiche -> 2. Audio -> 3. Posizione Fine -> 4. Posizione Background

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> checkAudioPermission()
                else -> requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            checkAudioPermission()
        }
    }

    private fun checkAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> checkFineLocationPermission()
            else -> requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun checkFineLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> checkBackgroundLocationPermission()
            else -> requestFineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun checkBackgroundLocationPermission() {
        // Il permesso in background è necessario solo da Android 10 (API 29) in su.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED -> startAudioService()
                else -> {
                    // Mostriamo una spiegazione del perché ci serve questo permesso speciale.
                    AlertDialog.Builder(this)
                        .setTitle("Permesso Posizione in Background")
                        .setMessage("LifeLog ha bisogno di accedere alla tua posizione anche quando l'app è chiusa per associare le coordinate geografiche alle registrazioni audio. Per favore, seleziona 'Consenti sempre' nella prossima schermata.")
                        .setPositiveButton("Capito") { _, _ ->
                            requestBackgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                        .setNegativeButton("Annulla") { dialog, _ ->
                            dialog.dismiss()
                            startAudioService() // Parte senza GPS
                        }
                        .show()
                }
            }
        } else {
            startAudioService() // Sulle versioni più vecchie, FINE_LOCATION basta.
        }
    }

    private fun startAudioService() {
        Log.d("MainActivity", "Fine del flusso di permessi. Avvio del servizio.")
        val serviceIntent = Intent(this, AudioRecordingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Servizio attivato!", Toast.LENGTH_SHORT).show()

        val prefs = AppPreferences.getInstance(this)
        prefs.isOnboardingCompleted = true
        prefs.isServiceActive = true

        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}