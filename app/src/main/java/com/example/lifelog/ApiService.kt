package com.example.lifelog

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {

    @Multipart
    @POST("/upload") // L'endpoint sul nostro server Python
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): Response<ResponseBody> // La risposta del server (può essere semplice testo o JSON)
}