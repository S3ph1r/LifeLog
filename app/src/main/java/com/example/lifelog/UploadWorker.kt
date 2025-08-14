package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val audioSegmentDao: AudioSegmentDao by lazy { AppDatabase.getDatabase(appContext).audioSegmentDao() }
    private val settingsManager: SettingsManager by lazy { SettingsManager.getInstance(appContext) }

    companion object {
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_SEGMENT_ID = "key_segment_id"
        const val KEY_TIMESTAMP = "key_timestamp"
        const val KEY_IS_VOICEPRINT = "key_is_voiceprint"
        private const val TAG = "UploadWorker"
    }

    data class UploadDescription(val timestamp: Long)

    override suspend fun doWork(): Result {
        if (runAttemptCount > 10) {
            return Result.failure()
        }

        // Leggi l'URL aggiornato dal SettingsManager
        val serverUrl = settingsManager.serverUrl.value
        if (serverUrl.isBlank()) {
            Log.e(TAG, "URL del server non configurato. Riprovo più tardi.")
            return Result.retry()
        }

        // Crea un'istanza di ApiService al momento, con l'URL corretto
        val apiService = createApiService(serverUrl)

        val filePath = inputData.getString(KEY_FILE_PATH)
        val segmentId = inputData.getLong(KEY_SEGMENT_ID, -1L)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, 0L)
        val isVoiceprint = inputData.getBoolean(KEY_IS_VOICEPRINT, false)

        if (filePath.isNullOrEmpty() || segmentId == -1L) {
            return Result.failure()
        }
        val file = File(filePath)
        if (!file.exists()) {
            audioSegmentDao.updateUploadStatus(segmentId, true)
            Log.w(TAG, "File non trovato, record rimosso dalla coda: $filePath")
            return Result.failure()
        }

        Log.d(TAG, "Tentativo di upload per il file: ${file.name} (Voiceprint: $isVoiceprint)")

        return try {
            val response = if (isVoiceprint) {
                uploadVoiceprintRequest(apiService, file)
            } else {
                uploadAudioSegmentRequest(apiService, file, timestamp)
            }

            if (response.isSuccessful) {
                Log.d(TAG, "Upload completato per: ${file.name}")
                audioSegmentDao.updateUploadStatus(segmentId, true)
                file.delete()
                Log.d(TAG, "File ${file.name} marcato come caricato e cancellato.")
                Result.success()
            } else {
                Log.e(TAG, "Upload fallito con codice: ${response.code()}. Riprovo.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione durante l'upload. Riprovo.", e)
            Result.retry()
        }
    }

    private fun createApiService(baseUrl: String): ApiService {
        val finalBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(finalBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }

    private suspend fun uploadVoiceprintRequest(api: ApiService, file: File): retrofit2.Response<Void> {
        // --- INIZIO MODIFICA ---
        // Leggiamo il valore corrente (.value) dagli StateFlow
        val firstNameBody = settingsManager.userFirstName.value.toRequestBody("text/plain".toMediaTypeOrNull())
        val lastNameBody = settingsManager.userLastName.value.toRequestBody("text/plain".toMediaTypeOrNull())
        val aliasBody = settingsManager.userAlias.value.toRequestBody("text/plain".toMediaTypeOrNull())
        // --- FINE MODIFICA ---

        val requestFileBody = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("voiceprintFile", file.name, requestFileBody)

        return api.uploadVoiceprint(firstNameBody, lastNameBody, aliasBody, filePart)
    }

    private suspend fun uploadAudioSegmentRequest(api: ApiService, file: File, timestamp: Long): retrofit2.Response<Void> {
        val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

        val descriptionObject = UploadDescription(timestamp = timestamp)
        val descriptionJson = Gson().toJson(descriptionObject)
        val descriptionPart = descriptionJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        return api.uploadAudio(filePart, descriptionPart)
    }
}