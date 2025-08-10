// Percorso: app/src/main/java/com/example/lifelog/audio/AudioRecorderManager.kt

package com.example.lifelog.audio

import android.content.Context
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.lifelog.RecordingState
import com.example.lifelog.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Gestisce il ciclo di vita del MediaRecorder per la registrazione audio,
 * inclusa la segmentazione dei file e la gestione delle posizioni.
 * Notifica un listener quando un segmento è pronto per il post-processing.
 */
class AudioRecorderManager(
    private val context: Context,
    private val scope: CoroutineScope, // Lo scope di coroutine del servizio
    private val onSegmentReady: (File) -> Unit, // Callback quando un segmento è pronto
    private val onRecordingStateChange: (RecordingState) -> Unit // Callback per lo stato
) {
    private val TAG = "AudioRecorderManager"

    private var mediaRecorder: MediaRecorder? = null
    private var recordingJob: Job? = null
    private var currentRecordingFile: File? = null

    // Le informazioni di localizzazione verranno passate esternamente da AudioRecorderService
    var lastKnownLocation: Location? = null

    // Stato di registrazione gestito internamente
    private var _recordingState: RecordingState = RecordingState.IDLE
        set(value) {
            if (field != value) {
                field = value
                onRecordingStateChange(value) // Notifica il listener esterno
            }
        }

    /**
     * Avvia il ciclo di registrazione audio, creando segmenti della durata predefinita.
     */
    fun startRecordingCycle() {
        if (recordingJob?.isActive == true) {
            Log.d(TAG, "Ciclo di registrazione già attivo.")
            return
        }

        Log.d(TAG, "Avvio ciclo di registrazione...")
        _recordingState = RecordingState.RECORDING

        // Lancia una coroutine per gestire il ciclo di registrazione segmentato.
        recordingJob = scope.launch {
            while (isActive) {
                val segmentDurationMinutes = 5L // Durata fissa: 5 minuti
                val segmentDurationMs = TimeUnit.MINUTES.toMillis(segmentDurationMinutes)

                currentRecordingFile = startNewSegment()
                if (currentRecordingFile == null) {
                    Log.e(TAG, "Creazione segmento fallita, ciclo interrotto.")
                    _recordingState = RecordingState.IDLE // Aggiorna stato
                    break // Esci dal ciclo se non riusciamo a creare il file
                }

                delay(segmentDurationMs) // Attende la durata del segmento

                if (isActive) { // Controlla se la coroutine è ancora attiva (non cancellata)
                    currentRecordingFile?.let { file ->
                        stopAndFinalizeRecording(file)?.let { finalizedFile ->
                            onSegmentReady(finalizedFile) // Chiamata al callback!
                        }
                    }
                }
            }
            _recordingState = RecordingState.IDLE // Reset stato al termine del ciclo
            Log.d(TAG, "Ciclo di registrazione terminato.")
        }
    }

    /**
     * Ferma il ciclo di registrazione. Può essere una pausa o uno stop definitivo.
     * @param isPaused true se il servizio è in pausa, false se è uno stop definitivo.
     */
    fun stopRecordingCycle(isPaused: Boolean) {
        recordingJob?.cancel() // Cancella la coroutine del ciclo
        recordingJob = null
        releaseRecorder() // Rilascia il MediaRecorder
        _recordingState = if (isPaused) RecordingState.PAUSED else RecordingState.IDLE
        Log.d(TAG, "Ciclo di registrazione fermato. Stato: $_recordingState")
    }

    /**
     * Avvia una nuova registrazione audio per un singolo segmento.
     * Salva il file nella directory protetta del dispositivo (DE storage).
     * @return Il File creato, o null se fallisce.
     */
    private fun startNewSegment(): File? {
        // Usiamo il contesto DE per assicurarci che la cartella sia sempre scrivibile,
        // anche in modalità Direct Boot (dispositivo bloccato).
        val contextDE = context.createDeviceProtectedStorageContext()
        val outputDir = File(contextDE.filesDir, "recordings_raw")
        if (!outputDir.exists()) outputDir.mkdirs()

        val fileName = getOutputFileName() + ".m4a"
        val outputFile = File(outputDir, fileName)

        // Inizializza il MediaRecorder.
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context) // Costruttore moderno
        } else {
            @Suppress("DEPRECATION") MediaRecorder() // Costruttore deprecato
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile.absolutePath)
            try {
                prepare() // Prepara il recorder
                start()   // Avvia la registrazione
                Log.d(TAG, "Nuovo segmento in registrazione (storage DE): ${outputFile.absolutePath}")
                return outputFile
            } catch (e: Exception) {
                Log.e(TAG, "MediaRecorder.prepare() fallito", e)
                // Se la preparazione o l'avvio falliscono, rilascia subito il recorder.
                releaseRecorder()
                return null
            }
        }
    }

    /**
     * Ferma la registrazione corrente e finalizza il file.
     * @param fileToProcess Il file che è stato registrato.
     * @return Il File finalizzato, o null se la finalizzazione fallisce o il file è invalido.
     */
    private fun stopAndFinalizeRecording(fileToProcess: File): File? {
        try {
            mediaRecorder?.stop() // Ferma la registrazione
        } catch (e: Exception) {
            Log.w(TAG, "MediaRecorder.stop() fallito, probabile segmento troppo breve o invalido.", e)
            fileToProcess.delete() // Elimina il file corrotto
            return null
        } finally {
            releaseRecorder() // Rilascia le risorse del recorder
        }
        // Piccola attesa per assicurarsi che il file sia stato completamente scritto su disco.
        runBlocking { delay(250) }
        // Verifica che il file esista e non sia vuoto (un file audio non può essere troppo piccolo).
        return if (fileToProcess.exists() && fileToProcess.length() > 1024) {
            fileToProcess // Restituisce il file se valido
        } else {
            Log.w(TAG, "File registrato è troppo piccolo o non esiste. Eliminato: ${fileToProcess.name}")
            fileToProcess.delete() // Elimina il file invalido
            null
        }
    }

    /**
     * Rilascia le risorse del MediaRecorder.
     */
    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    /**
     * Genera un nome file standardizzato con timestamp e coordinate GPS.
     */
    private fun getOutputFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // Formatta lat/lon a 4 cifre decimali, o "null" se non disponibili
        val lat = lastKnownLocation?.latitude?.let { "%.4f".format(Locale.US, it) } ?: "null"
        val lon = lastKnownLocation?.longitude?.let { "%.4f".format(Locale.US, it) } ?: "null"
        return "segment_${timestamp}_lat${lat}_lon${lon}"
    }

    // Metodo pubblico per ottenere lo stato corrente (per AudioRecorderService)
    fun getRecordingState(): RecordingState = _recordingState
}