package com.example.lifelog.audio
import com.example.lifelog.audio.RecordingState
enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED, // Lo manteniamo per flessibilità futura
    ERROR,
    INITIALIZING // Potrebbe essere utile
}