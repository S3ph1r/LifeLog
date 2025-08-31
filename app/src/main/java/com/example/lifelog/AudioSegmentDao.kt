package com.example.lifelog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioSegmentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(segment: AudioSegment): Long

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

    /**
     * --- NUOVA FUNZIONE PER IL SYNC MANUALE ---
     * Cerca nel database un singolo segmento che sia un voiceprint e non sia ancora stato caricato.
     * Restituisce il primo che trova (o null se non ce ne sono).
     */
    @Query("SELECT * FROM audio_segments WHERE isVoiceprint = 1 AND isUploaded = 0 LIMIT 1")
    suspend fun findUnuploadedVoiceprint(): AudioSegment?
}