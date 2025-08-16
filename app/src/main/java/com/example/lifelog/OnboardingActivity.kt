package com.example.lifelog

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels // Importa la dipendenza corretta
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.lifelog.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    lateinit var pagerAdapter: OnboardingAdapter

    // --- 1. CREAZIONE DELL'ISTANZA DEL VIEWMODEL ---
    // "by viewModels()" è il modo moderno e corretto in Kotlin per creare
    // e legare un ViewModel a un'Activity. L'activity si occuperà di
    // mantenerlo in vita (anche durante la rotazione dello schermo).
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

        // La logica di questo listener è ora molto più semplice.
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
    }

    // --- 2. LOGICA SEMPLIFICATA: NESSUNA COMUNICAZIONE DIRETTA CON I FRAGMENT ---
    // Tutta la vecchia logica per trovare i fragment è stata rimossa.
    // Ora l'activity si basa solo sullo stato del ViewModel.
    private fun updateButtonState(position: Int) {
        val totalSteps = pagerAdapter.itemCount
        binding.buttonBack.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE

        val permissionsStepPosition = 1
        val configStepPosition = 2
        val voiceprintStepPosition = 3
        binding.buttonNext.text = if (position == voiceprintStepPosition) "Fine" else "Avanti"

        // La logica di abilitazione del pulsante ora è più pulita:
        binding.buttonNext.isEnabled = when (position) {
            permissionsStepPosition -> arePermissionsGranted
            configStepPosition -> viewModel.areInputsValid() // Chiede al ViewModel se i dati sono validi
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

    /**
     * Questa funzione viene chiamata da ConfigurationFragment ogni volta che il testo cambia.
     * Ora il suo unico scopo è dire all'activity di rivalutare lo stato del pulsante.
     */
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
            Log.d("OnboardingActivity", "Schedulazione dell'UploadWorker per il voiceprint (ID: $segmentId)")
            UploadUtils.scheduleUpload(applicationContext, filePath, segmentId, audioSegment.timestamp, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.viewPagerOnboarding.unregisterOnPageChangeCallback(pageChangeCallback)
    }
}