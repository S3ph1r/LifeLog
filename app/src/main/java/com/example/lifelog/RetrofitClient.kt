package com.example.lifelog

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object RetrofitClient {

    // Crea un client HTTP con un logger per vedere i dettagli delle chiamate di rete in Logcat
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    // Costruisce l'istanza di Retrofit. L'URL di base verrà impostato dinamicamente.
    fun getClient(baseUrl: String): ApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            // Non aggiungiamo un converter (es. GsonConverterFactory) perché ci interessa
            // solo inviare un file e ricevere una risposta di successo/errore, non un JSON complesso.
            .build()
            .create(ApiService::class.java)
    }
}