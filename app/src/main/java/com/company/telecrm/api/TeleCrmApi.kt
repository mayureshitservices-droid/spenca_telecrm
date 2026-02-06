package com.company.telecrm.api

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TeleCrmApi {
    @POST("register")
    fun register(@Body request: RegisterRequest): Call<RegisterResponse>

    @POST("heartbeat")
    fun heartbeat(@Body request: HeartbeatRequest): Call<HeartbeatResponse>

    @POST("call-log")
    fun syncCallLog(@Body request: CallLogRequest): Call<GeneralResponse>

    @POST("call-outcome")
    fun syncCallOutcome(@Body request: CallOutcomeRequest): Call<GeneralResponse>

    @Multipart
    @POST("upload-recording")
    fun uploadRecording(
        @Part("deviceId") deviceId: okhttp3.RequestBody,
        @Part("token") token: okhttp3.RequestBody,
        @Part("callId") callId: okhttp3.RequestBody,
        @Part file: MultipartBody.Part
    ): Call<GeneralResponse>
}
