package com.roamly.app.auth.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface SupabaseAuthApi {

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Body request: SignUpRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(
        @Body request: PasswordLoginRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=id_token")
    suspend fun signInWithIdToken(
        @Body request: IdTokenLoginRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<AuthResponse>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(
        @Body request: RecoverRequest
    ): Response<Unit>

    @POST("auth/v1/logout")
    suspend fun logout(): Response<Unit>

    @GET("auth/v1/user")
    suspend fun getUser(): Response<UserDto>
}
