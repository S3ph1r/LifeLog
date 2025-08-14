package com.example.lifelog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioSegmentDao {

    // --- INIZIO MODIFICA ---
    // Cambia il tipo di ritorno da Unit (implicito) a Long.
    // Room restituirà automaticamente l'ID della riga appena inserita.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(segment: AudioSegment): Long
    // --- FINE MODIFICA ---

    @Update
    suspend fun update(segment: AudioSegment)

    @Query("SELECT * FROM audio_segments WHERE isUploaded = 0 ORDER BY timestamp DESC")
    fun getUnuploadedSegmentsFlow(): Flow<List<AudioSegment>>

    @Query("SELECT * FROM audio_segments WHERE id = :id")
    suspend fun getSegmentById(id: Long): AudioSegment?

    @Query("SELECT * FROM audio_segments WHERE isUploaded = 0 ORDER BY timestamp ASC LIMIT 1")
    suspend fun getFirstUnuploadedSegment(): AudioSegment?

    @Query("UPDATE audio_segments SET isUploaded = :uploadedStatus WHERE id = :segmentId")
    suspend fun updateUploadStatus(segmentId: Long, uploadedStatus: Boolean)

    // Se ti serve un metodo per cancellare per ID, puoi aggiungerlo qui
    // @Query("DELETE FROM audio_segments WHERE id = :id")
    // suspend fun deleteById(id: Long)
}