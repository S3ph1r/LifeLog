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
     * Corrisponde all'endpoint: POST /api/segments
     */
    @Multipart
    @POST("api/segments") // <-- MODIFICA: URL aggiornato
    suspend fun uploadAudioSegment(
        @Part file: MultipartBody.Part
        // La parte "description" è stata rimossa perché non più richiesta dalle specifiche.
    ): Response<Void>

    /**
     * Esegue l'upload del voiceprint e dei dati utente durante l'onboarding.
     * Corrisponde all'endpoint: POST /api/onboarding
     */
    @Multipart
    @POST("api/onboarding") // <-- MODIFICA: URL aggiornato
    suspend fun uploadOnboardingData(
        @Part("firstName") firstName: RequestBody, // <-- MODIFICA: name del campo aggiornato
        @Part("lastName") lastName: RequestBody,  // <-- MODIFICA: name del campo aggiornato
        @Part("alias") alias: RequestBody,
        @Part voiceprintFile: MultipartBody.Part
    ): Response<Void>
}