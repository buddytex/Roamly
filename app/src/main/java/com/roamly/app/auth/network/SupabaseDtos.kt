package com.roamly.app.auth.network

import com.google.gson.annotations.SerializedName

data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>? = null
)

data class PasswordLoginRequest(
    val email: String,
    val password: String
)

data class IdTokenLoginRequest(
    val provider: String = "google",
    @SerializedName("id_token") val idToken: String,
    @SerializedName("client_id") val clientId: String? = null,
    val nonce: String? = null
)

data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class RecoverRequest(
    val email: String
)

data class AuthResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("token_type") val tokenType: String? = null,
    @SerializedName("expires_in") val expiresIn: Long? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    val user: UserDto? = null
)

data class UserDto(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    @SerializedName("user_metadata") val userMetadata: Map<String, Any?>? = null,
    @SerializedName("app_metadata") val appMetadata: Map<String, Any?>? = null,
    @SerializedName("email_confirmed_at") val emailConfirmedAt: String? = null,
    @SerializedName("created_at") val createdAt: String? = null
)

data class ProfileDto(
    val id: String,
    @SerializedName("full_name") val fullName: String? = null,
    val role: String? = null,
    @SerializedName("approval_status") val approvalStatus: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
)

data class UpdateProfileRequest(
    @SerializedName("full_name") val fullName: String? = null,
    val phone: String? = null
)

data class SupabaseErrorResponse(
    val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null,
    val message: String? = null,
    val msg: String? = null,
    val code: Int? = null
)
