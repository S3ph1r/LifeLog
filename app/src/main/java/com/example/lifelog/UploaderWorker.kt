// Percorso: app/src/main/java/com/example/lifelog/UploaderWorker.kt

package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lifelog.data.SettingsManager // <-- MODIFICA: Import
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
        Log.d(TAG, "Worker avviato. Recupero file da caricare dal database.")

        val filesToUpload = audioFileDao.getFilesWithStatus(AudioFileStatus.PENDING_UPLOAD)

        if (filesToUpload.isEmpty()) {
            Log.d(TAG, "Nessun file da caricare. Lavoro terminato con successo.")
            return@withContext Result.success()
        }

        Log.d(TAG, "Trovati ${filesToUpload.size} file da caricare.")

        // --- MODIFICA CHIAVE ---
        // Leggiamo l'indirizzo del server dal nostro SettingsManager sicuro
        var serverAddress = SettingsManager.serverAddress

        if (serverAddress.isBlank()) {
            Log.e(TAG, "Indirizzo server non configurato nelle Impostazioni. Riproverò più tardi.")
            return@withContext Result.retry() // Riprova, l'utente potrebbe inserire l'indirizzo
        }
        // --- FINE MODIFICA ---

        // Normalizzazione dell'URL
        if (!serverAddress.startsWith("http")) serverAddress = "http://$serverAddress"
        if (!serverAddress.endsWith("/")) serverAddress += "/"

        val retrofitService = RetrofitClient.getClient(serverAddress)
        var allUploadsSucceeded = true

        for (entity in filesToUpload) {
            val file = File(entity.filePath)
            if (!file.exists()) {
                Log.w(TAG, "Il file ${entity.fileName} non esiste più sul disco. Rimuovo dal DB.")
                audioFileDao.deleteById(entity.id)
                continue
            }

            try {
                audioFileDao.update(entity.copy(status = AudioFileStatus.UPLOADING))

                val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", entity.fileName, requestFile)

                Log.d(TAG, "Tentativo di upload per: ${entity.fileName}")
                val response = retrofitService.uploadFile(body)

                if (response.isSuccessful) {
                    Log.d(TAG, "Upload riuscito per ${entity.fileName}. Aggiorno DB e elimino file.")
                    audioFileDao.update(entity.copy(status = AudioFileStatus.UPLOADED))
                    file.delete()
                } else {
                    Log.w(TAG, "Upload fallito per ${entity.fileName}. Codice: ${response.code()}. Tentativo #${entity.uploadAttempts + 1}")
                    allUploadsSucceeded = false
                    handleFailedUpload(entity)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione durante l'upload di ${entity.fileName}", e)
                allUploadsSucceeded = false
                handleFailedUpload(entity)
            }
        }

        return@withContext if (allUploadsSucceeded) Result.success() else Result.retry()
    }

    private suspend fun handleFailedUpload(entity: AudioFileEntity) {
        val nextAttempt = entity.uploadAttempts + 1
        if (nextAttempt >= MAX_RETRIES) {
            Log.e(TAG, "File ${entity.fileName} ha fallito troppe volte. Marco come PENDING per un tentativo futuro.")
            audioFileDao.update(entity.copy(status = AudioFileStatus.PENDING_UPLOAD, uploadAttempts = nextAttempt))
        } else {
            audioFileDao.update(entity.copy(status = AudioFileStatus.PENDING_UPLOAD, uploadAttempts = nextAttempt))
        }
    }
}