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
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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

        binding.viewPagerOnboarding.adapter = onboardingAdapter
        binding.viewPagerOnboarding.isUserInputEnabled = false

        registerListeners()
    }

    private fun registerListeners() {
        binding.buttonNext.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            if (currentItem == 1) { // Indice PermissionsFragment
                Toast.makeText(this, "Per favore, concedi i permessi richiesti per continuare.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentItem < onboardingAdapter.itemCount - 1) {
                if (currentItem == 2) { // Indice ConfigurationFragment
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

        binding.buttonBack.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            if (currentItem > 0) {
                binding.viewPagerOnboarding.currentItem = currentItem - 1
            }
        }

        binding.viewPagerOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.buttonBack.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
                binding.buttonNext.visibility = if (position == 1) View.INVISIBLE else View.VISIBLE

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

    fun onPermissionsGranted() {
        if (binding.viewPagerOnboarding.currentItem == 1) {
            Log.d("OnboardingActivity", "Permessi concessi, avanzo alla schermata di configurazione.")
            binding.viewPagerOnboarding.currentItem = 2
        }
    }

    private fun completeOnboarding() {
        val configFragment = supportFragmentManager.findFragmentByTag("f2") as? ConfigurationFragment

        if (configFragment == null || !configFragment.areInputsValid()) {
            Toast.makeText(this, "Dati di configurazione mancanti o non validi.", Toast.LENGTH_LONG).show()
            binding.viewPagerOnboarding.currentItem = 2
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

            val audioSegment = AudioSegment(
                filePath = voiceprintFilePath!!,
                timestamp = System.currentTimeMillis(),
                isUploaded = false,
                latitude = null,
                longitude = null
            )
            val db = AppDatabase.getDatabase(this@OnboardingActivity)
            val segmentId = db.audioSegmentDao().insert(audioSegment)
            Log.d("OnboardingActivity", "Voiceprint salvato nel DB con ID: $segmentId")

            scheduleVoiceprintUpload(segmentId, voiceprintFilePath!!)

            val sharedPrefs = getSharedPreferences("app_status", Context.MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()

            // Avviamo il servizio di registrazione per la prima volta
            Log.d("OnboardingActivity", "Onboarding completato. Avvio del servizio di registrazione.")
            val serviceIntent = Intent(this@OnboardingActivity, AudioRecordingService::class.java).apply {
                action = AudioRecordingService.ACTION_START
            }
            startService(serviceIntent)

            // Avviamo la DashboardActivity
            val intent = Intent(this@OnboardingActivity, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun scheduleVoiceprintUpload(segmentId: Long, filePath: String) {
        Log.d("OnboardingActivity", "Schedulazione dell'UploadWorker per il voiceprint (ID: $segmentId)")
        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_FILE_PATH, filePath)
            .putLong(UploadWorker.KEY_SEGMENT_ID, segmentId)
            .putLong(UploadWorker.KEY_TIMESTAMP, System.currentTimeMillis())
            .putBoolean(UploadWorker.KEY_IS_VOICEPRINT, true)
            .build()
        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(this).enqueue(uploadWorkRequest)
        Log.d("OnboardingActivity", "Lavoro di upload per il voiceprint accodato.")
    }
}