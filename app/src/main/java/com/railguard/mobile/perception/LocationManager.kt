package com.railguard.mobile.perception

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private val appContext = context.applicationContext
    private val legacyLocationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    
    private val _gpsData = MutableStateFlow(GpsData())
    val gpsData: StateFlow<GpsData> = _gpsData.asStateFlow()

    private var lastLocation: Location? = null
    private var smoothedSpeed = 0f

    private var pollingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        try {
            startUpdates()
        } catch (e: Exception) {
            Log.w("RailGuardGPS", "Auto startUpdates in init failed", e)
        }
    }

    fun handleNewLocation(location: Location, providerTag: String) {
        if (location.latitude == 0.0 && location.longitude == 0.0) return

        var currentSpeed = if (location.hasSpeed()) location.speed else 0f
        
        lastLocation?.let { last ->
            val timeDeltaSeconds = (location.time - last.time) / 1000.0f
            if (timeDeltaSeconds > 0) {
                val distance = last.distanceTo(location)
                val driftThreshold = if (location.accuracy > 0) location.accuracy * 0.6f else 1.5f
                if (distance > driftThreshold) {
                    val calculatedSpeed = distance / timeDeltaSeconds
                    if (currentSpeed <= 0.2f && calculatedSpeed > 0.2f) {
                        currentSpeed = calculatedSpeed
                    }
                } else {
                    if (currentSpeed < 0.5f) currentSpeed = 0f
                }
            }
        }
        
        val alpha = if (currentSpeed == 0f) 0.8f else 0.4f
        smoothedSpeed = (smoothedSpeed * (1f - alpha)) + (currentSpeed * alpha)
        if (smoothedSpeed < 0.3f) smoothedSpeed = 0f

        lastLocation = location
        Log.i("RailGuardGPS", "[$providerTag] Lat=${location.latitude}, Lon=${location.longitude}, Acc=${location.accuracy}m")

        _gpsData.value = GpsData(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = smoothedSpeed,
            accuracy = location.accuracy,
            timestamp = location.time,
            provider = providerTag,
            hasData = true
        )
    }

    fun simulateLocation() {
        val loc = Location("MANUAL").apply {
            latitude = 12.971598
            longitude = 77.594562
            altitude = 920.0
            speed = 7.2f
            accuracy = 2.5f
            time = System.currentTimeMillis()
        }
        handleNewLocation(loc, "SIMULATED")
    }

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                handleNewLocation(location, "FUSED")
            }
        }
        override fun onLocationAvailability(avail: LocationAvailability) {
            Log.d("RailGuardGPS", "Fused availability: isAvailable=${avail.isLocationAvailable}")
        }
    }

    private val legacyListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location, location.provider ?: "GPS")
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        try {
            stopUpdates()

            // 1. Immediately poll all caches (Fused + System)
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) handleNewLocation(loc, "FUSED_CACHE")
            }

            for (p in legacyLocationManager.allProviders) {
                try {
                    val loc = legacyLocationManager.getLastKnownLocation(p)
                    if (loc != null && !_gpsData.value.hasData) handleNewLocation(loc, p)
                } catch (e: Exception) {}
            }

            // 2. Fused Location Real-time Stream (Fast 1s interval)
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                fusedCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Log.d("RailGuardGPS", "Fused location stream registered")
            }.addOnFailureListener { e ->
                Log.w("RailGuardGPS", "Fused location stream failed", e)
            }

            // 3. System LocationManager Real-time Streams (GPS + Network + Passive)
            val legacyProviders = listOf(
                AndroidLocationManager.GPS_PROVIDER,
                AndroidLocationManager.NETWORK_PROVIDER,
                AndroidLocationManager.PASSIVE_PROVIDER
            )
            for (provider in legacyProviders) {
                try {
                    if (legacyLocationManager.isProviderEnabled(provider)) {
                        legacyLocationManager.requestLocationUpdates(
                            provider,
                            500L,
                            0f,
                            legacyListener,
                            Looper.getMainLooper()
                        )
                    }
                } catch (e: Exception) {}
            }

            // 4. Active Poller every 2s until first coordinates locked
            pollingJob = coroutineScope.launch {
                while (isActive) {
                    if (!_gpsData.value.hasData) {
                        try {
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                                .addOnSuccessListener { loc -> if (loc != null) handleNewLocation(loc, "NETWORK") }
                            
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { loc -> if (loc != null) handleNewLocation(loc, "GPS") }
                        } catch (e: Exception) {}
                    }
                    delay(2000L)
                }
            }

        } catch (e: Exception) {
            Log.e("RailGuardGPS", "Error in startUpdates", e)
        }
    }

    fun stopUpdates() {
        try {
            pollingJob?.cancel()
            pollingJob = null
            fusedLocationClient.removeLocationUpdates(fusedCallback)
            legacyLocationManager.removeUpdates(legacyListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
