// Percorso: app/src/main/java/com/example/lifelog/OnboardingActivity.kt

package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.lifelog.data.SettingsManager
import com.example.lifelog.ui.onboarding.BackendSetupFragment
import com.example.lifelog.ui.onboarding.OnboardingFragmentCallback
import com.example.lifelog.ui.onboarding.OnboardingCompleteFragment
import com.example.lifelog.ui.onboarding.PermissionsFragment
import com.example.lifelog.ui.onboarding.UserInfoFragment
import com.example.lifelog.ui.onboarding.VoiceprintFragment

/**
 * Activity che ospita il flusso di onboarding.
 * Agisce come un contenitore e gestore per i vari fragment che compongono
 * i passaggi della configurazione iniziale.
 */
class OnboardingActivity : AppCompatActivity(), OnboardingFragmentCallback {

    private lateinit var onboardingNextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        onboardingNextButton = findViewById(R.id.onboarding_next_button)
        onboardingNextButton.setOnClickListener {
            // Quando il pulsante "Prosegui" è cliccato, chiediamo al fragment corrente
            // se la sua fase è completa e se possiamo avanzare.
            val currentFragment = supportFragmentManager.findFragmentById(R.id.onboarding_fragment_container)
            val canAdvance = when (currentFragment) {
                is UserInfoFragment -> currentFragment.handleNextButtonClick()
                is PermissionsFragment -> currentFragment.handleNextButtonClick()
                is BackendSetupFragment -> currentFragment.handleNextButtonClick()
                is VoiceprintFragment -> currentFragment.handleNextButtonClick()
                is OnboardingCompleteFragment -> currentFragment.handleNextButtonClick()
                else -> true // Caso di default o errore, permette di avanzare
            }

            if (canAdvance) {
                navigateToNextStep()
            }
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.onboarding_fragment_container, UserInfoFragment())
                .commit()
        }
    }

    /**
     * Implementazione del callback OnboardingFragmentCallback.
     * I fragment chiameranno questo metodo per aggiornare lo stato del pulsante "Prosegui".
     */
    override fun setNextButtonEnabled(isEnabled: Boolean) {
        onboardingNextButton.isEnabled = isEnabled
    }

    /**
     * Metodo chiamato quando il pulsante "Prosegui" globale viene cliccato.
     * Determina il prossimo fragment da mostrare in base a quello corrente.
     * MODIFICA: Reso pubblico per essere accessibile dai fragment.
     */
    fun navigateToNextStep() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.onboarding_fragment_container)

        when (currentFragment) {
            is UserInfoFragment -> {
                replaceFragment(PermissionsFragment())
            }
            is PermissionsFragment -> {
                replaceFragment(BackendSetupFragment())
            }
            is BackendSetupFragment -> {
                replaceFragment(VoiceprintFragment())
            }
            is VoiceprintFragment -> {
                replaceFragment(OnboardingCompleteFragment())
            }
            is OnboardingCompleteFragment -> {
                finishOnboarding()
            }
            else -> {
                Toast.makeText(this, "Errore di navigazione onboarding.", Toast.LENGTH_SHORT).show()
                finishOnboarding()
            }
        }
    }

    /**
     * Helper function per sostituire il fragment corrente con uno nuovo.
     */
    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left, // Animazione per il nuovo fragment
                android.R.anim.slide_out_right, // Animazione per il vecchio fragment
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .replace(R.id.onboarding_fragment_container, fragment)
            .addToBackStack(null) // Permette all'utente di tornare indietro col tasto back
            .commit()
    }

    /**
     * Metodo finale chiamato per concludere il flusso di onboarding.
     * Imposta il flag 'onboarding complete' e avvia la MainActivity.
     */
    private fun finishOnboarding() {
        SettingsManager.isOnboardingComplete = true
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Pulisce lo stack delle activity
        startActivity(intent)
        finish() // Chiude OnboardingActivity
    }
}