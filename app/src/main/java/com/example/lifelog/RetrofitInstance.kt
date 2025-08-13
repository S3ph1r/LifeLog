package com.example.lifelog

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Oggetto che fornisce un'istanza di ApiService.
 * Ora è una classe per poter ricevere il Context e accedere alle impostazioni.
 */
class RetrofitInstance(private val context: Context) {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    // L'istanza di Retrofit non è più un singleton fisso,
    // viene creata con l'URL preso dalle impostazioni.
    private val retrofit: Retrofit
        get() {
            // Leggiamo l'URL salvato ogni volta.
            // Se l'URL non è valido o è vuoto, Retrofit lancerà un'eccezione
            // che verrà gestita nel Worker.
            val baseUrl = SettingsManager.getInstance(context).serverUrl

            // Per sicurezza, aggiungiamo la slash finale se manca
            val finalBaseUrl = if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/"

            return Retrofit.Builder()
                .baseUrl(finalBaseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

    // L'api viene creata usando l'istanza di Retrofit dinamica.
    val api: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // Per mantenere un accesso simile a un singleton, usiamo un companion object
    companion object {
        @Volatile

        private var instance: RetrofitInstance? = null

        fun getInstance(context: Context): RetrofitInstance {
            return instance ?: synchronized(this) {
                instance ?: RetrofitInstance(context.applicationContext).also { instance = it }
            }
        }
    }
}