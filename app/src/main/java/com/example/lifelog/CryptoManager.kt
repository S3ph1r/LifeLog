package com.example.lifelog

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gestisce tutte le operazioni di criptazione e decriptazione
 * basate su una password fornita dall'utente.
 */
class CryptoManager {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val KEY_SIZE_BITS = 256
        private const val SALT_SIZE_BYTES = 16
        private const val IV_SIZE_BYTES = 12
        private const val AUTH_TAG_SIZE_BITS = 128
        private const val ITERATION_COUNT = 65536 // Standard di sicurezza raccomandato
    }

    /**
     * Deriva una chiave di criptazione sicura da una password.
     * Usa PBKDF2, un algoritmo standard per questo scopo.
     * @param password La password inserita dall'utente.
     * @param salt Un valore casuale per prevenire attacchi a dizionario.
     * @return Una SecretKey a 256 bit.
     */
    private fun getKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_SIZE_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /**
     * Cripta i dati da un InputStream e li scrive in un OutputStream.
     * @param password La password per derivare la chiave.
     * @param inputStream Lo stream di dati in chiaro (es. il file audio).
     * @param outputStream Lo stream dove verranno scritti i dati criptati.
     */
    fun encrypt(password: String, inputStream: InputStream, outputStream: OutputStream) {
        // 1. Genera un 'salt' e un 'IV' casuali e sicuri.
        val salt = ByteArray(SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)

        val iv = ByteArray(IV_SIZE_BYTES)
        SecureRandom().nextBytes(iv)

        // 2. Deriva la chiave dalla password e dal salt.
        val key = getKeyFromPassword(password, salt)

        // 3. Inizializza il cifrario in modalità di criptazione.
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmParameterSpec = GCMParameterSpec(AUTH_TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmParameterSpec)

        // 4. Scrivi il salt e l'IV in chiaro nell'output stream.
        // Il backend dovrà leggerli per poter decriptare.
        outputStream.write(salt)
        outputStream.write(iv)

        // 5. Leggi i dati in chiaro a blocchi, criptali e scrivili.
        val buffer = ByteArray(4096)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val encryptedBytes = cipher.update(buffer, 0, bytesRead)
            if (encryptedBytes != null) {
                outputStream.write(encryptedBytes)
            }
        }
        val finalEncryptedBytes = cipher.doFinal()
        if (finalEncryptedBytes != null) {
            outputStream.write(finalEncryptedBytes)
        }
    }

    // Per ora non implementiamo la decriptazione lato client,
    // dato che verrà fatta sul backend. Se servisse in futuro, andrebbe qui.
    // fun decrypt(password: String, inputStream: InputStream, outputStream: OutputStream) { ... }
}