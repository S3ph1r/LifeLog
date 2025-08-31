package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Classe di utilità per schedulare i task di upload tramite WorkManager.
 * In questa nuova architettura, viene usata SOLO per l'upload del voiceprint dall'onboarding.
 */
object UploadUtils {

    private const val TAG = "UploadUtils"

    fun scheduleUpload(context: Context, filePath: String, segmentId: Long, isVoiceprint: Boolean) {
        Log.d(TAG, "Schedulazione upload per: $filePath (isVoiceprint: $isVoiceprint)")

        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_FILE_PATH, filePath)
            .putLong(UploadWorker.KEY_SEGMENT_ID, segmentId)
            // Il timestamp non è più rilevante qui, lo gestirà il backend
            .putBoolean(UploadWorker.KEY_IS_VOICEPRINT, isVoiceprint)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            // Il ritardo di 1 minuto è stato rimosso per avviare l'upload il prima possibile.
            .build()

        WorkManager.getInstance(context).enqueue(uploadWorkRequest)

        Log.i(TAG, "Task di upload per $filePath accodato con successo.")
    }
}