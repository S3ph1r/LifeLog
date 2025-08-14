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

        binding.viewPagerOnboarding.adapter = onboardingAdapter
        binding.viewPagerOnboarding.isUserInputEnabled = false

        registerListeners()
    }

    private fun registerListeners() {
        binding.buttonNext.setOnClickListener {
            val currentItem = binding.viewPagerOnboarding.currentItem
            // Non permettiamo di andare avanti dalla schermata dei permessi
            // se non sono stati concessi. Sarà il fragment a farci avanzare.
            if (currentItem == 1) {
                Toast.makeText(this, "Per favore, concedi i permessi richiesti per continuare.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentItem < onboardingAdapter.itemCount - 1) {
                if (currentItem == 2) { // Ora l'indice del ConfigurationFragment è 2
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

                // Nascondiamo il pulsante "Avanti" nella schermata dei permessi per evitare confusione
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

    /**
     * Chiamata dal PermissionsFragment quando tutti i permessi sono stati concessi.
     * Fa avanzare il ViewPager alla pagina successiva.
     */
    fun onPermissionsGranted() {
        if (binding.viewPagerOnboarding.currentItem == 1) { // L'indice del PermissionsFragment è 1
            Log.d("OnboardingActivity", "Permessi concessi, avanzo alla schermata di configurazione.")
            binding.viewPagerOnboarding.currentItem = 2 // Vai al ConfigurationFragment
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

            val intent = Intent(this@OnboardingActivity, DashboardActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun scheduleVoiceprintUpload(segmentId: Long, filePath: String) {
        // La tua logica esistente per schedulare il worker
    }
}