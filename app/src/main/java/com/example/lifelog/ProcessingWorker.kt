package com.example.lifelog

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
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

/**
 * Un CoroutineWorker responsabile di:
 * 1. Ricevere il percorso di un file audio .m4a non criptato.
 * 2. Ottenere la posizione GPS attuale del dispositivo.
 * 3. Criptare il file .m4a, includendo le coordinate GPS nel nuovo nome del file.
 * 4. Passare il percorso del nuovo file criptato (.m4a.enc) come output per il worker successivo.
 */
class ProcessingWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val cryptoManager by lazy { CryptoManager() }
    private val appPreferences by lazy { AppPreferences.getInstance(appContext) }
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    companion object {
        // Chiave per ricevere il percorso del file audio grezzo in input
        const val KEY_RAW_AUDIO_PATH = "key_raw_audio_path"
        // Chiave per inviare il percorso del file criptato in output
        const val KEY_ENCRYPTED_AUDIO_PATH = "key_encrypted_audio_path"
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
            return Result.failure() // Il file non esiste, inutile continuare
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
                Log.w(TAG, "Impossibile ottenere la posizione GPS. Il nome del file non conterrà le coordinate.")
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

            // 4. Preparare l'output per il worker successivo
            val outputData = workDataOf(KEY_ENCRYPTED_AUDIO_PATH to encryptedFile.absolutePath)

            // 5. Ritornare successo con i dati di output
            return Result.success(outputData)

        } catch (e: Exception) {
            Log.e(TAG, "Errore critico durante il processamento del segmento.", e)
            return Result.failure()
        } finally {
            // 6. Pulizia finale: il file grezzo .m4a viene sempre cancellato
            if (rawAudioFile.exists()) {
                rawAudioFile.delete()
                Log.d(TAG, "File audio grezzo ${rawAudioFile.name} eliminato.")
            }
        }
    }

    /**
     * Ottiene l'ultima posizione conosciuta o richiede un aggiornamento fresco.
     * Richiede i permessi ACCESS_COARSE_LOCATION o ACCESS_FINE_LOCATION.
     */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        // Controlla se i permessi sono stati concessi
        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Permessi di localizzazione non concessi. Impossibile ottenere il GPS.")
            return null
        }

        // Prova a ottenere la posizione attuale con alta priorità
        return try {
            val locationRequest = Priority.PRIORITY_HIGH_ACCURACY
            val cancellationToken = CancellationTokenSource().token
            fusedLocationClient.getCurrentLocation(locationRequest, cancellationToken).await()
        } catch (e: Exception) {
            Log.e(TAG, "Eccezione durante la richiesta della posizione corrente. Tento con l'ultima posizione nota.", e)
            // Se fallisce, ripiega sull'ultima posizione nota (potrebbe essere null)
            fusedLocationClient.lastLocation.await()
        }
    }
}