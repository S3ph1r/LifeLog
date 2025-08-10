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
import com.example.lifelog.UploaderWorker
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Il frammento per la registrazione del voiceprint dell'utente durante l'onboarding.
 * Guida l'utente a leggere un testo per creare un campione audio per il riconoscimento vocale.
 */
class VoiceprintFragment : Fragment() {

    private val TAG = "VoiceprintFragment"

    private lateinit var recordButton: MaterialButton
    // MODIFICA: Rimosso il riferimento a nextButton locale

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFilePath: String? = null
    private var isRecording = false
    private var isVoiceprintRecordedSuccessfully = false // MODIFICA: Nuovo stato per il voiceprint

    private val cryptoManager = CryptoManager()
    private val audioFileDao by lazy {
        AppDatabase.getInstance(requireContext()).audioFileDao()
    }

    // Riferimento al callback per comunicare con l'Activity host
    private var callback: OnboardingFragmentCallback? = null

    /**
     * Chiamato quando il fragment viene attaccato per la prima volta a un'Activity.
     */
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
        // MODIFICA: nextButton non è più qui

        setupClickListeners()
        updateUI() // Aggiorna lo stato iniziale della UI
    }

    /**
     * Chiamato quando il fragment viene staccato dall'Activity.
     */
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
        // MODIFICA: Il click del nextButton è ora gestito dall'Activity
    }

    private fun updateUI() {
        if (isRecording) {
            recordButton.text = "Ferma Registrazione"
            recordButton.setIconResource(android.R.drawable.ic_media_pause)
            callback?.setNextButtonEnabled(false) // Disabilita Prosegui durante la registrazione
        } else {
            recordButton.text = "Inizia Registrazione"
            recordButton.setIconResource(R.drawable.ic_mic)
            // Abilita Prosegui solo se la registrazione è stata completata con successo
            callback?.setNextButtonEnabled(isVoiceprintRecordedSuccessfully)
        }
    }

    private fun startRecording() {
        val outputDir = File(requireContext().filesDir, "voiceprints_raw")
        if (!outputDir.exists()) outputDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "enrollment_voiceprint_${timestamp}.m4a"
        recordingFilePath = File(outputDir, fileName).absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION") MediaRecorder()
        }.apply {
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
                updateUI()
                Toast.makeText(requireContext(), "Registrazione avviata...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Registrazione voiceprint avviata: $recordingFilePath")
                isVoiceprintRecordedSuccessfully = false // Resetta lo stato di successo
                callback?.setNextButtonEnabled(false) // Disabilita il pulsante "Prosegui"
            } catch (e: Exception) {
                Log.e(TAG, "Errore avvio registrazione voiceprint", e)
                Toast.makeText(requireContext(), "Errore registrazione: ${e.message}", Toast.LENGTH_LONG).show()
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
                updateUI()
                Log.d(TAG, "Registrazione voiceprint fermata.")
                processRecordedVoiceprintFile()
            } catch (e: Exception) {
                Log.e(TAG, "Errore stop registrazione voiceprint: ${e.message}", e)
                Toast.makeText(requireContext(), "Errore stop registrazione.", Toast.LENGTH_SHORT).show()
                recordingFilePath?.let { File(it).delete() }
                recordingFilePath = null
                isRecording = false
                updateUI()
            } finally {
                releaseRecorder()
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
            Log.e(TAG, "File voiceprint troppo piccolo o non esistente. Eliminato.")
            rawFile.delete()
            recordingFilePath = null
            isVoiceprintRecordedSuccessfully = false // Fallimento
            updateUI() // Aggiorna stato del pulsante
            Toast.makeText(requireContext(), "Registrazione non valida. Riprova.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val password = SettingsManager.encryptionPassword
            if (password.isBlank()) {
                Log.e(TAG, "Password di crittografia non impostata. Voiceprint non processato.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Imposta la password nelle impostazioni prima di registrare il voiceprint.", Toast.LENGTH_LONG).show()
                }
                rawFile.delete()
                isVoiceprintRecordedSuccessfully = false // Fallimento
                withContext(Dispatchers.Main) { updateUI() } // Aggiorna stato del pulsante
                return@launch
            }

            try {
                val encryptedFile = cryptoManager.encryptFile(password, rawFile)

                if (encryptedFile != null && encryptedFile.exists()) {
                    val voiceprintEntity = AudioFileEntity(
                        fileName = encryptedFile.name,
                        filePath = encryptedFile.absolutePath,
                        status = AudioFileStatus.VOICEPRINT_PENDING,
                        sizeInBytes = encryptedFile.length(),
                        uploadAttempts = 0
                    )
                    audioFileDao.insert(voiceprintEntity)
                    Log.d(TAG, "Voiceprint criptato e aggiunto al DB per l'elaborazione.")
                    isVoiceprintRecordedSuccessfully = true // Successo!
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Voiceprint registrato con successo!", Toast.LENGTH_SHORT).show()
                        updateUI() // Rende visibile il pulsante "Prosegui"
                    }
                } else {
                    Log.e(TAG, "Criptazione voiceprint fallita.")
                    isVoiceprintRecordedSuccessfully = false // Fallimento
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Errore durante la criptazione del voiceprint. Riprova.", Toast.LENGTH_LONG).show()
                        updateUI()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore in processRecordedVoiceprintFile", e)
                isVoiceprintRecordedSuccessfully = false // Fallimento
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Errore imprevisto durante il processamento del voiceprint.", Toast.LENGTH_LONG).show()
                    updateUI()
                }
            } finally {
                rawFile.delete()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        releaseRecorder()
    }

    // MODIFICA: Questo metodo sarà chiamato dall'Activity quando il pulsante "Prosegui" globale è cliccato.
    fun handleNextButtonClick(): Boolean {
        // L'Activity chiede al fragment se può avanzare.
        // Può avanzare solo se il voiceprint è stato registrato con successo.
        if (!isVoiceprintRecordedSuccessfully) {
            Toast.makeText(requireContext(), "Registra prima il tuo voiceprint per proseguire.", Toast.LENGTH_SHORT).show()
            return false // L'Activity NON deve avanzare
        }
        return true // L'Activity può avanzare
    }
}