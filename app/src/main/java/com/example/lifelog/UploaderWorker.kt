package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lifelog.data.ConfigManager
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileEntity
import com.example.lifelog.data.db.AudioFileStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class UploaderWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "UploaderWorker"
        const val MAX_RETRIES = 3
    }

    private val audioFileDao = AppDatabase.getInstance(appContext).audioFileDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val filesToUpload = audioFileDao.getFilesWithStatus(AudioFileStatus.PENDING_UPLOAD)
        if (filesToUpload.isEmpty()) {
            return@withContext Result.success()
        }

        var serverAddress = ConfigManager.getConfig().serverAddress
        if (serverAddress.isBlank()) {
            Log.e(TAG, "Indirizzo server non configurato.")
            return@withContext Result.retry()
        }

        if (!serverAddress.startsWith("http")) serverAddress = "http://$serverAddress"
        if (!serverAddress.endsWith("/")) serverAddress += "/"
        val retrofitService = RetrofitClient.getClient(serverAddress)

        for (entity in filesToUpload) {
            val file = File(entity.filePath)
            if (!file.exists()) {
                audioFileDao.deleteById(entity.id)
                continue
            }

            try {
                audioFileDao.update(entity.copy(status = AudioFileStatus.UPLOADING))
                val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", entity.fileName, requestFile)
                val response = retrofitService.uploadFile(body)

                if (response.isSuccessful) {
                    audioFileDao.update(entity.copy(status = AudioFileStatus.UPLOADED))
                    file.delete()
                } else {
                    handleFailedUpload(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione durante l'upload di ${entity.fileName}", e)
                handleFailedUpload(entity)
            }
        }
        // Il worker dovrebbe sempre ritentare se anche un solo file fallisce
        return@withContext Result.retry()
    }

    private suspend fun handleFailedUpload(entity: AudioFileEntity) {
        val nextAttempt = entity.uploadAttempts + 1
        val newStatus = if (nextAttempt >= MAX_RETRIES) AudioFileStatus.UPLOAD_FAILED_RETRYABLE else AudioFileStatus.PENDING_UPLOAD
        audioFileDao.update(entity.copy(status = newStatus, uploadAttempts = nextAttempt))
    }
}