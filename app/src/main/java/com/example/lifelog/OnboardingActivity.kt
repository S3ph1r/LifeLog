package com.example.lifelog

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.lifelog.databinding.ActivityOnboardingBinding
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import androidx.core.content.ContextCompat

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var onboardingAdapter: OnboardingAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("OnboardingActivity", "Permessi concessi. Procedo alla configurazione.")
            // L'utente ha concesso i permessi, quindi possiamo andare avanti
            binding.viewPagerOnboarding.setCurrentItem(1, true)
        } else {
            Log.w("OnboardingActivity", "Permessi critici negati.")
            Toast.makeText(this, "I permessi sono necessari per usare l'app.", Toast.LENGTH_LONG).show()
            // Qui potremmo anche chiudere l'app, ma per ora non facciamo nulla
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
                0 -> { // Pagina 0 (Benvenuto) -> Chiediamo i permessi
                    Log.d("OnboardingActivity", "Lancio MainActivity per richiesta permessi...")
                    val intent = Intent(this, MainActivity::class.java)
                    permissionLauncher.launch(intent)
                }
                1 -> { // Pagina 1 (Configurazione) -> vai a Voiceprint
                    val configFragment = onboardingAdapter.fragments[currentItem] as ConfigurationFragment
                    if (configFragment.saveSettings()) {
                        binding.viewPagerOnboarding.setCurrentItem(currentItem + 1, true)
                    } else {
                        Toast.makeText(this, "Compila i campi obbligatori", Toast.LENGTH_SHORT).show()
                    }
                }
                lastItem -> { // Ultima pagina (Voiceprint) -> fine
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
        Toast.makeText(this, "Finalizzazione...", Toast.LENGTH_LONG).show()

        lifecycleScope.launch {
            val success = uploadVoiceprint(voiceprintFile)
            if (success) {
                Log.d("OnboardingActivity", "Upload voiceprint riuscito.")

                // --- IMPORTANTE: Ora che TUTTO è finito, salviamo lo stato di onboarding completato ---
                val prefs = AppPreferences.getInstance(this@OnboardingActivity)
                prefs.isOnboardingCompleted = true
                prefs.isServiceActive = true // Il servizio deve partire attivo

                // E avviamo il servizio per la prima volta!
                val serviceIntent = Intent(this@OnboardingActivity, AudioRecordingService::class.java)
                ContextCompat.startForegroundService(this@OnboardingActivity, serviceIntent)

                // Infine, andiamo alla Dashboard
                val dashboardIntent = Intent(this@OnboardingActivity, DashboardActivity::class.java)
                startActivity(dashboardIntent)
                finish() // Chiudiamo l'onboarding per sempre
            } else {
                Log.e("OnboardingActivity", "Upload voiceprint fallito.")
                Toast.makeText(this@OnboardingActivity, "Upload fallito. Riprova.", Toast.LENGTH_LONG).show()
                binding.buttonNext.isEnabled = true
                binding.buttonBack.isEnabled = true
            }
        }
    }

    private suspend fun uploadVoiceprint(file: File): Boolean {
        // ... (questa funzione rimane identica)
        val settings = SettingsManager.getInstance(this)
        val firstNameBody = settings.userFirstName.toRequestBody("text/plain".toMediaTypeOrNull())
        val lastNameBody = settings.userLastName.toRequestBody("text/plain".toMediaTypeOrNull())
        val aliasBody = settings.userAlias.toRequestBody("text/plain".toMediaTypeOrNull())
        val requestFileBody = file.asRequestBody("audio/m4a".toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("voiceprintFile", file.name, requestFileBody)
        return try {
            val response = RetrofitInstance.getInstance(this).api.uploadVoiceprint(firstNameBody, lastNameBody, aliasBody, filePart)
            response.isSuccessful
        } catch (e: Exception) {
            Log.e("OnboardingActivity", "Eccezione durante l'upload del voiceprint", e)
            false
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
        // ... (identico a prima)
    }
}