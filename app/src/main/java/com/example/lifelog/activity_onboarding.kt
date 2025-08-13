package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.lifelog.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onboardingAdapter = OnboardingAdapter(this)
        binding.viewPagerOnboarding.adapter = onboardingAdapter

        // Disabilitiamo lo swipe manuale tra le pagine per forzare l'uso dei bottoni
        binding.viewPagerOnboarding.isUserInputEnabled = false

        setupButtonListeners()
        setupPageChangeCallback()
    }

    private fun setupButtonListeners() {
        binding.buttonNext.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            val lastItem = onboardingAdapter.itemCount - 1

            if (currentItem == lastItem) {
                // Siamo all'ultima pagina, il pulsante è "Fine"
                val configFragment = onboardingAdapter.fragments[lastItem] as ConfigurationFragment
                if (configFragment.saveSettings()) {
                    // Impostazioni salvate con successo, passiamo ai permessi
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish() // Chiudiamo l'onboarding per non poterci tornare
                } else {
                    Toast.makeText(this, "Per favore, compila i campi obbligatori", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Passiamo alla pagina successiva
                binding.viewPagerOnboarding.setCurrentItem(currentItem + 1, true)
            }
        }

        binding.buttonBack.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            if (currentItem > 0) {
                binding.viewPagerOnboarding.setCurrentItem(currentItem - 1, true)
            }
        }
    }

    private fun setupPageChangeCallback() {
        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateUIForPage(position)
            }
        })
    }

    private fun updateUIForPage(position: Int) {
        val lastItem = onboardingAdapter.itemCount - 1
        when (position) {
            0 -> { // Prima pagina (Benvenuto)
                binding.buttonBack.visibility = View.INVISIBLE
                binding.buttonNext.text = "Avanti"
            }
            lastItem -> { // Ultima pagina (Configurazione)
                binding.buttonBack.visibility = View.VISIBLE
                binding.buttonNext.text = "Fine"
            }
            else -> { // Pagine intermedie (se ce ne fossero)
                binding.buttonBack.visibility = View.VISIBLE
                binding.buttonNext.text = "Avanti"
            }
        }
    }
}