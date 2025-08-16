package com.example.lifelog

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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lifelog.databinding.FragmentPermissionsBinding

class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    // Launcher per i permessi standard (Foreground)
    private val requestStandardPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

            if (fineLocationGranted) {
                Log.d("PermissionsFragment", "Permessi foreground concessi. Procedo con la richiesta in background.")
                // Una volta ottenuti i permessi foreground, chiediamo quello in background
                requestBackgroundLocationPermission()
            } else {
                Log.w("PermissionsFragment", "Permessi foreground non completamente concessi.")
                Toast.makeText(requireContext(), "I permessi di base sono necessari.", Toast.LENGTH_SHORT).show()
                updateUiState()
            }
        }

    // Launcher separato per il permesso di localizzazione in background
    private val requestBackgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Log.d("PermissionsFragment", "Permesso di localizzazione in background concesso.")
                // Una volta ottenuto, procediamo con l'ottimizzazione batteria
                requestBatteryOptimizationPermission()
            } else {
                Log.w("PermissionsFragment", "Permesso di localizzazione in background negato.")
                Toast.makeText(requireContext(), "La localizzazione in background migliora il servizio.", Toast.LENGTH_SHORT).show()
                // Anche se negato, andiamo avanti con la batteria, non è bloccante
                requestBatteryOptimizationPermission()
            }
        }

    // Lista dei permessi da chiedere nel primo step
    private val standardPermissions = mutableListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonGrantPermissions.setOnClickListener {
            requestStandardPermissions()
        }
        updateUiState()
    }

    private fun requestStandardPermissions() {
        Log.d("PermissionsFragment", "Richiesta permessi standard (foreground)...")
        requestStandardPermissionsLauncher.launch(standardPermissions)
    }

    private fun requestBackgroundLocationPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle("Localizzazione in Background")
            .setMessage("LifeLog ha bisogno di accedere alla tua posizione anche quando l'app è in background per registrare correttamente i luoghi dei tuoi ricordi. Seleziona 'Consenti sempre' nella prossima schermata.")
            .setPositiveButton("OK") { _, _ ->
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton("Annulla") { _, _ ->
                // L'utente ha annullato, procediamo comunque con la batteria
                requestBatteryOptimizationPermission()
            }
            .show()
    }

    private fun requestBatteryOptimizationPermission() {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            Log.d("PermissionsFragment", "App già esente da ottimizzazione batteria.")
            signalPermissionsGranted()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Ottimizzazione Batteria")
                .setMessage("Per garantire che la registrazione non si interrompa, disabilita l'ottimizzazione della batteria per LifeLog.")
                .setPositiveButton("OK") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Annulla", null)
                .show()
        }
    }

    private fun signalPermissionsGranted() {
        (activity as? OnboardingActivity)?.onPermissionsGranted()
        updateUiState()
    }

    private fun updateUiState() {
        if (areAllCorePermissionsGranted()) {
            binding.buttonGrantPermissions.text = "Permessi Concessi"
            binding.buttonGrantPermissions.isEnabled = false
            binding.textViewPermissionsStatus.text = "Tutti i permessi principali sono stati concessi."
        } else {
            binding.buttonGrantPermissions.text = "Concedi Permessi"
            binding.buttonGrantPermissions.isEnabled = true
            binding.textViewPermissionsStatus.text = "L'app necessita di alcuni permessi per funzionare."
        }
    }

    // Controlla solo i permessi essenziali per andare avanti
    private fun areAllCorePermissionsGranted(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val recordAudioGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return fineLocationGranted && recordAudioGranted
    }

    override fun onResume() {
        super.onResume()
        Log.d("PermissionsFragment", "onResume, ricontrollo permessi...")
        if (areAllCorePermissionsGranted()) {
            // Se i permessi base ci sono, potremmo dover completare il flusso
            val backgroundLocationGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptIgnored = powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)

            if (backgroundLocationGranted && batteryOptIgnored) {
                signalPermissionsGranted()
            }
        }
        updateUiState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}