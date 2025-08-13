package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Usiamo AppPreferences per controllare lo stato di completamento dell'onboarding.
        // Questo flag viene impostato su 'true' solo alla fine del flusso dei permessi
        // nella MainActivity.
        val prefs = AppPreferences.getInstance(this)

        if (prefs.isOnboardingCompleted) {
            // L'utente ha già fatto tutto. Andiamo direttamente alla Dashboard.
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        } else {
            // È il primo avvio. Iniziamo il flusso di onboarding.
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
        }

        // Chiudiamo questa Activity per non poterci tornare con il tasto back.
        finish()
    }
}