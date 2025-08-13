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

    // --- NUOVI CAMPI ---
    // Li rendiamo nullable perché la posizione potrebbe non essere sempre disponibile.
    val latitude: Double? = null,
    val longitude: Double? = null
)