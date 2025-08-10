// Percorso: app/src/main/java/com/example/lifelog/PendingEncryptionWorker.kt

package com.example.lifelog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.lifelog.data.SettingsManager
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileEntity
import com.example.lifelog.data.db.AudioFileStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Un Worker che si occupa di processare i file audio registrati
 * mentre il dispositivo era in modalità Direct Boot (bloccato).
 */
class PendingEncryptionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "PendingEncryptionWorker"
    }

    private val cryptoManager = CryptoManager()
    private val audioFileDao = AppDatabase.getInstance(appContext).audioFileDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Worker avviato per processare i file registrati offline.")

        val contextDE = applicationContext.createDeviceProtectedStorageContext()
        val rawRecordingsDir = File(contextDE.filesDir, "recordings_raw")

        if (!rawRecordingsDir.exists() || !rawRecordingsDir.isDirectory) {
            Log.d(TAG, "La directory dei file grezzi non esiste. Nessun lavoro da fare.")
            return@withContext Result.success()
        }

        val pendingFiles = rawRecordingsDir.listFiles { _, name -> name.endsWith(".m4a") }
        if (pendingFiles.isNullOrEmpty()) {
            Log.d(TAG, "Nessun file in sospeso trovato. Lavoro terminato.")
            // --- CORREZIONE QUI ---
            return@withContext Result.success()
        }

        Log.d(TAG, "Trovati ${pendingFiles.size} file in sospeso da criptare.")

        val password = SettingsManager.encryptionPassword
        if (password.isBlank()) {
            Log.e(TAG, "Password di crittografia non trovata. Impossibile processare i file. Riproverò più tardi.")
            // --- CORREZIONE QUI ---
            return@withContext Result.retry()
        }

        var allSucceeded = true
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
                    Log.d(TAG, "File ${rawFile.name} processato e registrato nel DB.")
                    rawFile.delete()
                } else {
                    Log.e(TAG, "Criptazione fallita per il file ${rawFile.name}")
                    allSucceeded = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante il processamento del file ${rawFile.name}", e)
                allSucceeded = false
            }
        }

        return@withContext if (allSucceeded) Result.success() else Result.retry()
    }
}