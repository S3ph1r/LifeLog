package com.example.lifelog

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_segments")
data class AudioSegment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val filePath: String,
    val timestamp: Long,
    var isUploaded: Boolean = false,

    val latitude: Double? = null,
    val longitude: Double? = null,

    // --- NUOVO CAMPO ---
    // Questo flag ci dirà se la registrazione è un segmento normale
    // o il file speciale per il voiceprint.
    val isVoiceprint: Boolean = false
)