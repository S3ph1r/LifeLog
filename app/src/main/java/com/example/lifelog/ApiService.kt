package com.example.lifelog

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    /**
     * Esegue l'upload di un singolo segmento audio criptato.
     */
    @Multipart
    @POST("upload")
    suspend fun uploadAudio(
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): Response<Void>

    /**
     * NUOVO METODO: Esegue l'upload del voiceprint insieme ai dati dell'utente.
     * Invia un file audio e diversi campi di testo in una singola richiesta.
     */
    @Multipart
    @POST("voiceprint") // Un endpoint dedicato per il voiceprint
    suspend fun uploadVoiceprint(
        @Part("first_name") firstName: RequestBody,
        @Part("last_name") lastName: RequestBody,
        @Part("alias") alias: RequestBody,
        @Part voiceprintFile: MultipartBody.Part
    ): Response<Void> // Ipotizziamo che il server risponda solo con OK (200)
}