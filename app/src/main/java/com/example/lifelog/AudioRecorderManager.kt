package com.example.lifelog.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CancellationException // Importa specificamente CancellationException

class AudioRecorderManager(
    private val applicationContext: Context,
    private val onSegmentReady: (File) -> Unit, // Callback per quando un segmento è pronto
    private val onRecordingStateChange: (RecordingState) -> Unit, // Callback per il cambio di stato
    private val externalScope: CoroutineScope? = null // Scope esterno opzionale
) {
    private val TAG = "AudioRecorderManager"

    // Usa lo scope esterno se fornito, altrimenti creane uno nuovo con SupervisorJob e Dispatchers.IO
    // SupervisorJob assicura che il fallimento di un figlio non cancelli l'intero scope (utile se questo manager gestisce più task)
    private val serviceScope = externalScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)


    private var mediaRecorder: MediaRecorder? = null
    private var currentSegmentFile: File? = null
    private var recordingJob: Job? = null

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    companion object {
        val SEGMENT_DURATION_MS = TimeUnit.MINUTES.toMillis(5) // Durata di ogni segmento (es. 5 minuti)
        private const val RAW_AUDIO_SUBDIR = "audio_segments_raw"
    }

    init {
        // Osserva i cambiamenti di stato interni e notifica il callback
        serviceScope.launch {
            _recordingState.collect { state ->
                Log.d(TAG, "Internal state changed to: $state. Notifying callback.")
                onRecordingStateChange(state)
            }
        }
    }


    private fun getOutputDirectory(context: Context, subDir: String): File {
        val baseDir = context.getExternalFilesDir(null) // Scrive nella directory specifica dell'app
        val recordingsDir = File(baseDir, subDir)
        if (!recordingsDir.exists()) {
            if (!recordingsDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: ${recordingsDir.absolutePath}")
                // Potresti voler gestire questo errore in modo più robusto
            }
        }
        return recordingsDir
    }

    private fun createNewSegmentFile(): File? {
        val outputDir = getOutputDirectory(applicationContext, RAW_AUDIO_SUBDIR)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        // TODO: Aggiungere logica per ottenere latitudine e longitudine se necessario e disponibile
        val filename = "rec_${timestamp}_noLat_noLon.m4a" // O .3gp, .mp4 a seconda del formato
        val file = File(outputDir, filename)
        Log.d(TAG, "New segment file created: ${file.absolutePath}")
        return file
    }


    private suspend fun startNewSegment(): File? {
        if (_recordingState.value != RecordingState.RECORDING) {
            Log.w(TAG, "startNewSegment called but not in RECORDING state. Current: ${_recordingState.value}")
            return null
        }

        currentSegmentFile = createNewSegmentFile()
        if (currentSegmentFile == null) {
            Log.e(TAG, "Failed to create new segment file.")
            _recordingState.value = RecordingState.ERROR
            return null
        }

        try {
            Log.d(TAG, "Initializing MediaRecorder for: ${currentSegmentFile!!.absolutePath}")
            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(applicationContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // O THREE_GPP
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)    // O AMR_NB
                setAudioSamplingRate(44100)                        // Standard CD quality
                setAudioEncodingBitRate(96000)                     // Bitrate per AAC
                setOutputFile(currentSegmentFile!!.absolutePath)

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaRecorder error: what=$what, extra=$extra. File: ${currentSegmentFile?.name}")
                    _recordingState.value = RecordingState.ERROR
                    // Potrebbe essere necessario fermare e rilasciare qui
                    stopAndReleaseMediaRecorder(isError = true) // Tentativo di pulizia
                }
                setOnInfoListener { _, what, extra ->
                    Log.i(TAG, "MediaRecorder info: what=$what, extra=$extra")
                    // Puoi gestire info come MEDIA_RECORDER_INFO_MAX_DURATION_REACHED se imposti una max duration
                }
            }

            Log.d(TAG, "Preparing MediaRecorder...")
            mediaRecorder?.prepare()
            Log.d(TAG, "Starting MediaRecorder...")
            mediaRecorder?.start()
            Log.i(TAG, "New segment recording started: ${currentSegmentFile!!.name}")
            return currentSegmentFile
        } catch (e: IOException) {
            Log.e(TAG, "IOException during MediaRecorder prepare/start for ${currentSegmentFile?.name}", e)
            _recordingState.value = RecordingState.ERROR
            stopAndReleaseMediaRecorder(isError = true)
            currentSegmentFile = null // Assicurati che il file corrente sia resettato in caso di errore
            return null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException during MediaRecorder prepare/start for ${currentSegmentFile?.name}", e)
            _recordingState.value = RecordingState.ERROR
            stopAndReleaseMediaRecorder(isError = true)
            currentSegmentFile = null
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Generic exception during MediaRecorder prepare/start for ${currentSegmentFile?.name}", e)
            _recordingState.value = RecordingState.ERROR
            stopAndReleaseMediaRecorder(isError = true)
            currentSegmentFile = null
            return null
        }
    }

    private fun finalizeCurrentSegment(isErrorOrCancellation: Boolean) {
        val fileToFinalize = currentSegmentFile
        if (fileToFinalize == null) {
            Log.d(TAG, "finalizeCurrentSegment called but no current segment file.")
            stopAndReleaseMediaRecorder(isErrorOrCancellation) // Tentativo di rilascio se recorder attivo
            return
        }

        Log.d(TAG, "Stopping and finalizing segment: ${fileToFinalize.name}")
        stopAndReleaseMediaRecorder(isErrorOrCancellation) // Prima ferma e rilascia il recorder

        if (fileToFinalize.exists() && fileToFinalize.length() > 0) {
            Log.i(TAG, "Segment finalized successfully: ${fileToFinalize.name}, Size: ${fileToFinalize.length()}")
            onSegmentReady(fileToFinalize)
        } else if (fileToFinalize.exists() && fileToFinalize.length() == 0L) {
            Log.w(TAG, "Segment ${fileToFinalize.name} is empty. Deleting.")
            fileToFinalize.delete()
        } else {
            Log.w(TAG, "Segment ${fileToFinalize.name} does not exist or was already handled.")
        }
        currentSegmentFile = null // Resetta il file corrente dopo la finalizzazione
    }

    private fun stopAndReleaseMediaRecorder(isError: Boolean) {
        mediaRecorder?.apply {
            try {
                if (_recordingState.value == RecordingState.RECORDING || isError) { // Solo se stava registrando o c'è un errore esplicito
                    Log.d(TAG, "Attempting to stop MediaRecorder. Current state: ${_recordingState.value}, isError: $isError")
                    stop()
                    Log.d(TAG, "MediaRecorder stopped for ${currentSegmentFile?.name ?: "unknown file"}")
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "IllegalStateException on MediaRecorder.stop() for ${currentSegmentFile?.name ?: "unknown file"}. Might have been already stopped or not started.", e)
            } catch (e: RuntimeException) { // MediaRecorder.stop() può lanciare RuntimeException
                Log.e(TAG, "RuntimeException on MediaRecorder.stop() for ${currentSegmentFile?.name ?: "unknown file"}", e)
            } finally {
                try {
                    reset()
                    release()
                    Log.d(TAG, "MediaRecorder reset and released.")
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during MediaRecorder reset/release", e)
                }
            }
        }
        mediaRecorder = null
    }

    fun startRecordingCycle() {
        if (_recordingState.value == RecordingState.RECORDING) {
            Log.w(TAG, "Recording cycle already in progress.")
            return
        }
        Log.i(TAG, "Starting recording cycle...")
        _recordingState.value = RecordingState.INITIALIZING // Stato intermedio
        // Valuta se è necessario un piccolo delay qui se INITIALIZING deve essere osservabile per più tempo
        _recordingState.value = RecordingState.RECORDING

        recordingJob = serviceScope.launch {
            Log.d(TAG, "Recording coroutine started. isActive: $isActive, state: ${_recordingState.value}")
            try {
                // Loop principale della registrazione: continua finché la coroutine è attiva
                // e lo stato desiderato è RECORDING.
                while (isActive && _recordingState.value == RecordingState.RECORDING) {
                    val segmentFile = startNewSegment()
                    if (segmentFile == null || !isActive) {
                        // Se startNewSegment fallisce o la coroutine è stata cancellata durante startNewSegment,
                        // lo stato dovrebbe già essere ERROR o la coroutine si interromperà.
                        Log.w(TAG, "Failed to start new segment or coroutine cancelled. Exiting recording loop. isActive: $isActive, state: ${_recordingState.value}")
                        if (isActive && _recordingState.value == RecordingState.RECORDING) {
                            // Se siamo ancora attivi e lo stato è RECORDING, ma startNewSegment ha fallito, impostiamo ERROR.
                            _recordingState.value = RecordingState.ERROR
                        }
                        break // Esce dal while loop
                    }

                    Log.d(TAG, "Segment ${segmentFile.name} started. Waiting for ${SEGMENT_DURATION_MS / 1000}s... isActive: $isActive")
                    delay(SEGMENT_DURATION_MS)

                    if (!isActive) { // Controlla di nuovo dopo il delay, prima di finalizzare
                        Log.i(TAG, "Coroutine cancelled during segment delay. Segment: ${segmentFile.name}")
                        break // Esce dal while loop, il blocco finally gestirà la finalizzazione
                    }

                    // Se siamo ancora qui e attivi, il segmento è completato normalmente.
                    if (_recordingState.value == RecordingState.RECORDING) { // Finalizza solo se siamo ancora in stato RECORDING
                        finalizeCurrentSegment(false) // false = non è un errore/cancellazione
                    } else {
                        Log.w(TAG, "State changed from RECORDING during segment delay. Not finalizing normally. State: ${_recordingState.value}. CurrentFile: ${currentSegmentFile?.name}")
                        // Se lo stato non è più RECORDING (es. stoppato dall'utente), il finally si occuperà della pulizia.
                        break
                    }
                }
            } catch (e: CancellationException) {
                // Questa è attesa quando la coroutine viene cancellata esternamente (es. da stopRecordingCycle).
                // Lo stato dovrebbe essere gestito dal chiamante (es. stopRecordingCycle imposta IDLE).
                Log.i(TAG, "Recording coroutine was cancelled (expected). Current state: ${_recordingState.value}")
                // Non impostare ERROR qui. La finalizzazione del segmento avverrà nel blocco finally.
            } catch (e: Exception) {
                // Per tutte le altre eccezioni non gestite nel loop.
                Log.e(TAG, "Unhandled exception in recording cycle", e)
                if (isActive) { // Imposta ERROR solo se la coroutine non è stata già cancellata
                    _recordingState.value = RecordingState.ERROR
                }
            } finally {
                // Questo blocco viene eseguito sempre, sia per completamento normale, cancellazione o eccezione.
                Log.d(TAG, "Recording coroutine 'finally' block. Current state: ${_recordingState.value}, isActive: $isActive, currentSegmentFile: ${currentSegmentFile?.name}")

                // Se c'è un segmento corrente quando la coroutine termina (per qualsiasi motivo),
                // deve essere finalizzato.
                if (currentSegmentFile != null) {
                    val wasRecording = _recordingState.value == RecordingState.RECORDING || _recordingState.value == RecordingState.INITIALIZING
                    Log.w(TAG, "Finalizing segment '${currentSegmentFile?.name}' in 'finally' block. Initial state for finalize: ${if(wasRecording && !isActive) "Cancelled" else "Normal/Error"}")
                    // Passa true se la coroutine è stata cancellata mentre era in stato di registrazione, o se lo stato è ERROR
                    val isFinalizingDueToErrorOrCancellation = !isActive || _recordingState.value == RecordingState.ERROR
                    finalizeCurrentSegment(isFinalizingDueToErrorOrCancellation)
                }

                // Assicurati che MediaRecorder sia rilasciato in ogni caso.
                // finalizeCurrentSegment dovrebbe già chiamare stopAndReleaseMediaRecorder.
                // Questo è un ulteriore controllo di sicurezza.
                if (mediaRecorder != null) {
                    Log.w(TAG, "MediaRecorder was not null in final finally block. Attempting release.")
                    stopAndReleaseMediaRecorder(true) // Consideralo un errore/pulizia forzata
                }

                // Se la coroutine è terminata ma lo stato è ancora RECORDING (improbabile con la logica attuale del loop),
                // allora qualcosa è andato storto, e dovremmo passare a IDLE o ERROR.
                // Generalmente, lo stato dovrebbe già essere IDLE (se fermato intenzionalmente) o ERROR (se c'è stata un'eccezione).
                if (_recordingState.value == RecordingState.RECORDING) {
                    Log.w(TAG, "Recording coroutine ended but state was still RECORDING. Setting to IDLE as fallback.")
                    _recordingState.value = RecordingState.IDLE
                }
                Log.d(TAG, "Recording coroutine finished execution. Final state: ${_recordingState.value}")
            }
        }
    }


    fun stopRecordingCycle() {
        Log.i(TAG, "Stop recording cycle requested. Current state: ${_recordingState.value}")

        // Se non sta registrando o inizializzando, non fare nulla.
        if (_recordingState.value != RecordingState.RECORDING && _recordingState.value != RecordingState.INITIALIZING) {
            Log.d(TAG, "Recording not active or already stopping. No action taken.")
            // Assicurati che il job sia nullo se non siamo in registrazione, per pulizia.
            if (recordingJob != null && !recordingJob!!.isActive) recordingJob = null
            return
        }

        // Imposta lo stato a IDLE. Questo è il nostro stato desiderato dopo lo stop.
        // La coroutine di registrazione, quando cancellata, dovrebbe rispettare questo.
        _recordingState.value = RecordingState.IDLE

        // Cancella il job di registrazione esistente e attendi il suo completamento.
        // Eseguilo in un nuovo scope per non bloccare il chiamante di stopRecordingCycle
        // e per gestire la cancellazione del job in modo pulito.
        val jobToCancel = recordingJob
        recordingJob = null // Rimuovi il riferimento al vecchio job

        if (jobToCancel != null && jobToCancel.isActive) {
            CoroutineScope(Dispatchers.IO).launch { // Usa un nuovo scope temporaneo per la cancellazione
                Log.d(TAG, "Attempting to cancel and join recordingJob...")
                try {
                    jobToCancel.cancel(CancellationException("Recording stopped by user")) // Passa una causa
                    jobToCancel.join() // Attendi che la coroutine termini (il suo blocco finally)
                    Log.i(TAG, "Recording job successfully cancelled and joined.")
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during recordingJob cancelAndJoin", e)
                    // In caso di errore qui, il MediaRecorder potrebbe non essere rilasciato correttamente dal job.
                    // Tentativo di pulizia manuale come ultima risorsa.
                    if (mediaRecorder != null) {
                        Log.w(TAG, "Forcing media recorder release after cancel/join exception.")
                        stopAndReleaseMediaRecorder(true)
                    }
                }
            }
        } else {
            Log.d(TAG, "No active recording job to cancel or job already completed.")
            // Se non c'era un job attivo ma currentSegmentFile esiste, potrebbe essere uno stato inconsistente
            if (currentSegmentFile != null) {
                Log.w(TAG, "Active segment file exists but no active recording job. Attempting cleanup.")
                finalizeCurrentSegment(true) // Consideralo come un errore/cancellazione
            }
            if (mediaRecorder != null) {
                Log.w(TAG, "MediaRecorder not null but no active job. Attempting release.")
                stopAndReleaseMediaRecorder(true)
            }
        }
    }

    // Funzione di pulizia da chiamare quando il servizio/componente che usa questo manager viene distrutto
    fun cleanup() {
        Log.i(TAG, "Cleanup called for AudioRecorderManager.")
        stopRecordingCycle() // Assicura che la registrazione sia fermata
        // Cancella lo scope del servizio se non è stato fornito esternamente
        // Se externalScope è null, allora serviceScope è stato creato internamente e può essere cancellato.
        // Altrimenti, la gestione del ciclo di vita di externalScope è responsabilità del chiamante.
        if (externalScope == null) {
            Log.d(TAG, "Cancelling internal serviceScope.")
            (serviceScope.coroutineContext[Job] as? SupervisorJob)?.cancel()
        }
    }
}
