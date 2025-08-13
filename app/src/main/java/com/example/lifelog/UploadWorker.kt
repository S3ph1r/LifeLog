package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val audioSegmentDao: AudioSegmentDao by lazy {
        AppDatabase.getDatabase(applicationContext).audioSegmentDao()
    }

    companion object {
        const val KEY_FILE_PATH = "key_file_path"
        const val KEY_SEGMENT_ID = "key_segment_id"
        const val KEY_TIMESTAMP = "key_timestamp"
        private const val TAG = "UploadWorker"
    }

    data class UploadDescription(val timestamp: Long)

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
        val segmentId = inputData.getLong(KEY_SEGMENT_ID, -1L)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, 0L)

        if (filePath.isNullOrEmpty() || segmentId == -1L || timestamp == 0L) {
            Log.e(TAG, "Dati di input non validi.")
            return Result.failure()
        }

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File da caricare non trovato: $filePath")
            return Result.failure()
        }

        Log.d(TAG, "Tentativo di upload del file CRIPTATO: $filePath")

        return try {
            // Inviamo il file direttamente, senza decriptarlo.
            val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)

            val descriptionObject = UploadDescription(timestamp = timestamp)
            val descriptionJson = Gson().toJson(descriptionObject)
            val descriptionPart = descriptionJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            // --- MODIFICA CHIAVE: Usiamo getInstance(context) per ottenere l'api service ---
            val response = RetrofitInstance.getInstance(applicationContext).api.uploadAudio(filePart, descriptionPart)

            if (response.isSuccessful) {
                Log.d(TAG, "Upload del file criptato completato con successo: ${file.name}")

                val segment = audioSegmentDao.getSegmentById(segmentId)
                if (segment != null) {
                    segment.isUploaded = true
                    audioSegmentDao.update(segment)
                    Log.d(TAG, "Segmento $segmentId marcato come caricato nel DB.")

                    file.delete()
                    Log.d(TAG, "File fisico criptato cancellato: ${file.name}")
                }

                Result.success()
            } else {
                Log.e(TAG, "Upload fallito con codice di errore: ${response.code()} per il file: ${file.name}")
                Result.retry()
            }
        } catch (e: IllegalArgumentException) {
            // Questa eccezione viene lanciata da Retrofit se l'URL di base non è valido.
            Log.e(TAG, "URL del server non valido o non configurato. Controlla le impostazioni.", e)
            Result.failure() // Fallimento permanente, inutile riprovare se l'URL è sbagliato.
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione durante l'upload", e)
            Result.retry() // Altri errori (es. rete) possono essere temporanei.
        }
    }
}