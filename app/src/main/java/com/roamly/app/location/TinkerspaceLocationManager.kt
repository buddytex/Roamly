package com.roamly.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TinkerspaceLocationInfo(
    val hasPermission: Boolean = false,
    val isMonitoring: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val accuracyMeters: Float? = null,
    val distanceToOutdoorMeters: Float? = null,
    val distanceToIndoorMeters: Float? = null,
    val statusMessage: String = "Location idle"
)

class TinkerspaceLocationManager(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _locationInfo = MutableStateFlow(TinkerspaceLocationInfo())
    val locationInfo: StateFlow<TinkerspaceLocationInfo> = _locationInfo.asStateFlow()

    private var activeCallback: LocationCallback? = null

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring(
        onOutdoorArrival: () -> Unit,
        onIndoorArrival: () -> Unit,
        isOutdoorExpected: Boolean,
        isIndoorExpected: Boolean
    ) {
        if (!hasLocationPermission()) {
            _locationInfo.value = _locationInfo.value.copy(
                hasPermission = false,
                isMonitoring = false,
                statusMessage = "Location permission not granted"
            )
            return
        }

        stopMonitoring()

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .setMinUpdateDistanceMeters(2.0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                processLocation(loc, onOutdoorArrival, onIndoorArrival, isOutdoorExpected, isIndoorExpected)
            }
        }

        activeCallback = callback

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            _locationInfo.value = _locationInfo.value.copy(
                hasPermission = true,
                isMonitoring = true,
                statusMessage = "Monitoring GPS for arrival..."
            )
            // Also query last known location immediately
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    processLocation(loc, onOutdoorArrival, onIndoorArrival, isOutdoorExpected, isIndoorExpected)
                }
            }
        } catch (e: Exception) {
            _locationInfo.value = _locationInfo.value.copy(
                isMonitoring = false,
                statusMessage = "Could not start GPS: ${e.localizedMessage ?: "Unknown error"}"
            )
        }
    }

    private fun processLocation(
        location: Location,
        onOutdoorArrival: () -> Unit,
        onIndoorArrival: () -> Unit,
        isOutdoorExpected: Boolean,
        isIndoorExpected: Boolean
    ) {
        val distOutdoor = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            TinkerspaceLocationConfig.outdoorLatitude,
            TinkerspaceLocationConfig.outdoorLongitude,
            distOutdoor
        )
        val distanceToOutdoor = distOutdoor[0]

        val distIndoor = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            TinkerspaceLocationConfig.indoorLatitude,
            TinkerspaceLocationConfig.indoorLongitude,
            distIndoor
        )
        val distanceToIndoor = distIndoor[0]

        _locationInfo.value = _locationInfo.value.copy(
            hasPermission = true,
            isMonitoring = true,
            lastLatitude = location.latitude,
            lastLongitude = location.longitude,
            accuracyMeters = location.accuracy,
            distanceToOutdoorMeters = distanceToOutdoor,
            distanceToIndoorMeters = distanceToIndoor,
            statusMessage = "GPS active (Accuracy ±${location.accuracy.toInt()}m)"
        )

        // Accuracy check: if accuracy is too low, avoid triggering false positives
        if (location.hasAccuracy() && location.accuracy > TinkerspaceLocationConfig.REQUIRED_ACCURACY_METERS) {
            return
        }

        // Check Outdoor arrival zone
        if (isOutdoorExpected && distanceToOutdoor <= TinkerspaceLocationConfig.outdoorRadiusMeters) {
            onOutdoorArrival()
            return
        }

        // Check Indoor return zone
        if (isIndoorExpected && distanceToIndoor <= TinkerspaceLocationConfig.indoorRadiusMeters) {
            onIndoorArrival()
            return
        }
    }

    fun stopMonitoring() {
        activeCallback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                // Safe ignore
            }
        }
        activeCallback = null
        _locationInfo.value = _locationInfo.value.copy(
            isMonitoring = false,
            statusMessage = "Location monitoring idle"
        )
    }
}
