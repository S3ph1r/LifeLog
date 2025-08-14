package com.example.lifelog

import android.Manifest
import android.content.Context // <-- IMPORT AGGIUNTO QUI
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

    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                Log.d("PermissionsFragment", "Tutti i permessi standard sono stati concessi.")
                requestBatteryOptimizationPermission()
            } else {
                Log.w("PermissionsFragment", "Non tutti i permessi sono stati concessi.")
                Toast.makeText(requireContext(), "Alcuni permessi sono necessari per il funzionamento.", Toast.LENGTH_LONG).show()
            }
        }

    private val requiredPermissions = mutableListOf(
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
    }



    private fun requestStandardPermissions() {
        Log.d("PermissionsFragment", "Richiesta dei permessi standard.")
        requestMultiplePermissionsLauncher.launch(requiredPermissions)
    }

    private fun requestBatteryOptimizationPermission() {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            Log.d("PermissionsFragment", "L'app è già esente dalle ottimizzazioni della batteria.")
            (activity as? OnboardingActivity)?.onPermissionsGranted()
        } else {
            Log.d("PermissionsFragment", "Richiesta per ignorare le ottimizzazioni della batteria.")
            AlertDialog.Builder(requireContext())
                .setTitle("Ottimizzazione Batteria")
                .setMessage("Per garantire che il servizio di registrazione non venga interrotto, è necessario disabilitare l'ottimizzazione della batteria per LifeLog. Verrai ora reindirizzato alle impostazioni di sistema.")
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

    override fun onResume() {
        super.onResume()
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        val allStandardPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

        if (allStandardPermissionsGranted && powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)) {
            Log.d("PermissionsFragment", "Tutti i permessi, inclusa la batteria, sono ora concessi.")
            (activity as? OnboardingActivity)?.onPermissionsGranted()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}