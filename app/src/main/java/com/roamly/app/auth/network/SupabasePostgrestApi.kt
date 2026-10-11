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
        @Query("select") select: String = "*",
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<List<ProfileDto>>

    @POST("rest/v1/profiles")
    suspend fun insertProfile(
        @Body profile: ProfileDto,
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<Unit>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Query("id") idFilter: String,
        @Body update: UpdateProfileRequest
    ): Response<Unit>

    @GET("rest/v1/demo_sessions")
    suspend fun getDemoSessions(
        @Query("trip_slug") tripSlug: String? = null,
        @Query("order") order: String = "updated_at.desc",
        @Query("select") select: String = "*",
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<List<DemoSessionDto>>

    @POST("rest/v1/demo_sessions")
    suspend fun upsertDemoSession(
        @Body session: DemoSessionDto,
        @retrofit2.http.Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<Unit>

    @POST("rest/v1/demo_events")
    suspend fun insertDemoEvent(
        @Body event: DemoEventDto,
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<Unit>

    @GET("rest/v1/demo_events")
    suspend fun getDemoEvents(
        @Query("traveller_id") travellerIdFilter: String? = null,
        @Query("order") order: String = "event_timestamp.desc",
        @Query("limit") limit: Int = 30,
        @Query("select") select: String = "*",
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<List<DemoEventDto>>

    @GET("rest/v1/profiles")
    suspend fun getTravellerProfiles(
        @Query("role") roleFilter: String = "eq.traveller",
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<List<ProfileDto>>

    @GET("rest/v1/trip_members")
    suspend fun getTripMembers(
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<List<TripMemberDto>>

    @GET("rest/v1/trips")
    suspend fun getTrips(
        @Query("select") select: String = "*",
        @retrofit2.http.Header("Authorization") authHeader: String? = null
    ): Response<List<TripDto>>
}


