package com.railguard.mobile.network

import android.content.Context
import android.provider.Settings
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages networking with the RailGuard Pi server.
 */
class NetworkManager(private val context: Context) {

    companion object {
        // Centralized Pi server configuration
        // IMPORTANT: Retrofit base URL must end with a trailing slash /
        private const val BASE_URL = "http://10.240.194.204:8000/"
    }

    private val _isServerOnline = MutableStateFlow(false)
    val isServerOnline: StateFlow<Boolean> = _isServerOnline.asStateFlow()

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private val apiService: RailGuardApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .proxy(java.net.Proxy.NO_PROXY) // Ignore system proxies which might interfere
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(RailGuardApiService::class.java)
    }

    /**
     * Sends a heartbeat with current status and GPS.
     * Returns the response from the server which may contain commands.
     */
    suspend fun sendHeartbeat(status: String, gps: NetworkGps?): PiResponse? {
        return try {
            val request = HeartbeatRequest(
                deviceId = deviceId,
                status = status,
                gps = gps
            )
            val response = apiService.sendHeartbeat(request)
            val isSuccess = response.isSuccessful
            _isServerOnline.value = isSuccess
            
            if (isSuccess) {
                response.body()
            } else {
                null
            }
        } catch (e: Exception) {
            _isServerOnline.value = false
            e.printStackTrace()
            null
        }
    }

    /**
     * Logs an inspection event to the server.
     */
    suspend fun logEvent(
        type: String, 
        severity: String, 
        message: String, 
        gps: NetworkGps?, 
        metadata: Map<String, String> = emptyMap()
    ) {
        try {
            val request = EventRequest(
                deviceId = deviceId,
                type = type,
                severity = severity,
                message = message,
                gps = gps,
                metadata = metadata
            )
            val response = apiService.logEvent(request)
            _isServerOnline.value = response.isSuccessful
        } catch (e: Exception) {
            _isServerOnline.value = false
            e.printStackTrace()
        }
    }

    /**
     * Uploads an image captured during an anomaly.
     */
    suspend fun uploadAnomalyImage(imageFile: File, eventId: String? = null) {
        try {
            val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaTypeOrNull())
            val eventIdBody = eventId?.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val requestFile = imageFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)

            val response = apiService.uploadImage(deviceIdBody, eventIdBody, body)
            _isServerOnline.value = response.isSuccessful
        } catch (e: Exception) {
            _isServerOnline.value = false
            e.printStackTrace()
        }
    }

    /**
     * Tests the connection to the server and returns a descriptive status/error.
     */
    suspend fun testConnection(): String {
        return try {
            val request = HeartbeatRequest(
                deviceId = deviceId,
                status = "PING",
                gps = null
            )
            val response = apiService.sendHeartbeat(request)
            _isServerOnline.value = response.isSuccessful
            if (response.isSuccessful) {
                "SUCCESS: Server reached (${response.code()})"
            } else {
                "ERROR: Server returned ${response.code()} - ${response.errorBody()?.string()}"
            }
        } catch (e: Exception) {
            _isServerOnline.value = false
            e.printStackTrace()
            "FAILED: ${e.localizedMessage ?: e.message ?: "Unknown network error"}"
        }
    }
}
