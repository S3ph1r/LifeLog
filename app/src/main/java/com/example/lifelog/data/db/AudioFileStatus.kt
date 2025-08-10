// Percorso: app/src/main/java/com/example/lifelog/data/db/AudioFileStatus.kt

package com.example.lifelog.data.db

/**
 * Rappresenta i possibili stati di un file audio durante il suo ciclo di vita,
 * dalla registrazione all'upload.
 */
enum class AudioFileStatus {
    /**
     * Stato transitorio mentre un segmento audio è in fase di registrazione.
     */
    RECORDING_IN_PROGRESS,

    /**
     * Il segmento audio (.m4a) è stato registrato e salvato.
     * È in attesa di essere criptato.
     */
    PENDING_ENCRYPTION,

    /**
     * Il file è stato criptato (.enc) ed è pronto per essere caricato sul server.
     * Questo è lo stato principale che la UI mostrerà per i ricordi.
     */
    PENDING_UPLOAD,

    /**
     * Lo UploaderWorker ha preso in carico questo file e sta tentando di caricarlo.
     */
    UPLOADING,

    /**
     * Il file è stato caricato con successo sul server.
     * È in attesa di essere eliminato fisicamente dal dispositivo.
     */
    UPLOADED,

    /**
     * L'upload è fallito, ma può essere ritentato.
     */
    UPLOAD_FAILED_RETRYABLE,

    // --- MODIFICA: Nuovo stato per il voiceprint ---
    /**
     * Un file audio è un campione vocale (voiceprint) dell'utente, in attesa di
     * essere elaborato dal backend per l'identificazione dello speaker.
     */
    VOICEPRINT_PENDING,

    /**
     * Il voiceprint è stato elaborato con successo dal backend.
     */
    VOICEPRINT_PROCESSED
}