package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val userRepository = UserRepository(AppDatabase.getDatabase(appContext).userDao())
    private val audioSegmentDao = AppDatabase.getDatabase(appContext).audioSegmentDao()

    companion object {
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_SEGMENT_ID = "key_segment_id"
        const val KEY_IS_VOICEPRINT = "key_is_voiceprint"
        const val TAG = "UploadWorker"
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(ProcessingWorker.KEY_ENCRYPTED_AUDIO_PATH)
            ?: inputData.getString(KEY_FILE_PATH)
            ?: run {
                Log.e(TAG, "Input non valido: il percorso del file è nullo.")
                return Result.failure()
            }

        val isVoiceprint = inputData.getBoolean(KEY_IS_VOICEPRINT, false)
        val segmentId = if (isVoiceprint) {
            inputData.getLong(KEY_SEGMENT_ID, -1L)
        } else {
            inputData.getLong(ProcessingWorker.KEY_NEW_SEGMENT_ID, -1L)
        }

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File da caricare non trovato: $filePath")
            if (segmentId != -1L) {
                audioSegmentDao.updateUploadStatus(segmentId, true)
            }
            return Result.success()
        }

        Log.i(TAG, "Tentativo di upload per: ${file.name} (ID: $segmentId, isVoiceprint: $isVoiceprint)")

        val user = userRepository.user.first()
        if (user == null || user.serverUrl.isBlank()) {
            Log.e(TAG, "Dati utente o URL server non configurati. Riprovo.")
            return Result.retry()
        }
        val apiService = createApiService(user.serverUrl)

        return try {
            val response = if (isVoiceprint) {
                uploadOnboardingRequest(apiService, file, user)
            } else {
                uploadAudioSegmentRequest(apiService, file)
            }

            if (response.isSuccessful) {
                Log.i(TAG, "Upload completato con successo per: ${file.name}")
                if (segmentId != -1L) {
                    audioSegmentDao.updateUploadStatus(segmentId, true)
                    Log.d(TAG, "Stato del segmento ID $segmentId aggiornato a 'caricato' nel DB.")
                }

                if (!isVoiceprint) {
                    file.delete()
                    Log.d(TAG, "File di segmento ${file.name} eliminato.")
                } else {
                    Log.i(TAG, "Il file del voiceprint ${file.name} è stato conservato sul dispositivo.")
                }

                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: "N/A"
            Log.e(TAG, "Errore HTTP ${e.code()} durante l'upload di ${file.name}. Body: $errorBody. Riprovo.", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione generica durante l'upload di ${file.name}. Riprovo.", e)
            Result.retry()
        }
    } // <-- FINE DI doWork()

    private fun createApiService(baseUrl: String): ApiService {
        val finalBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(finalBaseUrl)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private suspend fun uploadOnboardingRequest(api: ApiService, file: File, user: User): Response<Void> {
        val firstNameBody = user.firstName.toRequestBody("text/plain".toMediaTypeOrNull())
        val lastNameBody = user.lastName.toRequestBody("text/plain".toMediaTypeOrNull())
        val aliasBody = user.alias.toRequestBody("text/plain".toMediaTypeOrNull())
        val requestFileBody = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("voiceprintFile", file.name, requestFileBody)

        Log.d(TAG, "Invio dati di onboarding: nome=${user.firstName}, file=${file.name}")
        return api.uploadOnboardingData(firstNameBody, lastNameBody, aliasBody, filePart)
    }

    private suspend fun uploadAudioSegmentRequest(api: ApiService, file: File): Response<Void> {
        val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

        Log.d(TAG, "Invio segmento audio: ${file.name}")
        return api.uploadAudioSegment(filePart)
    }
}