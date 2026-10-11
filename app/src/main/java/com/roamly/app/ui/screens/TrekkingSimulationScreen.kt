package com.roamly.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlySuccess
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary
import kotlin.math.abs
import kotlin.math.sqrt

private enum class TrekStatus {
    READY,
    RECORDING,
    CLIMB_DETECTED,
    FINISHED
}

@Composable
fun TrekkingSimulationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE)
            as SensorManager
    }

    val locationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var status by remember {
        mutableStateOf(TrekStatus.READY)
    }

    var hasPermission by remember {
        mutableStateOf(locationPermissionGranted(context))
    }

    var steps by remember {
        mutableIntStateOf(0)
    }

    var lastStepTime by remember {
        mutableLongStateOf(0L)
    }

    var previousAcceleration by remember {
        mutableStateOf(0f)
    }

    var filteredAcceleration by remember {
        mutableStateOf(0f)
    }

    var startAltitude by remember {
        mutableStateOf<Double?>(null)
    }

    var currentAltitude by remember {
        mutableStateOf<Double?>(null)
    }

    var maxAltitude by remember {
        mutableStateOf<Double?>(null)
    }

    var verticalAccuracy by remember {
        mutableStateOf<Float?>(null)
    }

    var gpsStatus by remember {
        mutableStateOf("Waiting for GPS")
    }

    var message by remember {
        mutableStateOf("Start the trek and walk normally.")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission =
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        gpsStatus = if (hasPermission) {
            "Waiting for GPS"
        } else {
            "Location permission denied"
        }
    }

    val elevationGain =
        if (startAltitude != null && currentAltitude != null) {
            currentAltitude!! - startAltitude!!
        } else {
            null
        }

    /*
     * GPS altitude is displayed independently from step detection.
     * It is not used as the only indication of movement.
     */
    DisposableEffect(status, hasPermission) {
        if (
            status != TrekStatus.RECORDING ||
            !hasPermission
        ) {
            onDispose { }
        } else {
            var smoothedAltitude: Double? = null

            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L
            )
                .setMinUpdateIntervalMillis(1000L)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(
                    result: LocationResult
                ) {
                    val location: Location =
                        result.lastLocation ?: return

                    if (
                        location.hasAccuracy() &&
                        location.accuracy > 50f
                    ) {
                        gpsStatus = "GPS accuracy is poor"
                        return
                    }

                    smoothedAltitude =
                        if (smoothedAltitude == null) {
                            location.altitude
                        } else {
                            smoothedAltitude!! * 0.8 +
                                location.altitude * 0.2
                        }

                    val altitude = smoothedAltitude!!

                    currentAltitude = altitude

                    maxAltitude = maxOf(
                        maxAltitude ?: altitude,
                        altitude
                    )

                    if (startAltitude == null) {
                        startAltitude = altitude
                    }

                    verticalAccuracy =
                        if (
                            android.os.Build.VERSION.SDK_INT >= 26 &&
                            location.hasVerticalAccuracy()
                        ) {
                            location.verticalAccuracyMeters
                        } else {
                            null
                        }

                    gpsStatus = "Receiving GPS readings"
                }
            }

            try {
                locationClient.requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper()
                )
            } catch (_: SecurityException) {
                gpsStatus = "Location permission unavailable"
            }

            onDispose {
                locationClient.removeLocationUpdates(callback)
            }
        }
    }

    /*
     * Restore the original, simpler accelerometer step detector.
     */
    DisposableEffect(status) {
        if (status != TrekStatus.RECORDING) {
            onDispose { }
        } else {
            val accelerometer =
                sensorManager.getDefaultSensor(
                    Sensor.TYPE_ACCELEROMETER
                )

            val listener = object : SensorEventListener {
                override fun onSensorChanged(
                    event: SensorEvent
                ) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val magnitude = sqrt(
                        x * x + y * y + z * z
                    )

                    val movement = abs(
                        magnitude - SensorManager.GRAVITY_EARTH
                    )

                    filteredAcceleration =
                        filteredAcceleration * 0.8f +
                            movement * 0.2f

                    val now = System.currentTimeMillis()

                    val crossedThreshold =
                        previousAcceleration < 1.15f &&
                            filteredAcceleration >= 1.15f

                    if (
                        crossedThreshold &&
                        now - lastStepTime > 300L &&
                        filteredAcceleration < 8f
                    ) {
                        steps++
                        lastStepTime = now
                    }

                    previousAcceleration = filteredAcceleration

                    if (steps >= 8 && status == TrekStatus.RECORDING) {
                        message =
                            "Walking movement detected. GPS altitude is reported separately."
                    }
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int
                ) = Unit
            }

            if (accelerometer != null) {
                sensorManager.registerListener(
                    listener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
                )
            } else {
                message = "Accelerometer unavailable on this phone."
            }

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    fun startTrek() {
        steps = 0
        lastStepTime = 0L
        previousAcceleration = 0f
        filteredAcceleration = 0f

        startAltitude = null
        currentAltitude = null
        maxAltitude = null
        verticalAccuracy = null

        gpsStatus = if (hasPermission) {
            "Waiting for GPS"
        } else {
            "Location permission not granted"
        }

        message = "Walk normally while carrying your phone."

        status = TrekStatus.RECORDING
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Trekking Simulation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = RoamlyTextPrimary
        )

        Text(
            text = "Phone movement and GPS altitude test.",
            color = RoamlyTextSecondary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = RoamlySurface
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when (status) {
                        TrekStatus.READY -> "READY TO START"
                        TrekStatus.RECORDING -> "TREK IN PROGRESS"
                        TrekStatus.CLIMB_DETECTED -> "POSSIBLE ASCENT"
                        TrekStatus.FINISHED -> "SIMULATION FINISHED"
                    },
                    color = if (
                        status == TrekStatus.CLIMB_DETECTED
                    ) {
                        RoamlySuccess
                    } else {
                        RoamlyPrimary
                    },
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = message,
                    color = RoamlyTextPrimary
                )

                MetricRow("Steps detected", steps.toString())

                MetricRow(
                    "Starting altitude",
                    startAltitude?.let { "%.1f m".format(it) }
                        ?: "Waiting for GPS"
                )

                MetricRow(
                    "Current altitude",
                    currentAltitude?.let { "%.1f m".format(it) }
                        ?: "Waiting for GPS"
                )

                MetricRow(
                    "Elevation change",
                    elevationGain?.let { "%+.1f m".format(it) }
                        ?: "Unavailable"
                )

                MetricRow(
                    "Maximum altitude",
                    maxAltitude?.let { "%.1f m".format(it) }
                        ?: "Unavailable"
                )

                MetricRow(
                    "Vertical accuracy",
                    verticalAccuracy?.let { "±%.1f m".format(it) }
                        ?: "Not reported"
                )

                MetricRow("GPS status", gpsStatus)
            }
        }

        if (!hasPermission) {
            Text(
                text = "Location permission is needed for GPS altitude. Step counting can work without it.",
                color = RoamlyTextSecondary
            )

            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Location Permission")
            }
        }

        when (status) {
            TrekStatus.READY -> {
                Button(
                    onClick = {
                        if (!hasPermission) {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }

                        startTrek()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoamlyPrimary
                    )
                ) {
                    Text("Start Trek")
                }
            }

            TrekStatus.RECORDING -> {
                OutlinedButton(
                    onClick = {
                        status = TrekStatus.FINISHED
                        message = "Trek finished."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finish Trek")
                }
            }

            TrekStatus.CLIMB_DETECTED -> {
                Button(
                    onClick = {
                        status = TrekStatus.FINISHED
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Finish Simulation")
                }

                OutlinedButton(
                    onClick = { startTrek() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try Again")
                }
            }

            TrekStatus.FINISHED -> {
                Button(
                    onClick = { startTrek() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Again")
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Trips")
        }

        Text(
            text = "GPS altitude can be inaccurate indoors. This screen reports altitude separately from movement and does not claim to verify that a staircase was climbed.",
            color = RoamlyTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = RoamlyTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
    )

        Text(
            text = value,
            color = RoamlyTextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun locationPermissionGranted(
    context: Context
): Boolean {
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