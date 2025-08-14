package com.example.lifelog

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.lifelog.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch
import java.io.File

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter
    private lateinit var settingsManager: SettingsManager
    private var voiceprintFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager.getInstance(this)

        onboardingAdapter = OnboardingAdapter(this)
        // Usa l'ID corretto dal tuo XML
        binding.viewPagerOnboarding.adapter = onboardingAdapter

        // Usa l'ID corretto dal tuo XML
        binding.viewPagerOnboarding.isUserInputEnabled = false

        registerListeners()
    }

    private fun registerListeners() {
        // Usa l'ID corretto dal tuo XML
        binding.buttonNext.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            if (currentItem < onboardingAdapter.itemCount - 1) {
                if (currentItem == 1) {
                    val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") as? ConfigurationFragment
                    if (fragment?.areInputsValid() == true) {
                        binding.viewPagerOnboarding.currentItem = currentItem + 1
                    } else {
                        Toast.makeText(this, "Alias, URL e Password sono obbligatori.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    binding.viewPagerOnboarding.currentItem = currentItem + 1
                }
            } else {
                completeOnboarding()
            }
        }

        // Usa l'ID corretto dal tuo XML
        binding.buttonBack.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            if (currentItem > 0) {
                binding.viewPagerOnboarding.currentItem = currentItem - 1
            }
        }

        // Usa l'ID corretto dal tuo XML
        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.buttonBack.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE

                if (position == onboardingAdapter.itemCount - 1) {
                    binding.buttonNext.text = getString(R.string.finish)
                } else {
                    binding.buttonNext.text = getString(R.string.next)
                }
            }
        })
    }

    fun onVoiceprintRecorded(filePath: String) {
        this.voiceprintFilePath = filePath
    }

    private fun completeOnboarding() {
        val configFragment = supportFragmentManager.findFragmentByTag("f1") as? ConfigurationFragment

        if (configFragment == null || !configFragment.areInputsValid()) {
            Toast.makeText(this, "Dati di configurazione mancanti o non validi.", Toast.LENGTH_LONG).show()
            binding.viewPagerOnboarding.currentItem = 1
            return
        }

        if (voiceprintFilePath.isNullOrEmpty()) {
            Toast.makeText(this, "Per favore, registra la tua impronta vocale.", Toast.LENGTH_LONG).show()
            return
        }

        val (firstName, lastName, alias, url, password) = configFragment.getSettingsData()

        lifecycleScope.launch {
            settingsManager.saveGeneralSettings(firstName, lastName, alias, url)
            settingsManager.savePassword(password)

            val file = File(voiceprintFilePath!!)
            val audioSegment = AudioSegment(
                filePath = voiceprintFilePath!!,
                timestamp = System.currentTimeMillis(),
                latitude = null,
                longitude = null,
                isUploaded = false
            )
            val db = AppDatabase.getDatabase(this@OnboardingActivity)
            val segmentId = db.audioSegmentDao().insert(audioSegment)
            Log.d("OnboardingActivity", "Voiceprint salvato nel DB con ID: $segmentId")

            scheduleVoiceprintUpload(segmentId, voiceprintFilePath!!)

            val sharedPrefs = getSharedPreferences("app_status", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()

            val intent = Intent(this@OnboardingActivity, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun scheduleVoiceprintUpload(segmentId: Long, filePath: String) {
        // La tua logica esistente per schedulare il worker
    }
}