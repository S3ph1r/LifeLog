package com.example.lifelog

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.lifelog.databinding.FragmentVoiceprintBinding
import java.io.File
import java.io.IOException

// L'enum per lo stato rimane utile
private enum class RecordingState { IDLE, RECORDING, FINISHED }

class VoiceprintFragment : Fragment() {

    private var _binding: FragmentVoiceprintBinding? = null
    private val binding get() = _binding!!

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var currentState = RecordingState.IDLE

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

        // Impostiamo i listener per ENTRAMBI i pulsanti
        binding.buttonRecordStart.setOnClickListener {
            startVoiceprintRecording()
        }
        binding.buttonRecordStop.setOnClickListener {
            stopVoiceprintRecording()
        }

        updateUi() // Imposta lo stato iniziale della UI
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startVoiceprintRecording() {
        if (currentState == RecordingState.RECORDING) return

        audioFile = File(requireContext().cacheDir, "voiceprint_temp.m4a")

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
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
                Log.d("VoiceprintFragment", "Registrazione voiceprint avviata.")
            } catch (e: IOException) {
                Log.e("VoiceprintFragment", "prepare() failed", e)
                currentState = RecordingState.IDLE // Torna allo stato iniziale in caso di errore
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
            Log.d("VoiceprintFragment", "Registrazione voiceprint fermata. File: ${audioFile?.absolutePath}")

            // Comunica all'activity che il file è pronto.
            (activity as? OnboardingActivity)?.onVoiceprintRecorded(audioFile!!.absolutePath)

        }?.onFailure {
            Log.e("VoiceprintFragment", "Errore durante lo stop della registrazione", it)
            currentState = RecordingState.IDLE
        }
        mediaRecorder = null
        updateUi()
    }

    private fun updateUi() {
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