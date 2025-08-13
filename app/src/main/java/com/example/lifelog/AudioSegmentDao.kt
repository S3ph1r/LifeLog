package com.example.lifelog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioSegmentDao {

    // Vecchio metodo di inserimento
    @Insert
    suspend fun insert(segment: AudioSegment)

    // --- NUOVO METODO ---
    // Inserisce un segmento e restituisce il suo ID.
    @Insert
    suspend fun insertAndGetId(segment: AudioSegment): Long

    @Update
    suspend fun update(segment: AudioSegment)

    @Query("SELECT * FROM audio_segments WHERE id = :segmentId")
    suspend fun getSegmentById(segmentId: Long): AudioSegment?

    @Query("SELECT * FROM audio_segments WHERE isUploaded = 0 ORDER BY timestamp DESC")
    fun getUnsyncedSegments(): Flow<List<AudioSegment>>
}