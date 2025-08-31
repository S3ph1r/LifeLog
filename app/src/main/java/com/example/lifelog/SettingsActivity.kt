package com.example.lifelog

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.lifelog.databinding.ActivitySettingsBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var appPreferences: AppPreferences
    private lateinit var userRepository: UserRepository
    private val audioSegmentDao by lazy { AppDatabase.getDatabase(this).audioSegmentDao() }
    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appPreferences = AppPreferences.getInstance(this)
        val userDao = AppDatabase.getDatabase(this).userDao()
        userRepository = UserRepository(userDao)

        loadCurrentSettings()

        binding.buttonSaveSettings.setOnClickListener {
            saveSettings()
        }

        setupVoiceprintButtons()
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            val user = userRepository.user.first()
            if (user != null) {
                binding.editTextFirstName.setText(user.firstName)
                binding.editTextLastName.setText(user.lastName)
                binding.editTextAlias.setText(user.alias)
                binding.editTextServerUrl.setText(user.serverUrl)
            }
            binding.editTextPassword.setText(appPreferences.password)
        }
    }

    private fun saveSettings() {
        val firstName = binding.editTextFirstName.text.toString().trim()
        val lastName = binding.editTextLastName.text.toString().trim()
        val alias = binding.editTextAlias.text.toString().trim()
        val url = binding.editTextServerUrl.text.toString().trim()
        val password = binding.editTextPassword.text.toString().trim()

        if (alias.isBlank() || url.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Alias, URL e Password sono obbligatori.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val updatedUser = User(firstName = firstName, lastName = lastName, alias = alias, serverUrl = url)
            userRepository.saveUser(updatedUser)
            appPreferences.password = password
            Toast.makeText(this@SettingsActivity, "Impostazioni salvate", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupVoiceprintButtons() {
        binding.buttonPlayVoiceprint.setOnClickListener {
            togglePlayStopVoiceprint()
        }

        binding.buttonRerecordVoiceprint.setOnClickListener {
            stopPlayback()
            val intent = Intent(this, OnboardingActivity::class.java).apply {
                putExtra("NAVIGATE_TO_VOICEPRINT", true)
            }
            startActivity(intent)
        }
    }

    private fun togglePlayStopVoiceprint() {
        if (mediaPlayer?.isPlaying == true) {
            stopPlayback()
        } else {
            playVoiceprint()
        }
    }

    private fun playVoiceprint() {
        lifecycleScope.launch {
            val voiceprintSegment = audioSegmentDao.findUnuploadedVoiceprint()
            if (voiceprintSegment == null || voiceprintSegment.filePath.isBlank()) {
                Toast.makeText(this@SettingsActivity, "Nessun voiceprint registrato trovato.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val audioFile = java.io.File(voiceprintSegment.filePath)
            if (!audioFile.exists()) {
                Toast.makeText(this@SettingsActivity, "File del voiceprint non trovato.", Toast.LENGTH_LONG).show()
                return@launch
            }

            stopPlayback()

            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(audioFile.absolutePath)
                    prepareAsync()
                    setOnPreparedListener { player ->
                        player.start()
                        updatePlayButton(isPlaying = true)
                        Toast.makeText(this@SettingsActivity, "Riproduzione...", Toast.LENGTH_SHORT).show()
                    }
                    setOnCompletionListener {
                        Toast.makeText(this@SettingsActivity, "Riproduzione terminata.", Toast.LENGTH_SHORT).show()
                        stopPlayback()
                    }
                    setOnErrorListener { _, _, _ ->
                        Toast.makeText(this@SettingsActivity, "Errore durante la riproduzione.", Toast.LENGTH_SHORT).show()
                        stopPlayback()
                        true
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Errore durante la preparazione del MediaPlayer", e)
                    Toast.makeText(this@SettingsActivity, "Impossibile riprodurre il file.", Toast.LENGTH_SHORT).show()
                    stopPlayback()
                }
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        updatePlayButton(isPlaying = false)
    }

    private fun updatePlayButton(isPlaying: Boolean) {
        val playButton = binding.buttonPlayVoiceprint as MaterialButton // Cast a MaterialButton
        if (isPlaying) {
            playButton.text = "Stop"
            playButton.icon = ContextCompat.getDrawable(this, android.R.drawable.ic_media_pause)
        } else {
            playButton.text = "Ascolta"
            playButton.icon = ContextCompat.getDrawable(this, android.R.drawable.ic_media_play)
        }
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }
}