// Percorso: app/src/main/java/com/example/lifelog/ui/onboarding/VoiceprintFragment.kt

package com.example.lifelog.ui.onboarding

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lifelog.CryptoManager
import com.example.lifelog.OnboardingActivity
import com.example.lifelog.R
import com.example.lifelog.data.SettingsManager
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileEntity
import com.example.lifelog.data.db.AudioFileStatus
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceprintFragment : Fragment() {

    private val TAG = "VoiceprintFragment"

    private lateinit var recordButton: MaterialButton
    private var isRecording = false
    private var isVoiceprintRecordedSuccessfully = false
    private var recordingFilePath: String? = null

    private val cryptoManager = CryptoManager()
    private val audioFileDao by lazy { AppDatabase.getInstance(requireContext()).audioFileDao() }
    private var callback: OnboardingFragmentCallback? = null

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
        return inflater.inflate(R.layout.fragment_voiceprint, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recordButton = view.findViewById(R.id.recordButton)
        setupClickListeners()
        updateUI()
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    private fun setupClickListeners() {
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    private fun updateUI() {
        if (isRecording) {
            recordButton.text = "Ferma Registrazione"
            recordButton.setIconResource(android.R.drawable.ic_media_pause)
            callback?.setNextButtonEnabled(false)
        } else {
            recordButton.text = "Inizia Registrazione"
            recordButton.setIconResource(R.drawable.ic_mic)
            callback?.setNextButtonEnabled(isVoiceprintRecordedSuccessfully)
        }
    }

    private fun startRecording() {
        val outputDir = File(requireContext().filesDir, "voiceprints_raw")
        if (!outputDir.exists()) outputDir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "enrollment_voiceprint_${timestamp}.m4a"
        recordingFilePath = File(outputDir, fileName).absolutePath

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(recordingFilePath)
            try {
                prepare()
                start()
                isRecording = true
                isVoiceprintRecordedSuccessfully = false
                updateUI()
                Toast.makeText(requireContext(), "Registrazione avviata...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Registrazione voiceprint avviata: $recordingFilePath")
            } catch (e: Exception) {
                Log.e(TAG, "Errore avvio registrazione voiceprint", e)
                isRecording = false
                updateUI()
                releaseRecorder()
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            try {
                stop()
                release()
                isRecording = false
                Log.d(TAG, "Registrazione voiceprint fermata.")
                processRecordedVoiceprintFile()
            } catch (e: Exception) {
                Log.e(TAG, "Errore stop registrazione voiceprint", e)
                recordingFilePath?.let { File(it).delete() }
                recordingFilePath = null
                isRecording = false
            } finally {
                releaseRecorder()
                updateUI()
            }
        }
    }

    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun processRecordedVoiceprintFile() {
        val path = recordingFilePath ?: return
        val rawFile = File(path)
        if (!rawFile.exists() || rawFile.length() < 1024) {
            Log.e(TAG, "File voiceprint non valido. Eliminato.")
            rawFile.delete()
            recordingFilePath = null
            isVoiceprintRecordedSuccessfully = false
            Toast.makeText(requireContext(), "Registrazione non valida. Riprova.", Toast.LENGTH_LONG).show()
            updateUI()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val password = SettingsManager.encryptionPassword
            if (password.isBlank()) {
                Log.e(TAG, "Password non trovata. Impossibile processare voiceprint.")
                rawFile.delete()
                isVoiceprintRecordedSuccessfully = false
                withContext(Dispatchers.Main) { updateUI() }
                return@launch
            }

            try {
                val encryptedFile = cryptoManager.encryptFile(password, rawFile)
                if (encryptedFile != null && encryptedFile.exists()) {
                    val voiceprintEntity = AudioFileEntity(
                        fileName = encryptedFile.name,
                        filePath = encryptedFile.absolutePath,
                        // --- MODIFICA CHIAVE ---
                        status = AudioFileStatus.PENDING_UPLOAD,
                        sizeInBytes = encryptedFile.length()
                    )
                    audioFileDao.insert(voiceprintEntity)
                    Log.d(TAG, "Voiceprint criptato e aggiunto al DB per l'upload.")
                    isVoiceprintRecordedSuccessfully = true
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Voiceprint registrato!", Toast.LENGTH_SHORT).show()
                        updateUI()
                    }
                } else {
                    Log.e(TAG, "Criptazione voiceprint fallita.")
                    isVoiceprintRecordedSuccessfully = false
                    withContext(Dispatchers.Main) { updateUI() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore in processRecordedVoiceprintFile", e)
                isVoiceprintRecordedSuccessfully = false
                withContext(Dispatchers.Main) { updateUI() }
            } finally {
                rawFile.delete()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releaseRecorder()
    }

    fun handleNextButtonClick(): Boolean {
        if (!isVoiceprintRecordedSuccessfully) {
            Toast.makeText(requireContext(), "Registra prima il tuo voiceprint.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}