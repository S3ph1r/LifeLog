package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Otteniamo l'istanza del nostro nuovo gestore di preferenze.
        val appPreferences = AppPreferences.getInstance(this)

        // Leggiamo DIRETTAMENTE la proprietà. Non serve più una coroutine
        // perché SharedPreferences è sincrono e velocissimo.
        val isOnboardingCompleted = appPreferences.isOnboardingCompleted

        val intent = if (isOnboardingCompleted) {
            Intent(this, DashboardActivity::class.java)
        } else {
            Intent(this, OnboardingActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}