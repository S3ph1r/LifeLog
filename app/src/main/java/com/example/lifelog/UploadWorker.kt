package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val audioSegmentDao: AudioSegmentDao by lazy { AppDatabase.getDatabase(applicationContext).audioSegmentDao() }
    private val settingsManager: SettingsManager by lazy { SettingsManager.getInstance(applicationContext) }

    companion object {
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_SEGMENT_ID = "key_segment_id"
        const val KEY_TIMESTAMP = "key_timestamp"
        const val KEY_IS_VOICEPRINT = "key_is_voiceprint" // Nuova chiave
        private const val TAG = "UploadWorker"
    }

    data class UploadDescription(val timestamp: Long)

    override suspend fun doWork(): Result {
        // Riprova ogni 15 minuti in caso di fallimento di rete
        if (runAttemptCount > 10) { // Limite di tentativi per evitare cicli infiniti
            return Result.failure()
        }

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

        Log.d(TAG, "Tentativo di upload per il file: ${file.name} (Voiceprint: $isVoiceprint)")

        return try {
            val response = if (isVoiceprint) {
                // Esegui l'upload del voiceprint
                uploadVoiceprintRequest(file)
            } else {
                // Esegui l'upload di un segmento normale
                uploadAudioSegmentRequest(file, timestamp)
            }

            if (response.isSuccessful) {
                Log.d(TAG, "Upload completato con successo per: ${file.name}")
                val segment = audioSegmentDao.getSegmentById(segmentId)
                if (segment != null) {
                    segment.isUploaded = true
                    audioSegmentDao.update(segment)
                    file.delete()
                    Log.d(TAG, "File ${file.name} marcato come caricato e cancellato.")
                }
                Result.success()
            } else {
                Log.e(TAG, "Upload fallito con codice: ${response.code()}. Riprovo tra 15 minuti.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione di rete. Riprovo tra 15 minuti.", e)
            Result.retry()
        }
    }

    private suspend fun uploadVoiceprintRequest(file: File): retrofit2.Response<Void> {
        val firstNameBody = settingsManager.userFirstName.toRequestBody("text/plain".toMediaTypeOrNull())
        val lastNameBody = settingsManager.userLastName.toRequestBody("text/plain".toMediaTypeOrNull())
        val aliasBody = settingsManager.userAlias.toRequestBody("text/plain".toMediaTypeOrNull())
        val requestFileBody = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("voiceprintFile", file.name, requestFileBody)

        return RetrofitInstance.getInstance(applicationContext).api.uploadVoiceprint(
            firstNameBody, lastNameBody, aliasBody, filePart
        )
    }

    private suspend fun uploadAudioSegmentRequest(file: File, timestamp: Long): retrofit2.Response<Void> {
        val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

        val descriptionObject = UploadDescription(timestamp = timestamp)
        val descriptionJson = Gson().toJson(descriptionObject)
        val descriptionPart = descriptionJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        return RetrofitInstance.getInstance(applicationContext).api.uploadAudio(filePart, descriptionPart)
    }
}