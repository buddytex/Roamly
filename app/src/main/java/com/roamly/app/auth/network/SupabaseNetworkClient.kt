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
            val statusCode = response.code()
            val retryAfterHeader = response.headers().get("Retry-After")?.trim()
            val retryAfterSeconds = retryAfterHeader?.toLongOrNull()

            val waitGuidance = when {
                retryAfterSeconds != null && retryAfterSeconds > 60 -> {
                    val minutes = (retryAfterSeconds + 59) / 60
                    "Please wait $minutes minute${if (minutes > 1L) "s" else ""} before trying again."
                }
                retryAfterSeconds != null && retryAfterSeconds > 0 -> {
                    "Please wait $retryAfterSeconds seconds before trying again."
                }
                else -> null
            }

            val errorBody = response.errorBody()?.string()
            val parsed = if (!errorBody.isNullOrBlank()) {
                try {
                    gson.fromJson(errorBody, SupabaseErrorResponse::class.java)
                } catch (e: Exception) {
                    null
                }
            } else null

            val rawMessage = parsed?.errorDescription
                ?: parsed?.message
                ?: parsed?.msg
                ?: parsed?.error
                ?: ""

            val errorCode = parsed?.errorCode ?: ""

            val isRateLimit = statusCode == 429 ||
                rawMessage.contains("rate limit", ignoreCase = true) ||
                rawMessage.contains("rate_limit", ignoreCase = true) ||
                rawMessage.contains("over_email_send_rate_limit", ignoreCase = true) ||
                errorCode.contains("rate_limit", ignoreCase = true) ||
                errorCode.contains("over_email_send_rate_limit", ignoreCase = true)

            if (isRateLimit) {
                val isEmailRateLimit = errorCode.contains("email", ignoreCase = true) ||
                    rawMessage.contains("email", ignoreCase = true) ||
                    errorCode.contains("over_email_send_rate_limit", ignoreCase = true)

                return when {
                    isEmailRateLimit -> {
                        if (waitGuidance != null) {
                            "Email delivery limit reached. $waitGuidance"
                        } else {
                            "Email delivery rate limit reached. Please wait a few minutes before trying again."
                        }
                    }
                    waitGuidance != null -> {
                        "Too many requests. $waitGuidance"
                    }
                    rawMessage.contains("once every", ignoreCase = true) -> {
                        "For security purposes, please wait a moment before trying again."
                    }
                    else -> {
                        "Too many requests. Please wait a moment and try again."
                    }
                }
            }

            if (rawMessage.isNotBlank()) {
                sanitizeMessage(rawMessage)
            } else {
                "Request failed with status code $statusCode."
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
            lower.contains("user already registered") ||
            lower.contains("already registered") ||
            lower.contains("user_already_exists") ||
            lower.contains("user already exists") ||
            lower.contains("email address already in use") ||
            lower.contains("email already exists") ||
            lower.contains("email already in use") ->
                "An account with this email address already exists. Please sign in instead."
            lower.contains("password should be at least") ->
                "Password must be at least 6 characters long."
            lower.contains("rate limit") ->
                "Too many requests. Please wait a moment and try again."
            else -> raw
        }
    }
}
