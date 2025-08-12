package com.example.lifelog.data

data class AppConfig(
    // --- CORREZIONE QUI ---
    var isOnboardingComplete: Boolean = false, // Era "isOboardingComplete"
    var userFirstName: String = "",
    var userLastName: String = "",
    var userAlias: String = "",
    var serverAddress: String = "",
    var encryptionPassword: String = ""
)