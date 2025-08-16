package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
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
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // --- MODIFICHE CHIAVE ---
    private val audioSegmentDao = AppDatabase.getDatabase(appContext).audioSegmentDao()
    private val userRepository = UserRepository(AppDatabase.getDatabase(appContext).userDao())

    companion object {
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_SEGMENT_ID = "key_segment_id"
        const val KEY_TIMESTAMP = "key_timestamp"
        const val KEY_IS_VOICEPRINT = "key_is_voiceprint"
        private const val TAG = "UploadWorker"
    }

    data class UploadDescription(val timestamp: Long)

    override suspend fun doWork(): Result {
        // Leggi i dati utente (che contengono l'URL) da Room
        val user = userRepository.user.first()
        if (user == null || user.serverUrl.isBlank()) {
            Log.e(TAG, "Dati utente o URL del server non configurati. Riprovo più tardi.")
            return Result.retry()
        }
        val serverUrl = user.serverUrl

        val apiService = createApiService(serverUrl)

        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val segmentId = inputData.getLong(KEY_SEGMENT_ID, -1L)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, 0L)
        val isVoiceprint = inputData.getBoolean(KEY_IS_VOICEPRINT, false)

        if (segmentId == -1L) return Result.failure()

        val file = File(filePath)
        if (!file.exists()) {
            audioSegmentDao.updateUploadStatus(segmentId, true)
            Log.w(TAG, "File non trovato, rimosso dalla coda: $filePath")
            return Result.failure()
        }

        Log.d(TAG, "Tentativo di upload per: ${file.name} (Voiceprint: $isVoiceprint)")

        return try {
            val response = if (isVoiceprint) {
                // Passiamo l'oggetto user per l'upload del voiceprint
                uploadVoiceprintRequest(apiService, file, user)
            } else {
                uploadAudioSegmentRequest(apiService, file, timestamp)
            }

            if (response.isSuccessful) {
                Log.d(TAG, "Upload completato per: ${file.name}")
                audioSegmentDao.updateUploadStatus(segmentId, true)
                file.delete()
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
        // ... (resto della funzione invariato)
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

    private suspend fun uploadVoiceprintRequest(api: ApiService, file: File, user: User): retrofit2.Response<Void> {
        // --- MODIFICA CHIAVE ---
        // I dati ora provengono dall'oggetto User passato come parametro
        val firstNameBody = user.firstName.toRequestBody("text/plain".toMediaTypeOrNull())
        val lastNameBody = user.lastName.toRequestBody("text/plain".toMediaTypeOrNull())
        val aliasBody = user.alias.toRequestBody("text/plain".toMediaTypeOrNull())

        val requestFileBody = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("voiceprint", file.name, requestFileBody)

        Log.d(TAG, "Dati per l'upload: nome=${user.firstName}, cognome=${user.lastName}, alias=${user.alias}")

        // Assicurati che il tuo ApiService sia stato aggiornato per questo
        return api.uploadVoiceprint(firstNameBody, lastNameBody, aliasBody, filePart)
    }

    private suspend fun uploadAudioSegmentRequest(api: ApiService, file: File, timestamp: Long): retrofit2.Response<Void> {
        // ... (questa funzione rimane invariata)
        val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

        val descriptionObject = UploadDescription(timestamp = timestamp)
        val descriptionJson = Gson().toJson(descriptionObject)
        val descriptionPart = descriptionJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        return api.uploadAudio(filePart, descriptionPart)
    }
}