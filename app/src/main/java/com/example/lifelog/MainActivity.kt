package com.example.lifelog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Launcher per la richiesta a catena dei permessi
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Controlliamo i permessi critici dopo che l'utente ha risposto
            val isAudioGranted = permissions.getOrDefault(Manifest.permission.RECORD_AUDIO, false)
            val isFineLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)

            if (isAudioGranted && isFineLocationGranted) {
                // Permessi principali concessi. Ora controlliamo quello per il background.
                checkBackgroundLocationPermission()
            } else {
                Log.w("MainActivity", "Permessi critici (Audio o Location) negati.")
                Toast.makeText(this, "Permessi essenziali non concessi. L'app potrebbe non funzionare correttamente.", Toast.LENGTH_LONG).show()
                // Finiamo con un risultato di fallimento
                finishWithResult(false)
            }
        }

    // Launcher separato per il permesso in background
    private val requestBackgroundLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("MainActivity", "Tutti i permessi sono stati concessi con successo.")
                finishWithResult(true) // Successo
            } else {
                Log.d("MainActivity", "Permesso di localizzazione in background negato.")
                // Finiamo comunque con successo, l'app può funzionare senza GPS in background.
                finishWithResult(true)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Questa activity non ha più bisogno di un layout visibile,
        // mostra solo le finestre di dialogo dei permessi.

        // Avviamo subito la richiesta dei permessi.
        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        // Creiamo la lista di permessi da chiedere in un unico blocco.
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(this)
                    .setTitle("Posizione in Background")
                    .setMessage("LifeLog ha bisogno di accedere alla posizione in background per associare le coordinate alle registrazioni. Seleziona 'Consenti sempre' nella prossima schermata.")
                    .setPositiveButton("Capito") { _, _ ->
                        requestBackgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("Annulla") { dialog, _ ->
                        dialog.dismiss()
                        finishWithResult(true) // L'utente ha annullato, ma l'app può continuare
                    }
                    .show()
                return
            }
        }
        // Se siamo qui, o la versione è < Q o il permesso è già concesso.
        finishWithResult(true)
    }

    // Funzione helper per chiudere l'activity e restituire un risultato
    private fun finishWithResult(isSuccess: Boolean) {
        val resultCode = if (isSuccess) RESULT_OK else RESULT_CANCELED
        setResult(resultCode)
        finish()
    }
}