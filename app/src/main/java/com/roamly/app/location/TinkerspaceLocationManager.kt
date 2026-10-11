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

    val lastFixTimeMillis: Long? = null,

    val isOutdoorCalibrated: Boolean = false,
    val isIndoorCalibrated: Boolean = false,

    val statusMessage: String = "Location idle"
)

class TinkerspaceLocationManager(
    context: Context
) {

    private val appContext = context.applicationContext

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val _locationInfo = MutableStateFlow(
        TinkerspaceLocationInfo()
    )

    val locationInfo: StateFlow<TinkerspaceLocationInfo> =
        _locationInfo.asStateFlow()

    private var activeCallback: LocationCallback? = null

    private var outdoorEventSent = false
    private var indoorEventSent = false

    init {
        TinkerspaceLocationConfig.load(appContext)

        _locationInfo.value = _locationInfo.value.copy(
            isOutdoorCalibrated =
                TinkerspaceLocationConfig.outdoorCalibrated,
            isIndoorCalibrated =
                TinkerspaceLocationConfig.indoorCalibrated
        )
    }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    /**
     * Start receiving actual Android location updates.
     *
     * The manager never invents coordinates. It uses locations returned
     * by the Android location provider.
     */
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
                statusMessage = "Location permission required"
            )
            return
        }

        stopMonitoring()

        outdoorEventSent = false
        indoorEventSent = false

        TinkerspaceLocationConfig.load(appContext)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        val callback = object : LocationCallback() {

            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    processLocation(
                        location = location,
                        onOutdoorArrival = onOutdoorArrival,
                        onIndoorArrival = onIndoorArrival,
                        isOutdoorExpected = isOutdoorExpected,
                        isIndoorExpected = isIndoorExpected
                    )
                }
            }
        }

        activeCallback = callback

        _locationInfo.value = _locationInfo.value.copy(
            hasPermission = true,
            isMonitoring = true,
            isOutdoorCalibrated =
                TinkerspaceLocationConfig.outdoorCalibrated,
            isIndoorCalibrated =
                TinkerspaceLocationConfig.indoorCalibrated,
            statusMessage = "Waiting for a fresh GPS fix..."
        )

        try {
            fusedClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            ).addOnFailureListener { error ->
                _locationInfo.value = _locationInfo.value.copy(
                    isMonitoring = false,
                    statusMessage =
                        "Could not start GPS: ${
                            error.localizedMessage ?: "Unknown error"
                        }"
                )
            }

            // A last-known location may be displayed, but it will not
            // trigger arrival unless it is fresh enough.
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    processLocation(
                        location = location,
                        onOutdoorArrival = onOutdoorArrival,
                        onIndoorArrival = onIndoorArrival,
                        isOutdoorExpected = isOutdoorExpected,
                        isIndoorExpected = isIndoorExpected
                    )
                }
            }.addOnFailureListener {
                _locationInfo.value = _locationInfo.value.copy(
                    statusMessage =
                        "Waiting for a fresh GPS fix..."
                )
            }

        } catch (securityException: SecurityException) {
            _locationInfo.value = _locationInfo.value.copy(
                hasPermission = false,
                isMonitoring = false,
                statusMessage = "Location permission was denied"
            )
        } catch (exception: Exception) {
            _locationInfo.value = _locationInfo.value.copy(
                isMonitoring = false,
                statusMessage =
                    "Could not start GPS: ${
                        exception.localizedMessage ?: "Unknown error"
                    }"
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
        TinkerspaceLocationConfig.load(appContext)

        val now = System.currentTimeMillis()
        val ageMillis = now - location.time

        val isFresh = ageMillis in 0..15_000L
        val hasAccuracy = location.hasAccuracy()
        val accuracy = if (hasAccuracy) location.accuracy else null

        val distanceToOutdoor = if (
            TinkerspaceLocationConfig.outdoorCalibrated
        ) {
            distanceBetween(
                location,
                TinkerspaceLocationConfig.outdoorLatitude,
                TinkerspaceLocationConfig.outdoorLongitude
            )
        } else {
            null
        }

        val distanceToIndoor = if (
            TinkerspaceLocationConfig.indoorCalibrated
        ) {
            distanceBetween(
                location,
                TinkerspaceLocationConfig.indoorLatitude,
                TinkerspaceLocationConfig.indoorLongitude
            )
        } else {
            null
        }

        val status = when {
            !isFresh ->
                "GPS fix is stale; waiting for a fresh reading"

            !hasAccuracy ->
                "GPS accuracy unavailable"

            accuracy != null &&
                accuracy > TinkerspaceLocationConfig.REQUIRED_ACCURACY_METERS ->
                "Accuracy too low: ±${accuracy.toInt()} m"

            else ->
                "GPS tracking active"
        }

        _locationInfo.value = _locationInfo.value.copy(
            hasPermission = true,
            isMonitoring = activeCallback != null,

            lastLatitude = location.latitude,
            lastLongitude = location.longitude,

            accuracyMeters = accuracy,

            distanceToOutdoorMeters = distanceToOutdoor,
            distanceToIndoorMeters = distanceToIndoor,

            lastFixTimeMillis = location.time,

            isOutdoorCalibrated =
                TinkerspaceLocationConfig.outdoorCalibrated,
            isIndoorCalibrated =
                TinkerspaceLocationConfig.indoorCalibrated,

            statusMessage = status
        )

        // Do not use old or inaccurate readings to change trip state.
        if (!isFresh || !hasAccuracy) return

        if (
            accuracy != null &&
            accuracy > TinkerspaceLocationConfig.REQUIRED_ACCURACY_METERS
        ) {
            return
        }

        // Outdoor destination: only trigger after explicit calibration.
        if (
            isOutdoorExpected &&
            !outdoorEventSent &&
            TinkerspaceLocationConfig.outdoorCalibrated &&
            distanceToOutdoor != null &&
            distanceToOutdoor <=
                TinkerspaceLocationConfig.outdoorRadiusMeters
        ) {
            outdoorEventSent = true
            onOutdoorArrival()
            return
        }

        // Indoor destination: also requires explicit calibration.
        if (
            isIndoorExpected &&
            !indoorEventSent &&
            TinkerspaceLocationConfig.indoorCalibrated &&
            distanceToIndoor != null &&
            distanceToIndoor <=
                TinkerspaceLocationConfig.indoorRadiusMeters
        ) {
            indoorEventSent = true
            onIndoorArrival()
        }
    }

    private fun distanceBetween(
        location: Location,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): Float {
        val result = FloatArray(1)

        Location.distanceBetween(
            location.latitude,
            location.longitude,
            destinationLatitude,
            destinationLongitude,
            result
        )

        return result[0]
    }

    fun stopMonitoring() {
        activeCallback?.let { callback ->
            try {
                fusedClient.removeLocationUpdates(callback)
            } catch (_: Exception) {
                // Cleanup must not crash the UI.
            }
        }

        activeCallback = null

        _locationInfo.value = _locationInfo.value.copy(
            isMonitoring = false,
            statusMessage = if (_locationInfo.value.lastLatitude != null) {
                "GPS tracking paused"
            } else {
                "Location monitoring idle"
            }
        )
    }
}