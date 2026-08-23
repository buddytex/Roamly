package com.railguard.mobile.network

import com.google.gson.annotations.SerializedName

/**
 * Common GPS structure for network payloads.
 */
data class NetworkGps(
    @SerializedName("lat") val latitude: Double,
    @SerializedName("lon") val longitude: Double,
    @SerializedName("speed") val speed: Float,
    @SerializedName("alt") val altitude: Double = 0.0
)

/**
 * Payload for periodic heartbeat signal.
 */
data class HeartbeatRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("status") val status: String,
    @SerializedName("gps") val gps: NetworkGps?,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

/**
 * Payload for logging an inspection event (e.g. Anomaly detected).
 */
data class EventRequest(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("event_type") val type: String,
    @SerializedName("severity") val severity: String,
    @SerializedName("message") val message: String,
    @SerializedName("gps") val gps: NetworkGps?,
    @SerializedName("metadata") val metadata: Map<String, String> = emptyMap(),
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

/**
 * Generic response from the Pi server.
 */
data class PiResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String?,
    @SerializedName("command") val command: String? = null
)
