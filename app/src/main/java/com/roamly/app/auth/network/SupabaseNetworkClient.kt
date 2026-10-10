package com.roamly.app.auth.network

import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SupabaseNetworkClient(
    baseUrl: String,
    private val anonKey: String,
    private val tokenProvider: () -> String?
) {

    private val gson = Gson()
    private val cleanBaseUrl: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("apikey", anonKey)

        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        } else {
            requestBuilder.header("Authorization", "Bearer $anonKey")
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Redact authorization and apikey headers to protect tokens and prevent credential leaks
        level = HttpLoggingInterceptor.Level.BASIC
        redactHeader("Authorization")
        redactHeader("apikey")
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(cleanBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val authApi: SupabaseAuthApi = retrofit.create(SupabaseAuthApi::class.java)
    val postgrestApi: SupabasePostgrestApi = retrofit.create(SupabasePostgrestApi::class.java)

    fun parseErrorMessage(response: Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                val parsed = gson.fromJson(errorBody, SupabaseErrorResponse::class.java)
                when {
                    !parsed.errorDescription.isNullOrBlank() -> sanitizeMessage(parsed.errorDescription)
                    !parsed.message.isNullOrBlank() -> sanitizeMessage(parsed.message)
                    !parsed.msg.isNullOrBlank() -> sanitizeMessage(parsed.msg)
                    !parsed.error.isNullOrBlank() -> sanitizeMessage(parsed.error)
                    else -> "Authentication failed (${response.code()}). Please try again."
                }
            } else {
                "Request failed with status code ${response.code()}."
            }
        } catch (e: Exception) {
            "An unexpected network error occurred. Please check your connection."
        }
    }

    private fun sanitizeMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("invalid login credentials") || lower.contains("invalid_grant") ->
                "Invalid email or password. Please check your credentials."
            lower.contains("email not confirmed") ->
                "Your email address has not been verified. Please check your inbox for the confirmation link."
            lower.contains("user already registered") ->
                "An account with this email address already exists. Please sign in instead."
            lower.contains("password should be at least") ->
                "Password must be at least 6 characters long."
            lower.contains("rate limit") ->
                "Too many requests. Please wait a moment and try again."
            else -> raw
        }
    }
}
