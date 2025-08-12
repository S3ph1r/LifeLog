package com.example.lifelog.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.IOException

object ConfigManager {
    private const val CONFIG_FILE_NAME = "lifelog_config.json"
    private const val TAG = "ConfigManager"

    private lateinit var configFile: File
    private val gson = Gson()
    private var currentConfig: AppConfig = AppConfig()

    fun initialize(context: Context) {
        val filesDir = context.filesDir
        configFile = File(filesDir, CONFIG_FILE_NAME)
        loadConfig()
        // --- CORREZIONE QUI ---
        Log.d(TAG, "ConfigManager inizializzato. Onboarding: ${currentConfig.isOnboardingComplete}")
    }

    private fun loadConfig() {
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                if (json.isNotBlank()) {
                    currentConfig = gson.fromJson(json, AppConfig::class.java)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel caricare config.json", e)
                currentConfig = AppConfig()
            }
        }
    }

    @Synchronized
    private fun saveConfig() {
        try {
            val json = gson.toJson(currentConfig)
            configFile.writeText(json)
        } catch (e: IOException) {
            Log.e(TAG, "Errore nel salvare config.json", e)
        }
    }

    fun getConfig(): AppConfig {
        return currentConfig
    }

    fun updateFirstName(name: String) {
        currentConfig.userFirstName = name
        saveConfig()
    }

    fun updateLastName(name: String) {
        currentConfig.userLastName = name
        saveConfig()
    }

    fun updateAlias(alias: String) {
        currentConfig.userAlias = alias
        saveConfig()
    }

    fun updateServerAddress(address: String) {
        currentConfig.serverAddress = address
        saveConfig()
    }

    fun updateEncryptionPassword(password: String) {
        currentConfig.encryptionPassword = password
        saveConfig()
    }

    fun completeOnboarding() {
        // --- CORREZIONE QUI ---
        currentConfig.isOnboardingComplete = true
        saveConfig()
    }
}