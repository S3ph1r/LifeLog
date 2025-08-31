package com.example.lifelog

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.lifelog.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    lateinit var pagerAdapter: OnboardingAdapter
    val viewModel: OnboardingViewModel by viewModels()

    var lastRecordedVoiceprintPath: String? = null
    private var arePermissionsGranted = false

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            updateButtonState(position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pagerAdapter = OnboardingAdapter(this)
        binding.viewPagerOnboarding.adapter = pagerAdapter
        binding.viewPagerOnboarding.isUserInputEnabled = false
        binding.viewPagerOnboarding.registerOnPageChangeCallback(pageChangeCallback)

        binding.buttonNext.setOnClickListener {
            val currentStep = binding.viewPagerOnboarding.currentItem
            if (currentStep < pagerAdapter.itemCount - 1) {
                binding.viewPagerOnboarding.currentItem = currentStep + 1
            }
        }

        binding.buttonBack.setOnClickListener {
            val currentStep = binding.viewPagerOnboarding.currentItem
            if (currentStep > 0) {
                binding.viewPagerOnboarding.currentItem = currentStep - 1
            }
        }

        checkInitialPermissions()
        updateButtonState(0)

        // --- NUOVA LOGICA AGGIUNTA QUI ---
        // Controlla se l'activity è stata avviata con l'intento di
        // andare direttamente alla registrazione del voiceprint.
        if (intent.getBooleanExtra("NAVIGATE_TO_VOICEPRINT", false)) {
            // L'indice del VoiceprintFragment nel nostro adapter è 3 (0-based)
            val voiceprintStepPosition = 3
            binding.viewPagerOnboarding.setCurrentItem(voiceprintStepPosition, false) // false per non mostrare l'animazione di scorrimento
        }
    }

    private fun updateButtonState(position: Int) {
        val totalSteps = pagerAdapter.itemCount
        binding.buttonBack.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE

        val permissionsStepPosition = 1
        val configStepPosition = 2
        val voiceprintStepPosition = 3
        binding.buttonNext.text = if (position == voiceprintStepPosition) "Fine" else "Avanti"

        binding.buttonNext.isEnabled = when (position) {
            permissionsStepPosition -> arePermissionsGranted
            configStepPosition -> viewModel.areInputsValid()
            voiceprintStepPosition -> lastRecordedVoiceprintPath != null
            else -> true
        }

        val finalStepPosition = totalSteps - 1
        if (position == finalStepPosition) {
            binding.buttonNext.visibility = View.GONE
            binding.buttonBack.visibility = View.GONE
        } else {
            binding.buttonNext.visibility = View.VISIBLE
        }
    }

    fun onConfigurationInputChanged() {
        if (binding.viewPagerOnboarding.currentItem == 2) {
            updateButtonState(2)
        }
    }

    fun onPermissionsGranted() {
        arePermissionsGranted = true
        updateButtonState(binding.viewPagerOnboarding.currentItem)
    }

    private fun checkInitialPermissions() {
        val requiredPermissions = listOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            arePermissionsGranted = true
        }
    }

    fun onVoiceprintRecorded(filePath: String) {
        lastRecordedVoiceprintPath = filePath
        updateButtonState(binding.viewPagerOnboarding.currentItem)
    }

    fun scheduleVoiceprintUpload(filePath: String) {
        val audioSegment = AudioSegment(
            filePath = filePath,
            timestamp = System.currentTimeMillis(),
            isUploaded = false,
            isVoiceprint = true
        )

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val segmentId = db.audioSegmentDao().insert(audioSegment)
            Log.d("OnboardingActivity", "Voiceprint salvato nel DB (ID: $segmentId). Schedulazione upload...")

            UploadUtils.scheduleUpload(
                context = applicationContext,
                filePath = filePath,
                segmentId = segmentId,
                isVoiceprint = true
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.viewPagerOnboarding.unregisterOnPageChangeCallback(pageChangeCallback)
    }
}