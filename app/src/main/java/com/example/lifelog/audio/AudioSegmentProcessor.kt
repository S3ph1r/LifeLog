package com.example.lifelog.audio

import android.content.Context
import android.os.UserManager
import android.util.Log
import com.example.lifelog.CryptoManager
import com.example.lifelog.data.ConfigManager
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileEntity
import com.example.lifelog.data.db.AudioFileStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class AudioSegmentProcessor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val TAG = "AudioSegmentProcessor"
    private val cryptoManager = CryptoManager()
    private val audioFileDao by lazy { AppDatabase.getInstance(context).audioFileDao() }

    fun processAudioSegment(rawFile: File) {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        if (!userManager.isUserUnlocked) {
            Log.d(TAG, "Dispositivo bloccato. ${rawFile.name} rimane in attesa.")
            return
        }

        scope.launch(Dispatchers.IO) {
            val password = ConfigManager.getConfig().encryptionPassword
            if (password.isBlank()) {
                Log.e(TAG, "Password non impostata. File ${rawFile.name} eliminato.")
                rawFile.delete()
                return@launch
            }

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
                    Log.d(TAG, "Nuovo file criptato e registrato: ${entity.fileName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante il processamento di ${rawFile.name}", e)
            } finally {
                rawFile.delete()
            }
        }
    }
}