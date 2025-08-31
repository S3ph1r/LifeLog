package com.example.lifelog

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProcessingWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val cryptoManager by lazy { CryptoManager() }
    private val appPreferences by lazy { AppPreferences.getInstance(appContext) }
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    // --- NUOVO: Aggiunto riferimento al DAO ---
    private val audioSegmentDao by lazy { AppDatabase.getDatabase(appContext).audioSegmentDao() }

    companion object {
        const val KEY_RAW_AUDIO_PATH = "key_raw_audio_path"

        // Chiavi per l'OUTPUT (dati da passare al worker successivo)
        const val KEY_ENCRYPTED_AUDIO_PATH = "key_encrypted_audio_path"
        const val KEY_NEW_SEGMENT_ID = "key_new_segment_id" // <-- NUOVA CHIAVE PER L'ID
        private const val TAG = "ProcessingWorker"
    }

    override suspend fun doWork(): Result {
        val rawAudioPath = inputData.getString(KEY_RAW_AUDIO_PATH)
        if (rawAudioPath.isNullOrEmpty()) {
            Log.e(TAG, "Input non valido: il percorso del file audio grezzo è nullo o vuoto.")
            return Result.failure()
        }

        val rawAudioFile = File(rawAudioPath)
        if (!rawAudioFile.exists()) {
            Log.e(TAG, "File audio grezzo non trovato al percorso: $rawAudioPath")
            return Result.failure()
        }

        Log.d(TAG, "Inizio processamento per il file: ${rawAudioFile.name}")

        try {
            // 1. Ottenere la posizione GPS
            val location = getCurrentLocation()
            val latitude = location?.latitude
            val longitude = location?.longitude

            if (latitude != null && longitude != null) {
                Log.i(TAG, "Posizione GPS ottenuta con successo: Lat: $latitude, Lon: $longitude")
            } else {
                Log.w(TAG, "Impossibile ottenere la posizione GPS.")
            }

            // 2. Costruire il nuovo nome del file criptato
            val timeStamp: String = dateFormat.format(Date())
            val encryptedFileName = if (latitude != null && longitude != null) {
                "segment_${timeStamp}_lat${"%.4f".format(Locale.US, latitude)}_lon${"%.4f".format(Locale.US, longitude)}.m4a.enc"
            } else {
                "segment_$timeStamp.m4a.enc"
            }
            val encryptedFile = File(appContext.filesDir, encryptedFileName)

            // 3. Criptare il file
            Log.d(TAG, "Inizio crittografia...")
            val password = appPreferences.password
            if (password.isBlank()) {
                Log.e(TAG, "Password non trovata! Impossibile criptare.")
                return Result.failure()
            }
            cryptoManager.encrypt(password, rawAudioFile.inputStream(), encryptedFile.outputStream())
            Log.i(TAG, "File criptato con successo in: ${encryptedFile.name}")

            // --- NUOVA LOGICA: SALVATAGGIO NEL DATABASE ---
            val newSegment = AudioSegment(
                filePath = encryptedFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                isUploaded = false,
                isVoiceprint = false // Questo worker gestisce solo segmenti normali
            )
            val newSegmentId = audioSegmentDao.insert(newSegment)
            Log.i(TAG, "Nuovo segmento salvato nel DB con ID: $newSegmentId")
            // --- FINE NUOVA LOGICA ---

            // 4. Preparare l'output per l'UploadWorker
            val outputData = workDataOf(
                KEY_ENCRYPTED_AUDIO_PATH to encryptedFile.absolutePath,
                KEY_NEW_SEGMENT_ID to newSegmentId // Passiamo anche il nuovo ID
            )

            // 5. Ritornare successo con i dati di output
            return Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Errore critico durante il processamento del segmento.", e)
            return Result.failure()
        } finally {
            // 6. Pulizia finale
            if (rawAudioFile.exists()) {
                rawAudioFile.delete()
                Log.d(TAG, "File audio grezzo ${rawAudioFile.name} eliminato.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permessi di localizzazione non concessi.")
            return null
        }
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione durante la richiesta della posizione. Tento con l'ultima posizione nota.", e)
            fusedLocationClient.lastLocation.await()
        }
    }
}