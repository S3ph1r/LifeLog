package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.lifelog.data.ConfigManager // <-- NUOVO IMPORT
import com.example.lifelog.ui.onboarding.BackendSetupFragment
import com.example.lifelog.ui.onboarding.OnboardingCompleteFragment
import com.example.lifelog.ui.onboarding.OnboardingFragmentCallback
import com.example.lifelog.ui.onboarding.PermissionsFragment
import com.example.lifelog.ui.onboarding.UserInfoFragment
import com.example.lifelog.ui.onboarding.VoiceprintFragment

class OnboardingActivity : AppCompatActivity(), OnboardingFragmentCallback {

    private lateinit var onboardingNextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        onboardingNextButton = findViewById(R.id.onboarding_next_button)
        onboardingNextButton.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.onboarding_fragment_container)
            val canAdvance = when (currentFragment) {
                is UserInfoFragment -> currentFragment.handleNextButtonClick()
                is PermissionsFragment -> currentFragment.handleNextButtonClick()
                is BackendSetupFragment -> currentFragment.handleNextButtonClick()
                is VoiceprintFragment -> currentFragment.handleNextButtonClick()
                is OnboardingCompleteFragment -> currentFragment.handleNextButtonClick()
                else -> true
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

    override fun setNextButtonEnabled(isEnabled: Boolean) {
        onboardingNextButton.isEnabled = isEnabled
    }

    fun navigateToNextStep() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.onboarding_fragment_container)
        when (currentFragment) {
            is UserInfoFragment -> replaceFragment(PermissionsFragment())
            is PermissionsFragment -> replaceFragment(BackendSetupFragment())
            is BackendSetupFragment -> replaceFragment(VoiceprintFragment())
            is VoiceprintFragment -> replaceFragment(OnboardingCompleteFragment())
            is OnboardingCompleteFragment -> finishOnboarding()
            else -> {
                Toast.makeText(this, "Errore di navigazione.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
            .replace(R.id.onboarding_fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun finishOnboarding() {
        ConfigManager.completeOnboarding()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}