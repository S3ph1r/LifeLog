package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.lifelog.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter
    private val audioSegmentDao: AudioSegmentDao by lazy {
        AppDatabase.getDatabase(application).audioSegmentDao()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            Log.d("OnboardingActivity", "Permessi concessi. Procedo alla configurazione.")
            binding.viewPagerOnboarding.setCurrentItem(1, true)
        } else {
            Log.w("OnboardingActivity", "Permessi critici negati.")
            Toast.makeText(this, "I permessi sono necessari per usare l'app.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onboardingAdapter = OnboardingAdapter(this)
        binding.viewPagerOnboarding.adapter = onboardingAdapter
        binding.viewPagerOnboarding.isUserInputEnabled = false

        setupButtonListeners()
        setupPageChangeCallback()
        updateUIForPage(0)
    }

    private fun setupButtonListeners() {
        binding.buttonNext.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            val lastItem = onboardingAdapter.itemCount - 1

            when (currentItem) {
                0 -> {
                    Log.d("OnboardingActivity", "Lancio MainActivity per richiesta permessi...")
                    val intent = Intent(this, MainActivity::class.java)
                    permissionLauncher.launch(intent)
                }
                1 -> {
                    val configFragment = onboardingAdapter.fragments[currentItem] as ConfigurationFragment
                    if (configFragment.saveSettings()) {
                        binding.viewPagerOnboarding.setCurrentItem(currentItem + 1, true)
                    } else {
                        Toast.makeText(this, "Compila i campi obbligatori", Toast.LENGTH_SHORT).show()
                    }
                }
                lastItem -> {
                    handleFinishOnboarding()
                }
            }
        }

        binding.buttonBack.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            if (currentItem > 0) {
                binding.viewPagerOnboarding.setCurrentItem(currentItem - 1, true)
            }
        }
    }

    private fun handleFinishOnboarding() {
        val voiceprintFragment = onboardingAdapter.fragments.last() as VoiceprintFragment
        if (!voiceprintFragment.isRecordingComplete) {
            Toast.makeText(this, "Completa la registrazione del voiceprint", Toast.LENGTH_SHORT).show()
            return
        }

        val voiceprintFile = voiceprintFragment.getVoiceprintFile()
        if (voiceprintFile == null) {
            Toast.makeText(this, "Errore: file voiceprint non trovato.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.buttonNext.isEnabled = false
        binding.buttonBack.isEnabled = false
        Toast.makeText(this, "Finalizzazione...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val segment = AudioSegment(
                filePath = voiceprintFile.absolutePath,
                timestamp = timestamp,
                isVoiceprint = true
            )
            val segmentId = audioSegmentDao.insertAndGetId(segment)
            Log.d("OnboardingActivity", "Voiceprint salvato nel DB con ID: $segmentId")

            scheduleVoiceprintUpload(segmentId, voiceprintFile.absolutePath, timestamp)

            val prefs = AppPreferences.getInstance(this@OnboardingActivity)
            prefs.isOnboardingCompleted = true
            prefs.isServiceActive = true

            val serviceIntent = Intent(this@OnboardingActivity, AudioRecordingService::class.java)
            ContextCompat.startForegroundService(this@OnboardingActivity, serviceIntent)

            val dashboardIntent = Intent(this@OnboardingActivity, DashboardActivity::class.java)
            startActivity(dashboardIntent)
            finish()
        }
    }

    private fun scheduleVoiceprintUpload(segmentId: Long, filePath: String, timestamp: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putString(UploadWorker.KEY_FILE_PATH, filePath)
            .putLong(UploadWorker.KEY_SEGMENT_ID, segmentId)
            .putLong(UploadWorker.KEY_TIMESTAMP, timestamp)
            .putBoolean(UploadWorker.KEY_IS_VOICEPRINT, true)
            .build()

        val uploadWorkRequest = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(uploadWorkRequest)
        Log.d("OnboardingActivity", "UploadWorker schedulato per il voiceprint (ID: $segmentId)")
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
        when (position) {
            0 -> {
                binding.buttonBack.visibility = View.INVISIBLE
                binding.buttonNext.text = getString(R.string.next)
                binding.textViewOnboardingTitle.text = "Benvenuto"
            }
            1 -> {
                binding.buttonBack.visibility = View.VISIBLE
                binding.buttonNext.text = getString(R.string.next)
                binding.textViewOnboardingTitle.text = "Configurazione"
            }
            2 -> {
                binding.buttonBack.visibility = View.VISIBLE
                binding.buttonNext.text = getString(R.string.finish)
                binding.textViewOnboardingTitle.text = "Impronta Vocale"
            }
        }
    }
}