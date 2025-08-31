package com.example.lifelog

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Import corretto per i ViewModel condivisi
import com.example.lifelog.databinding.FragmentVoiceprintBinding
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class RecordingState { IDLE, RECORDING, FINISHED }

class VoiceprintFragment : Fragment() {

    private var _binding: FragmentVoiceprintBinding? = null
    private val binding get() = _binding!!

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var currentState = RecordingState.IDLE

    // ViewModel condiviso con l'Activity per accedere ai dati dell'utente
    private val onboardingViewModel: OnboardingViewModel by activityViewModels()
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val fragmentScope = CoroutineScope(Dispatchers.Main) // Coroutine scope per il fragment

    companion object {
        private const val TAG = "VoiceprintFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceprintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!hasAudioPermission()) {
            binding.buttonRecordStart.isEnabled = false
            binding.textViewRecordingStatus.text = "Permesso per il microfono non concesso."
            return
        }

        binding.buttonRecordStart.setOnClickListener {
            // Avviamo la registrazione in una coroutine per gestire l'ottenimento del GPS
            fragmentScope.launch {
                startVoiceprintRecording()
            }
        }
        binding.buttonRecordStop.setOnClickListener {
            stopVoiceprintRecording()
        }

        updateUi()
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun startVoiceprintRecording() {
        if (currentState == RecordingState.RECORDING) return

        // --- NUOVA LOGICA PER IL NOME DEL FILE ---
        val alias = onboardingViewModel.alias ?: "user"
        val timeStamp = dateFormat.format(Date())
        val location = getCurrentLocation()
        val lat = location?.latitude
        val lon = location?.longitude

        val fileName = if (lat != null && lon != null) {
            "${alias}_voiceprint_${timeStamp}_lat${"%.4f".format(Locale.US, lat)}_lon${"%.4f".format(Locale.US, lon)}.m4a"
        } else {
            "${alias}_voiceprint_$timeStamp.m4a"
        }

        audioFile = File(requireContext().cacheDir, fileName)
        Log.d(TAG, "File del voiceprint creato in: ${audioFile?.absolutePath}")
        // --- FINE NUOVA LOGICA ---

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile!!.absolutePath)
            try {
                prepare()
                start()
                currentState = RecordingState.RECORDING
                Log.d(TAG, "Registrazione voiceprint avviata.")
            } catch (e: IOException) {
                Log.e(TAG, "prepare() failed", e)
                currentState = RecordingState.IDLE
            }
        }
        updateUi()
    }

    private fun stopVoiceprintRecording() {
        if (currentState != RecordingState.RECORDING) return

        mediaRecorder?.runCatching {
            stop()
            release()
            currentState = RecordingState.FINISHED
            Log.d(TAG, "Registrazione voiceprint fermata. File: ${audioFile?.absolutePath}")

            (activity as? OnboardingActivity)?.onVoiceprintRecorded(audioFile!!.absolutePath)

        }?.onFailure {
            Log.e(TAG, "Errore durante lo stop della registrazione", it)
            currentState = RecordingState.IDLE
        }
        mediaRecorder = null
        updateUi()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permesso di localizzazione non concesso.")
            return null
        }
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
        } catch (e: Exception) {
            Log.e(TAG, "Impossibile ottenere la posizione GPS.", e)
            null
        }
    }

    private fun updateUi() {
        // ... codice invariato ...
        when (currentState) {
            RecordingState.IDLE -> {
                binding.buttonRecordStart.visibility = View.VISIBLE
                binding.buttonRecordStop.visibility = View.GONE
                binding.textViewRecordingStatus.text = "Pronto per registrare"
            }
            RecordingState.RECORDING -> {
                binding.buttonRecordStart.visibility = View.GONE
                binding.buttonRecordStop.visibility = View.VISIBLE
                binding.textViewRecordingStatus.text = "In registrazione..."
            }
            RecordingState.FINISHED -> {
                binding.buttonRecordStart.visibility = View.VISIBLE
                binding.buttonRecordStop.visibility = View.GONE
                binding.textViewRecordingStatus.text = "Registrazione completata! Premi 'Fine' per continuare."
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaRecorder?.release()
        mediaRecorder = null
        _binding = null
    }
}