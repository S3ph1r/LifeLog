package com.example.lifelog.ui.onboarding

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.lifelog.CryptoManager
import com.example.lifelog.R
import com.example.lifelog.data.ConfigManager
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileEntity
import com.example.lifelog.data.db.AudioFileStatus
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceprintFragment : Fragment() {

    private val TAG = "VoiceprintFragment"

    private lateinit var recordButton: MaterialButton
    private var isRecording = false
    private var isVoiceprintRecordedSuccessfully = false

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    private val cryptoManager = CryptoManager()
    private val audioFileDao by lazy { AppDatabase.getInstance(requireContext()).audioFileDao() }
    private var callback: OnboardingFragmentCallback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnboardingFragmentCallback) {
            callback = context
        } else {
            throw RuntimeException("$context must implement OnboardingFragmentCallback")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_voiceprint, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recordButton = view.findViewById(R.id.recordButton)
        recordButton.setOnClickListener {
            if (isRecording) {
                // Modifica chiave: ora la chiamata a stop è dentro una coroutine
                handleStopRecording()
            } else {
                startRecording()
            }
        }
        updateUI()
    }

    private fun updateUI() {
        recordButton.text = if (isRecording) "Ferma Registrazione" else "Inizia Registrazione"
        recordButton.setIconResource(if (isRecording) android.R.drawable.ic_media_pause else R.drawable.ic_mic)
        recordButton.isEnabled = true // Il pulsante è sempre abilitato
        callback?.setNextButtonEnabled(isVoiceprintRecordedSuccessfully)
    }

    private fun startRecording() {
        // ... (il codice per startRecording rimane invariato)
        val config = ConfigManager.getConfig()
        if (config.userFirstName.isBlank() || config.userAlias.isBlank()) {
            Toast.makeText(requireContext(), "Errore: dati utente non trovati.", Toast.LENGTH_LONG).show()
            return
        }

        val sanitizedFirstName = config.userFirstName.replace(" ", "_")
        val sanitizedLastName = config.userLastName.replace(" ", "_")
        val sanitizedAlias = config.userAlias.replace(" ", "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val identityPart = "${sanitizedFirstName}_${sanitizedLastName}-${sanitizedAlias}"
        val fileName = "enrollment_${identityPart}_${timestamp}.m4a"

        val outputDir = File(requireContext().cacheDir, "voiceprints")
        if (!outputDir.exists()) outputDir.mkdirs()
        outputFile = File(outputDir, fileName)

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(requireContext()) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile!!.absolutePath)

            try {
                prepare()
                start()
                isRecording = true
                isVoiceprintRecordedSuccessfully = false
                updateUI()
                Toast.makeText(requireContext(), "Registrazione avviata...", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e(TAG, "prepare() failed", e)
                Toast.makeText(requireContext(), "Errore nell'avviare la registrazione.", Toast.LENGTH_SHORT).show()
                releaseRecorder()
            }
        }
    }

    // --- NUOVO METODO PER GESTIRE LO STOP IN MODO ASINCRONO ---
    private fun handleStopRecording() {
        if (!isRecording) return

        recordButton.isEnabled = false // Disabilita il pulsante per evitare doppi click
        isRecording = false
        updateUI()
        Toast.makeText(requireContext(), "Finalizzazione registrazione...", Toast.LENGTH_SHORT).show()

        // Lanciamo una coroutine per fare tutto il lavoro pesante in background
        lifecycleScope.launch(Dispatchers.IO) {
            val recorder = mediaRecorder
            val fileToProcess = outputFile

            mediaRecorder = null
            outputFile = null

            // 1. Ferma e rilascia il recorder (operazione bloccante)
            try {
                recorder?.stop()
                recorder?.release()
            } catch (e: Exception) {
                Log.w(TAG, "stop() o release() fallito.", e)
                fileToProcess?.delete()
                // Usciamo dalla coroutine se lo stop fallisce
                return@launch
            }

            // 2. Verifica e processa il file
            if (fileToProcess != null && fileToProcess.exists() && fileToProcess.length() > 4096) {
                processFile(fileToProcess)
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Registrazione non valida. Riprova.", Toast.LENGTH_LONG).show()
                }
                fileToProcess?.delete()
            }
        }
    }

    // processFile ora è privato e chiamato solo dalla coroutine
    private suspend fun processFile(fileToProcess: File) {
        val password = ConfigManager.getConfig().encryptionPassword
        if (password.isBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Errore: password non trovata.", Toast.LENGTH_LONG).show()
            }
            fileToProcess.delete()
            return
        }

        try {
            val encryptedFile = cryptoManager.encryptFile(password, fileToProcess)
            if (encryptedFile != null && encryptedFile.exists()) {
                val finalDestination = File(requireContext().filesDir, encryptedFile.name)
                encryptedFile.renameTo(finalDestination)

                val entity = AudioFileEntity(
                    fileName = finalDestination.name,
                    filePath = finalDestination.absolutePath,
                    status = AudioFileStatus.PENDING_UPLOAD,
                    sizeInBytes = finalDestination.length()
                )
                audioFileDao.insert(entity)

                withContext(Dispatchers.Main) {
                    isVoiceprintRecordedSuccessfully = true
                    updateUI()
                    Toast.makeText(requireContext(), "Impronta vocale salvata!", Toast.LENGTH_SHORT).show()
                }
            } else {
                throw IOException("Criptazione fallita, file non creato.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il processamento del file", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Errore durante il salvataggio.", Toast.LENGTH_SHORT).show()
            }
        } finally {
            fileToProcess.delete()
        }
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione durante il rilascio del recorder", e)
        }
        mediaRecorder = null
        isRecording = false
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            handleStopRecording()
        }
    }

    fun handleNextButtonClick(): Boolean {
        if (!isVoiceprintRecordedSuccessfully) {
            Toast.makeText(requireContext(), "Registra prima la tua impronta vocale.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }
}