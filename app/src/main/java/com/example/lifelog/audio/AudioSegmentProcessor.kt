// Percorso: app/src/main/java/com/example/lifelog/audio/AudioSegmentProcessor.kt

package com.example.lifelog.audio

import android.content.Context
import android.os.UserManager
import android.util.Log
import com.example.lifelog.CryptoManager
import com.example.lifelog.data.SettingsManager
import com.example.lifelog.data.db.AppDatabase
import com.example.lifelog.data.db.AudioFileEntity
import com.example.lifelog.data.db.AudioFileStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gestisce il post-processamento dei segmenti audio registrati.
 * Decide se criptare il file immediatamente (se l'utente è sbloccato)
 * o lasciarlo in attesa per una criptazione successiva (se il dispositivo è bloccato).
 * Inserisce i record nel database Room.
 */
class AudioSegmentProcessor(
    private val context: Context,
    private val scope: CoroutineScope // Lo scope di coroutine del servizio
) {
    private val TAG = "AudioSegmentProcessor"

    private val cryptoManager = CryptoManager()

    // Usiamo il lazy per inizializzare il DAO solo quando è effettivamente necessario.
    // L'istanza del database dipende dal contesto (Direct Boot o sbloccato).
    private val audioFileDao by lazy {
        // La scelta del contesto è fondamentale qui:
        // getApplicationContext() restituirebbe sempre il contesto CE, che è bloccato in Direct Boot.
        // Dobbiamo usare il contesto passato nel costruttore che viene creato correttamente.
        AppDatabase.getInstance(context).audioFileDao()
    }

    /**
     * Processa un file audio grezzo.
     * Se il dispositivo è sbloccato, cripta il file e lo aggiunge al DB per l'upload.
     * Se il dispositivo è bloccato, il file è già nello storage DE e non viene toccato.
     * Sarà un worker separato (PendingEncryptionWorker) a processarlo dopo lo sblocco.
     */
    fun processAudioSegment(rawFile: File) {
        // Verifichiamo lo stato di sblocco al momento del processamento.
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val isUserUnlocked = userManager.isUserUnlocked

        if (!isUserUnlocked) {
            Log.d(TAG, "Dispositivo bloccato. File grezzo ${rawFile.name} rimane in attesa di criptazione (storage DE).")
            // Non facciamo nulla, il file è già al sicuro nella cartella recordings_raw
            // che è nello storage DE. PendingEncryptionWorker se ne occuperà.
            return
        }

        // Se il dispositivo è sbloccato, procediamo con la criptazione e l'inserimento nel DB.
        scope.launch(Dispatchers.IO) {
            val password = SettingsManager.encryptionPassword

            if (password.isBlank()) {
                Log.e(TAG, "Password di crittografia non impostata. File eliminato: ${rawFile.name}")
                rawFile.delete() // Elimina il file grezzo se non possiamo criptarlo.
                return@launch
            }

            try {
                // Cripta il file. Il CryptoManager si occuperà di salvare il file .enc.
                val encryptedFile = cryptoManager.encryptFile(password, rawFile)

                if (encryptedFile != null && encryptedFile.exists()) {
                    // Crea l'entità per il database e la imposta come PENDING_UPLOAD.
                    val entity = AudioFileEntity(
                        fileName = encryptedFile.name,
                        filePath = encryptedFile.absolutePath,
                        status = AudioFileStatus.PENDING_UPLOAD,
                        sizeInBytes = encryptedFile.length()
                    )
                    audioFileDao.insert(entity)
                    Log.d(TAG, "Nuovo file criptato e registrato nel DB: ${entity.fileName}")
                } else {
                    Log.e(TAG, "Criptazione fallita per il file ${rawFile.name}. Eliminato.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante la criptazione o inserimento DB per ${rawFile.name}. Eliminato.", e)
            } finally {
                rawFile.delete() // Assicurati che il file grezzo .m4a sia sempre eliminato.
            }
        }
    }
}