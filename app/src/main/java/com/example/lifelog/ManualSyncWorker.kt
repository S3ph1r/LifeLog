package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File

/**
 * Un worker dedicato alla sincronizzazione manuale, avviato dall'utente.
 * Le sue responsabilità sono:
 * 1. Pulire la coda di upload esistente per evitare duplicati.
 * 2. Scansionare il disco per tutti i file di segmento (.m4a.enc) non ancora caricati.
 * 3. Controllare il database per un eventuale voiceprint non caricato.
 * 4. Accodare un nuovo task di upload per ogni file trovato.
 */
class ManualSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val audioSegmentDao = AppDatabase.getDatabase(appContext).audioSegmentDao()

    companion object {
        const val TAG = "ManualSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Avvio del processo di sincronizzazione manuale...")

        try {
            // --- FASE 1: PULIZIA DELLA CODA ESISTENTE ---
            // Cancella tutti i task di upload precedentemente in coda per evitare duplicati.
            // Usiamo il TAG dell'UploadWorker per identificarli.
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(UploadWorker.TAG)
            Log.d(TAG, "Coda di upload esistente pulita.")

            var segmentsEnqueued = 0
            var voiceprintEnqueued = 0

            // --- FASE 2: SCANSIONE DEI SEGMENTI AUDIO CRIPTATI SUL DISCO ---
            val filesDir = applicationContext.filesDir
            Log.d(TAG, "Scansione della directory ${filesDir.absolutePath} per file .m4a.enc...")

            filesDir.listFiles()?.forEach { file ->
                // Controlliamo solo i file che corrispondono al nostro formato di segmenti
                if (file.isFile && file.name.endsWith(".m4a.enc")) {
                    Log.d(TAG, "Trovato file di segmento: ${file.name}. Schedulazione upload...")
                    // Per ogni file .m4a.enc, scheduliamo un UploadWorker.
                    // L'ID del segmento non è fondamentale qui, perché l'upload si basa sul path.
                    // Passiamo -1 come ID placeholder.
                    UploadUtils.scheduleUpload(
                        context = applicationContext,
                        filePath = file.absolutePath,
                        segmentId = -1L, // Non necessario per i segmenti trovati su disco
                        isVoiceprint = false
                    )
                    segmentsEnqueued++
                }
            }
            Log.i(TAG, "Schedulati $segmentsEnqueued task di upload per i segmenti audio.")

            // --- FASE 3: CONTROLLO DEL VOICEPRINT NEL DATABASE ---
            Log.d(TAG, "Controllo del database per un voiceprint non caricato...")
            val unuploadedVoiceprint = audioSegmentDao.findUnuploadedVoiceprint() // Metodo da aggiungere al DAO

            if (unuploadedVoiceprint != null) {
                val voiceprintFile = File(unuploadedVoiceprint.filePath)
                if (voiceprintFile.exists()) {
                    Log.i(TAG, "Trovato voiceprint non caricato (ID: ${unuploadedVoiceprint.id}). Schedulazione upload...")
                    UploadUtils.scheduleUpload(
                        context = applicationContext,
                        filePath = unuploadedVoiceprint.filePath,
                        segmentId = unuploadedVoiceprint.id,
                        isVoiceprint = true
                    )
                    voiceprintEnqueued = 1
                } else {
                    Log.w(TAG, "Voiceprint trovato nel DB ma il file non esiste più al percorso: ${unuploadedVoiceprint.filePath}. Lo segno come caricato.")
                    // Se il file non esiste, puliamo il DB per non riprovare all'infinito
                    audioSegmentDao.updateUploadStatus(unuploadedVoiceprint.id, true)
                }
            } else {
                Log.d(TAG, "Nessun voiceprint non caricato trovato nel database.")
            }

            Log.i(TAG, "Sincronizzazione manuale completata. Task totali accodati: ${segmentsEnqueued + voiceprintEnqueued}")
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Errore critico durante la sincronizzazione manuale. Riprovo.", e)
            return Result.retry()
        }
    }
}