package com.roamly.app.auth.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabasePostgrestApi {

    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<ProfileDto>>

    @POST("rest/v1/profiles")
    suspend fun insertProfile(
        @Body profile: ProfileDto
    ): Response<Unit>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Body update: UpdateProfileRequest
    ): Response<Unit>
}
