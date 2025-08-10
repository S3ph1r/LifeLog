package com.example.lifelog

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
// HKDF non è nativo in Java/Android standard, quindi useremo una libreria o un'implementazione semplice.
// Per semplicità qui, useremo PBKDF2 che è molto simile e disponibile,
// ma assicuriamoci che il server Python sia modificato per usare PBKDF2.
// Visto che il server usa HKDF, la via più pulita è aggiungere una dipendenza.

// Per ora, ti do la versione CON PBKDF2 che è la più facile da implementare su Android
// senza nuove dipendenze, e poi ti dirò come modificare UNA RIGA in Python per allinearlo.

class CryptoManager {

    companion object {
        private const val TAG = "CryptoManager"
        // --- ALLINEATO CON AES/GCM ---
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding" // Standard per GCM
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"

        // GCM usa un nonce/IV di 12 bytes.
        private const val IV_SIZE = 12
        private const val SALT_SIZE = 16 // Un buon valore per il salt
        private const val ITERATION_COUNT = 65536
        private const val KEY_SIZE = 256
        private const val AUTH_TAG_LENGTH = 128 // in bits
    }

    /**
     * Cripta un file usando AES/GCM con una chiave derivata tramite PBKDF2.
     * La struttura del file sarà: [SALT (16 bytes)] + [IV/NONCE (12 bytes)] + [DATI CRIPTATI]
     */
    fun encryptFile(password: String, fileToEncrypt: File): File? {
        if (!fileToEncrypt.exists() || password.isBlank()) {
            Log.e(TAG, "File non trovato o password vuota.")
            return null
        }

        try {
            // 1. Genera un SALT casuale.
            val salt = ByteArray(SALT_SIZE)
            SecureRandom().nextBytes(salt)

            // 2. Deriva una chiave segreta dalla password e dal salt usando PBKDF2.
            val factory = javax.crypto.SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
            val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_SIZE)
            val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, ALGORITHM)

            // 3. Genera un IV/NONCE casuale.
            val iv = ByteArray(IV_SIZE)
            SecureRandom().nextBytes(iv)

            // 4. Inizializza il Cipher per la criptazione in modalità GCM
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(AUTH_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            val encryptedFile = File(fileToEncrypt.parent, "${fileToEncrypt.name}.enc")

            FileOutputStream(encryptedFile).use { fos ->
                // 5. Scrivi il SALT e l'IV all'inizio del file di output.
                fos.write(salt)
                fos.write(iv)

                // 6. Cripta il file originale.
                FileInputStream(fileToEncrypt).use { fis ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val output = cipher.update(buffer, 0, bytesRead)
                        if (output != null) {
                            fos.write(output)
                        }
                    }
                }
                // doFinal() in GCM mode aggiunge l'Authentication Tag alla fine
                val output = cipher.doFinal()
                if (output != null) {
                    fos.write(output)
                }
            }
            Log.d(TAG, "File criptato con successo (AES/GCM): ${encryptedFile.name}")
            return encryptedFile

        } catch (e: Exception) {
            Log.e(TAG, "Errore durante la criptazione (AES/GCM)", e)
            return null
        }
    }
}