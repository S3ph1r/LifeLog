package com.example.lifelog

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UploadUtils {

    fun scheduleUpload(context: Context, filePath: String, segmentId: Long, timestamp: Long, isVoiceprint: Boolean) {
        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_FILE_PATH, filePath)
            .putLong(UploadWorker.KEY_SEGMENT_ID, segmentId)
            .putLong(UploadWorker.KEY_TIMESTAMP, timestamp)
            .putBoolean(UploadWorker.KEY_IS_VOICEPRINT, isVoiceprint)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            // --- MODIFICA CHIAVE QUI ---
            // Aggiungiamo un ritardo iniziale di 1 minuto.
            // Questo assicura che, quando il worker partirà, tutte le operazioni di salvataggio
            // avviate dall'onboarding saranno state completate da tempo.
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueue(uploadWorkRequest)
    }
}