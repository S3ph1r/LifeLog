// Percorso: app/src/main/java/com/example/lifelog/ui/onboarding/PermissionsFragment.kt

package com.example.lifelog.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lifelog.OnboardingActivity
import com.example.lifelog.R

class PermissionsFragment : Fragment() {

    // MODIFICA: Riferimento al nuovo pulsante nel fragment
    private lateinit var grantPermissionsButton: Button

    private val TAG = "PermissionsFragment"

    private var callback: OnboardingFragmentCallback? = null

    private val requiredPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            checkAllPermissions()
        }

    private val requestBackgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            checkAllPermissions()
        }

    private val requestIgnoreBatteryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkAllPermissions()
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnboardingFragmentCallback) {
            callback = context
        } else {
            throw RuntimeException("$context deve implementare OnboardingFragmentCallback")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_permissions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // MODIFICA: Inizializza il nuovo pulsante e imposta il listener
        grantPermissionsButton = view.findViewById(R.id.grantPermissionsButton)
        grantPermissionsButton.setOnClickListener {
            // Quando l'utente clicca "Concedi Permessi", avvia il flusso di richiesta
            startPermissionRequestFlow()
        }

        // Al momento della creazione della vista, controlla subito i permessi
        // per aggiornare lo stato del pulsante "Concedi Permessi"
        checkAllPermissions()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    // MODIFICA: Questo metodo sarà chiamato dall'Activity quando il pulsante "Prosegui" globale è cliccato.
    // L'Activity può avanzare SOLO se tutti i permessi sono già stati concessi.
    fun handleNextButtonClick(): Boolean {
        if (!allPermissionsAlreadyGranted()) {
            Toast.makeText(requireContext(), "Concedi prima tutti i permessi necessari per proseguire.", Toast.LENGTH_LONG).show()
            // Se i permessi non sono tutti concessi, non far avanzare l'Activity.
            return false
        }
        // Se tutti i permessi sono già concessi, l'Activity può avanzare.
        return true
    }

    private fun allPermissionsAlreadyGranted(): Boolean {
        val runtimePermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        val backgroundLocationGranted = isBackgroundLocationPermissionGranted()
        val ignoringBatteryOptimizations = isIgnoringBatteryOptimizations()

        return runtimePermissionsGranted && backgroundLocationGranted && ignoringBatteryOptimizations
    }

    private fun startPermissionRequestFlow() {
        // Disabilita il pulsante "Concedi Permessi" mentre la richiesta è in corso
        grantPermissionsButton.isEnabled = false

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            return
        }

        if (!isBackgroundLocationPermissionGranted()) {
            requestBackgroundLocationPermission()
            return
        }

        if (!isIgnoringBatteryOptimizations()) {
            requestIgnoreBatteryOptimizations()
            return
        }

        // Se arriviamo qui, significa che tutti i permessi sono stati concessi.
        // Aggiorniamo lo stato e abilitiamo il pulsante "Prosegui" globale.
        checkAllPermissions()
    }

    // MODIFICA: Aggiornato per abilitare/disabilitare il pulsante "Concedi Permessi" e "Prosegui" globale
    private fun checkAllPermissions() {
        val allGranted = allPermissionsAlreadyGranted()

        if (allGranted) {
            grantPermissionsButton.visibility = View.GONE // Nascondi il pulsante "Concedi Permessi"
            callback?.setNextButtonEnabled(true) // Abilita il pulsante "Prosegui" globale
            Toast.makeText(requireContext(), "Tutti i permessi sono stati concessi!", Toast.LENGTH_SHORT).show()
        } else {
            grantPermissionsButton.visibility = View.VISIBLE // Mostra il pulsante "Concedi Permessi"
            grantPermissionsButton.isEnabled = true // Abilita il pulsante "Concedi Permessi" (dopo una richiesta fallita)
            callback?.setNextButtonEnabled(false) // Disabilita il pulsante "Prosegui" globale
        }
    }


    private fun isBackgroundLocationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestBackgroundLocationPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permesso Posizione in Background")
            .setMessage("LifeLog ha bisogno di accedere alla posizione in background per funzionare sempre. Nella prossima schermata, seleziona 'Consenti sempre'.")
            .setPositiveButton("Vai alle Impostazioni") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireActivity().packageName, null))
                startActivity(intent) // Nota: non c'è un launcher specifico per verificare il ritorno da questo intent
            }
            .setCancelable(false)
            .show()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        AlertDialog.Builder(requireContext())
            .setTitle("Ottimizzazione Batteria")
            .setMessage("Infine, escludi LifeLog dalle ottimizzazioni della batteria per garantire che non venga interrotto dal sistema.")
            .setPositiveButton("Vai alle Impostazioni") { _, _ ->
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${requireContext().packageName}")
                requestIgnoreBatteryLauncher.launch(intent)
            }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Quando il fragment torna in primo piano (es. dopo che l'utente torna dalle impostazioni)
        // controlliamo di nuovo lo stato dei permessi per aggiornare il pulsante.
        checkAllPermissions()
    }
}