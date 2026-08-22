package com.railguard.mobile.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface RailGuardApiService {

    @POST("api/heartbeat")
    suspend fun sendHeartbeat(@Body request: HeartbeatRequest): Response<PiResponse>

    @POST("api/events")
    suspend fun logEvent(@Body request: EventRequest): Response<PiResponse>

    @Multipart
    @POST("api/upload")
    suspend fun uploadImage(
        @Part("device_id") deviceId: RequestBody,
        @Part("event_id") eventId: RequestBody?,
        @Part image: MultipartBody.Part
    ): Response<PiResponse>
}
