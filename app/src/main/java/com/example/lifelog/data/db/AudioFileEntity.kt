// Percorso: app/src/main/java/com/example/lifelog/data/db/AudioFileEntity.kt

package com.example.lifelog.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Rappresenta una singola registrazione audio come record nel database.
 * Ogni istanza di questa classe corrisponde a una riga nella tabella "audio_files".
 *
 * @param id La chiave primaria univoca, generata automaticamente da Room.
 * @param fileName Il nome del file criptato (es. "segment_20231027_103000_lat..._lon....enc").
 * @param filePath Il percorso assoluto del file sul dispositivo, per poterlo leggere o eliminare.
 * @param createdAt Timestamp (in millisecondi) di quando il record è stato creato. Utile per ordinare.
 * @param status Lo stato attuale del file nel suo ciclo di vita (es. PENDING_UPLOAD).
 * @param sizeInBytes La dimensione del file in byte, per visualizzazione e statistiche.
 * @param uploadAttempts Il numero di volte che si è tentato di caricare questo file.
 */
@Entity(tableName = "audio_files")
data class AudioFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val fileName: String,
    val filePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: AudioFileStatus,
    val sizeInBytes: Long,
    val uploadAttempts: Int = 0
)