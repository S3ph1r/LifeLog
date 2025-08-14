package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val audioSegmentDao: AudioSegmentDao by lazy { AppDatabase.getDatabase(applicationContext).audioSegmentDao() }

    companion object {
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_SEGMENT_ID = "key_segment_id"
        const val KEY_TIMESTAMP = "key_timestamp"
        const val KEY_IS_VOICEPRINT = "key_is_voiceprint" // Nuova chiave
        private const val TAG = "UploadWorker"
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
        val segmentId = inputData.getLong(KEY_SEGMENT_ID, -1L)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, 0L)
        val isVoiceprint = inputData.getBoolean(KEY_IS_VOICEPRINT, false)

        if (filePath.isNullOrEmpty() || segmentId == -1L) {
            return Result.failure()
        }
        val file = File(filePath)
        if (!file.exists()) {
            // Se il file non esiste più, cancelliamo il record dal DB
            // audioSegmentDao.deleteById(segmentId) // (richiederebbe un nuovo metodo nel DAO)
            return Result.failure()
        }

        Log.d(TAG, "Processando il file: ${file.name} (Voiceprint: $isVoiceprint)")

        // Simula upload riuscito - aggiorna il database e cancella il file
        Log.d(TAG, "Upload completato con successo per: ${file.name}")
        val segment = audioSegmentDao.getSegmentById(segmentId)
        if (segment != null) {
            segment.isUploaded = true
            audioSegmentDao.update(segment)
            file.delete()
            Log.d(TAG, "File ${file.name} marcato come caricato e cancellato.")
        }
        return Result.success()
    }

}