package com.example.lifelog

import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.lifelog.databinding.FragmentVoiceprintBinding
import java.io.File
import java.io.IOException

class VoiceprintFragment : Fragment() {

    private var _binding: FragmentVoiceprintBinding? = null
    private val binding get() = _binding!!

    private var mediaRecorder: MediaRecorder? = null
    private var voiceprintFile: File? = null

    var isRecordingComplete: Boolean = false
        private set

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVoiceprintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonRecordStart.setOnClickListener {
            startVoiceprintRecording()
        }
        binding.buttonRecordStop.setOnClickListener {
            stopVoiceprintRecording()
        }
    }

    private fun startVoiceprintRecording() {
        // Creiamo un file temporaneo per il voiceprint nella cache
        voiceprintFile = File(requireContext().cacheDir, "voiceprint_temp.m4a")

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(voiceprintFile!!.absolutePath)
                prepare()
                start()

                updateUI(isRecording = true)
                isRecordingComplete = false
            } catch (e: IOException) {
                Log.e("VoiceprintFragment", "prepare() failed", e)
                Toast.makeText(requireContext(), "Errore nell'avviare la registrazione", Toast.LENGTH_SHORT).show()
                releaseMediaRecorder()
            }
        }
    }

    private fun stopVoiceprintRecording() {
        try {
            mediaRecorder?.stop()
            isRecordingComplete = true
            updateUI(isRecording = false)
            Log.d("VoiceprintFragment", "Registrazione completata in: ${voiceprintFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e("VoiceprintFragment", "stop() failed", e)
            Toast.makeText(requireContext(), "Errore nell'arrestare la registrazione", Toast.LENGTH_SHORT).show()
            isRecordingComplete = false
            voiceprintFile?.delete() // Se lo stop fallisce, il file è corrotto
        } finally {
            releaseMediaRecorder()
        }
    }

    private fun updateUI(isRecording: Boolean) {
        binding.buttonRecordStart.isVisible = !isRecording
        binding.buttonRecordStop.isVisible = isRecording

        binding.textViewRecordingStatus.text = if (isRecording) {
            "Registrazione in corso..."
        } else {
            if (isRecordingComplete) "Registrazione completata!" else "Pronto per registrare"
        }
    }

    fun getVoiceprintFile(): File? {
        return if (isRecordingComplete) voiceprintFile else null
    }

    private fun releaseMediaRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releaseMediaRecorder()
        _binding = null
    }
}