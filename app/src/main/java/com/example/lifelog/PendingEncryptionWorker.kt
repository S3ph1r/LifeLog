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
import java.io.File

class PendingEncryptionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val cryptoManager = CryptoManager()
    private val audioFileDao = AppDatabase.getInstance(appContext).audioFileDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val contextDE = applicationContext.createDeviceProtectedStorageContext()
        val rawRecordingsDir = File(contextDE.filesDir, "recordings_raw")

        if (!rawRecordingsDir.exists()) return@withContext Result.success()

        val pendingFiles = rawRecordingsDir.listFiles { _, name -> name.endsWith(".m4a") }
        if (pendingFiles.isNullOrEmpty()) return@withContext Result.success()

        val password = ConfigManager.getConfig().encryptionPassword
        if (password.isBlank()) {
            Log.e("PendingEncryptionWorker", "Password non trovata.")
            return@withContext Result.retry()
        }

        for (rawFile in pendingFiles) {
            try {
                val encryptedFile = cryptoManager.encryptFile(password, rawFile)
                if (encryptedFile != null && encryptedFile.exists()) {
                    val entity = AudioFileEntity(
                        fileName = encryptedFile.name,
                        filePath = encryptedFile.absolutePath,
                        status = AudioFileStatus.PENDING_UPLOAD,
                        sizeInBytes = encryptedFile.length()
                    )
                    audioFileDao.insert(entity)
                    rawFile.delete()
                }
            } catch (e: Exception) {
                Log.e("PendingEncryptionWorker", "Errore processando ${rawFile.name}", e)
            }
        }
        return@withContext Result.success()
    }
}