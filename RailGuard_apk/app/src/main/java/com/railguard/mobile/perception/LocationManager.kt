package com.railguard.mobile.perception

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import android.util.Log

data class GpsData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val accuracy: Float = 0f,
    val timestamp: Long = 0L,
    val provider: String = "NONE",
    val hasData: Boolean = false,
)

class LocationManager(context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    
    private val _gpsData = MutableStateFlow(GpsData())
    val gpsData: StateFlow<GpsData> = _gpsData.asStateFlow()

    private var lastLocation: Location? = null
    private var smoothedSpeed = 0f

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            var currentSpeed = if (location.hasSpeed()) location.speed else 0f
            
            // GPS Jitter / Drift Filtering
            lastLocation?.let { last ->
                val timeDeltaSeconds = (location.time - last.time) / 1000.0f
                if (timeDeltaSeconds > 0) {
                    val distance = last.distanceTo(location)
                    
                    // DRIFT FILTER: Standard GPS drift when stationary is ~0.5 to 2 meters.
                    // If distance moved is less than the accuracy margin (e.g. 3m), ignore it.
                    val driftThreshold = if (location.accuracy > 0) location.accuracy * 0.6f else 1.5f
                    
                    if (distance > driftThreshold) {
                        val calculatedSpeed = distance / timeDeltaSeconds
                        // Use calculated speed only if hardware speed is zero or inconsistent
                        if (currentSpeed <= 0.2f && calculatedSpeed > 0.2f) {
                            currentSpeed = calculatedSpeed
                        }
                    } else {
                        // Within drift range, hardware speed is likely jitter too if it's low
                        if (currentSpeed < 0.5f) {
                            currentSpeed = 0f
                        }
                    }
                }
            }
            
            // Smoothing Filter (Alpha = 0.4)
            // If currentSpeed is 0, we decay faster (Alpha = 0.8) to reach 0 immediately on stop
            val alpha = if (currentSpeed == 0f) 0.8f else 0.4f
            smoothedSpeed = (smoothedSpeed * (1f - alpha)) + (currentSpeed * alpha)
            
            // Hard threshold to force zero for stationary state (0.3m/s is ~1km/h)
            if (smoothedSpeed < 0.3f) smoothedSpeed = 0f

            lastLocation = location
            
            Log.d("RailGuardGPS", "GPS: Lat=${location.latitude}, Spd=${currentSpeed}m/s, Smoothed=${smoothedSpeed}m/s, Acc=${location.accuracy}m")

            _gpsData.value = GpsData(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                speed = smoothedSpeed,
                accuracy = location.accuracy,
                timestamp = location.time,
                provider = location.provider ?: "GPS",
                hasData = true
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            startUpdates()
        }
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        try {
            // Stop any existing updates first to avoid duplicates
            stopUpdates()

            // Request from GPS for high accuracy (1Hz update rate)
            if (locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    500L, // Request every 500ms
                    0f,   // 0 meters to ensure time-based updates
                    locationListener
                )
            }
            
            // Request from Network as a faster/indoor fallback
            if (locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    AndroidLocationManager.NETWORK_PROVIDER,
                    1000L,
                    0f,
                    locationListener
                )
            }
            
            // Immediately check for last known location to show something
            val lastGps = locationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)
            
            val bestLocation = when {
                lastGps != null && lastNetwork != null -> {
                    if (lastGps.time > lastNetwork.time - 30000) lastGps else lastNetwork
                }
                lastGps != null -> lastGps
                else -> lastNetwork
            }
            
            bestLocation?.let { 
                if (_gpsData.value.timestamp < it.time) {
                    locationListener.onLocationChanged(it)
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopUpdates() {
        locationManager.removeUpdates(locationListener)
    }
}
