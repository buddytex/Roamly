package com.roamly.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.data.SampleData
import com.roamly.app.location.TinkerspaceLocationConfig
import com.roamly.app.location.TinkerspaceLocationManager
import com.roamly.app.model.TinkerspaceWorkflowState
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySuccess
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlySurfaceVariant
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TinkerspaceDemoScreen(
    repository: RoamlyRepository,
    onBack: () -> Unit,
    onReviewChecklist: () -> Unit = {}
) {
    val context = LocalContext.current

    val workflowState by repository.tinkerspaceWorkflowState.collectAsState()
    val belongingsCheckedTime by repository.tinkerspaceBelongingsCheckedTime.collectAsState()

    val locationManager = remember {
        TinkerspaceLocationManager(context)
    }

    val locationInfo by locationManager.locationInfo.collectAsState()

    androidx.compose.runtime.LaunchedEffect(locationInfo.hasPermission, locationInfo.isMonitoring) {
        if (workflowState != TinkerspaceWorkflowState.UPCOMING && workflowState != TinkerspaceWorkflowState.COMPLETED) {
            if (!locationInfo.hasPermission) {
                repository.recordLocationAvailability(false, "Location permission missing")
            } else if (locationInfo.isMonitoring) {
                repository.recordLocationAvailability(true, "GPS location active")
            }
        }
    }


    var showOutdoorArrivalDialog by remember {
        mutableStateOf(false)
    }

    var showIndoorArrivalDialog by remember {
        mutableStateOf(false)
    }

    val checkedBelongings = remember {
        mutableStateMapOf<String, Boolean>().apply {
            SampleData.belongingDepartureItems
                .take(5)
                .forEach { put(it, false) }
        }
    }

    val tripPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                repository.startTinkerspaceTrip()

                locationManager.startMonitoring(
                    onOutdoorArrival = {
                        repository.reachTinkerspaceOutdoorStop(
                            isSimulated = false
                        )
                        showOutdoorArrivalDialog = true
                    },
                    onIndoorArrival = {
                        repository.reachTinkerspaceIndoorStop(
                            isSimulated = false
                        )
                        showIndoorArrivalDialog = true
                    },
                    isOutdoorExpected =
                        workflowState == TinkerspaceWorkflowState.IN_PROGRESS,
                    isIndoorExpected =
                        workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
                )
            }
        }

    val calibrationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val granted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                locationManager.startMonitoring(
                    onOutdoorArrival = {
                        repository.reachTinkerspaceOutdoorStop(
                            isSimulated = false
                        )
                        showOutdoorArrivalDialog = true
                    },
                    onIndoorArrival = {
                        repository.reachTinkerspaceIndoorStop(
                            isSimulated = false
                        )
                        showIndoorArrivalDialog = true
                    },
                    isOutdoorExpected =
                        workflowState == TinkerspaceWorkflowState.IN_PROGRESS,
                    isIndoorExpected =
                        workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
                )
            }
        }

    DisposableEffect(workflowState) {
        if (locationManager.hasLocationPermission()) {
            locationManager.startMonitoring(
                onOutdoorArrival = {
                    repository.reachTinkerspaceOutdoorStop(
                        isSimulated = false
                    )
                    showOutdoorArrivalDialog = true
                },
                onIndoorArrival = {
                    repository.reachTinkerspaceIndoorStop(
                        isSimulated = false
                    )
                    showIndoorArrivalDialog = true
                },
                isOutdoorExpected =
                    workflowState == TinkerspaceWorkflowState.IN_PROGRESS,
                isIndoorExpected =
                    workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
            )
        }

        onDispose {
            locationManager.stopMonitoring()
        }
    }

    if (showOutdoorArrivalDialog) {
        AlertDialog(
            onDismissRequest = {
                showOutdoorArrivalDialog = false
            },
            title = {
                Text(
                    text = "You've Arrived!",
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "You've reached the outside Tinkerspace destination.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextPrimary
                    )

                    Text(
                        text = "Next destination: Back Inside Tinkerspace Building",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )

                    StatusChip(
                        text = "1 of 2 locations reached",
                        backgroundColor = RoamlyPrimaryContainer,
                        textColor = RoamlyPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOutdoorArrivalDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoamlyPrimary
                    )
                ) {
                    Text("Continue Journey")
                }
            }
        )
    }

    if (showIndoorArrivalDialog) {
        AlertDialog(
            onDismissRequest = {
                showIndoorArrivalDialog = false
            },
            title = {
                Text(
                    text = "Welcome Back!",
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "You've reached the final destination.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextPrimary
                    )

                    Text(
                        text = "Your trip is complete!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = RoamlySuccess
                    )

                    StatusChip(
                        text = "2 of 2 locations reached",
                        backgroundColor = RoamlyPrimaryContainer,
                        textColor = RoamlyPrimary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showIndoorArrivalDialog = false
                        repository.proceedToBelongingsCheck()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoamlyPrimary
                    )
                ) {
                    Text("Check Belongings")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RoamlySurface)
                        .border(
                            1.dp,
                            RoamlyBorder,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to My Trips",
                        tint = RoamlyTextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Outside Tinkerspace",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )

                    Text(
                        text = "GPS Location Demonstration",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RoamlySurface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        RoamlyBorder,
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Short Trip",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )

                        val statusLabel = when (workflowState) {
                            TinkerspaceWorkflowState.UPCOMING -> "Upcoming"
                            TinkerspaceWorkflowState.COMPLETED -> "Completed"
                            else -> "In Progress"
                        }

                        StatusChip(
                            text = statusLabel,
                            backgroundColor =
                                if (workflowState == TinkerspaceWorkflowState.COMPLETED) {
                                    RoamlySuccess.copy(alpha = 0.15f)
                                } else {
                                    RoamlyPrimaryContainer
                                },
                            textColor =
                                if (workflowState == TinkerspaceWorkflowState.COMPLETED) {
                                    RoamlySuccess
                                } else {
                                    RoamlyPrimary
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Roamly uses your phone's real GPS position to detect when you reach the configured destinations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    val progressRatio = when (workflowState) {
                        TinkerspaceWorkflowState.UPCOMING -> 0f
                        TinkerspaceWorkflowState.IN_PROGRESS -> 0f
                        TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED -> 0.5f
                        TinkerspaceWorkflowState.INDOOR_STOP_REACHED,
                        TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK,
                        TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION,
                        TinkerspaceWorkflowState.COMPLETED -> 1f
                    }

                    val progressText = when (workflowState) {
                        TinkerspaceWorkflowState.UPCOMING,
                        TinkerspaceWorkflowState.IN_PROGRESS ->
                            "0 of 2 locations reached"

                        TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED ->
                            "1 of 2 locations reached"

                        else -> "2 of 2 locations reached"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trip Progress",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RoamlyTextSecondary
                        )

                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = RoamlyPrimary,
                        trackColor = RoamlyBorder
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Route",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val outdoorReached =
                        workflowState != TinkerspaceWorkflowState.UPCOMING &&
                            workflowState != TinkerspaceWorkflowState.IN_PROGRESS

                    StopItemRow(
                        stopNumber = 1,
                        stopName = "Outside Tinkerspace Building",
                        stopDesc = "Reach the calibrated outdoor GPS destination.",
                        isReached = outdoorReached,
                        isActive = workflowState == TinkerspaceWorkflowState.IN_PROGRESS
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val indoorReached =
                        workflowState == TinkerspaceWorkflowState.INDOOR_STOP_REACHED ||
                            workflowState == TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK ||
                            workflowState == TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION ||
                            workflowState == TinkerspaceWorkflowState.COMPLETED

                    StopItemRow(
                        stopNumber = 2,
                        stopName = "Back Inside Tinkerspace Building",
                        stopDesc = "Return to the separately calibrated indoor destination.",
                        isReached = indoorReached,
                        isActive = workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RoamlySurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        RoamlyBorder,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Live GPS Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                    }

                    Text(
                        text = locationInfo.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    DiagnosticRow(
                        label = "Latitude",
                        value = locationInfo.lastLatitude?.let {
                            String.format(Locale.US, "%.6f", it)
                        } ?: "Waiting for GPS"
                    )

                    DiagnosticRow(
                        label = "Longitude",
                        value = locationInfo.lastLongitude?.let {
                            String.format(Locale.US, "%.6f", it)
                        } ?: "Waiting for GPS"
                    )

                    DiagnosticRow(
                        label = "GPS accuracy",
                        value = locationInfo.accuracyMeters?.let {
                            String.format(Locale.US, "±%.1f m", it)
                        } ?: "Unavailable"
                    )

                    DiagnosticRow(
                        label = "Outdoor distance",
                        value = locationInfo.distanceToOutdoorMeters?.let {
                            String.format(Locale.US, "%.1f m", it)
                        } ?: "Not calibrated"
                    )

                    DiagnosticRow(
                        label = "Indoor distance",
                        value = locationInfo.distanceToIndoorMeters?.let {
                            String.format(Locale.US, "%.1f m", it)
                        } ?: "Not calibrated"
                    )

                    DiagnosticRow(
                        label = "Geofence radius",
                        value = "${TinkerspaceLocationConfig.DEFAULT_GEOFENCE_RADIUS_METERS.toInt()} m"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (!locationManager.hasLocationPermission()) {
                                calibrationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                val latitude = locationInfo.lastLatitude
                                val longitude = locationInfo.lastLongitude
                                val accuracy = locationInfo.accuracyMeters

                                if (
                                    latitude != null &&
                                    longitude != null &&
                                    accuracy != null &&
                                    accuracy <= TinkerspaceLocationConfig.REQUIRED_ACCURACY_METERS
                                ) {
                                    TinkerspaceLocationConfig.calibrateOutdoor(
                                        context = context,
                                        latitude = latitude,
                                        longitude = longitude
                                    )

                                    locationManager.startMonitoring(
                                        onOutdoorArrival = {
                                            repository.reachTinkerspaceOutdoorStop(
                                                isSimulated = false
                                            )
                                            showOutdoorArrivalDialog = true
                                        },
                                        onIndoorArrival = {
                                            repository.reachTinkerspaceIndoorStop(
                                                isSimulated = false
                                            )
                                            showIndoorArrivalDialog = true
                                        },
                                        isOutdoorExpected =
                                            workflowState == TinkerspaceWorkflowState.IN_PROGRESS,
                                        isIndoorExpected =
                                            workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
                                    )
                                }
                            }
                        },
                        enabled =
                            !locationManager.hasLocationPermission() ||
                                (
                                    locationInfo.lastLatitude != null &&
                                        locationInfo.lastLongitude != null &&
                                        (
                                            locationInfo.accuracyMeters?.let {
                                                it <= TinkerspaceLocationConfig.REQUIRED_ACCURACY_METERS
                                            } == true
                                        )
                                    ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoamlyPrimary
                        )
                    ) {
                        Text(
                            text = "Calibrate Outdoor Destination",
                            color = RoamlySurface
                        )
                    }

                    Text(
                        text = if (locationInfo.isOutdoorCalibrated) {
                            "Outdoor destination: ${
                                String.format(
                                    Locale.US,
                                    "%.6f",
                                    TinkerspaceLocationConfig.outdoorLatitude
                                )
                            }, ${
                                String.format(
                                    Locale.US,
                                    "%.6f",
                                    TinkerspaceLocationConfig.outdoorLongitude
                                )
                            }"
                        } else {
                            "Not calibrated. Stand at the intended outdoor destination and wait for GPS accuracy of 10 m or better."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (locationInfo.isOutdoorCalibrated) {
                                RoamlySuccess
                            } else {
                                RoamlyTextSecondary
                            },
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (!locationManager.hasLocationPermission()) {
                                calibrationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                val latitude = locationInfo.lastLatitude
                                val longitude = locationInfo.lastLongitude
                                val accuracy = locationInfo.accuracyMeters

                                if (
                                    latitude != null &&
                                    longitude != null &&
                                    accuracy != null &&
                                    accuracy <= TinkerspaceLocationConfig.REQUIRED_ACCURACY_METERS
                                ) {
                                    TinkerspaceLocationConfig.calibrateIndoor(
                                        context = context,
                                        latitude = latitude,
                                        longitude = longitude
                                    )

                                    locationManager.startMonitoring(
                                        onOutdoorArrival = {
                                            repository.reachTinkerspaceOutdoorStop(
                                                isSimulated = false
                                            )
                                            showOutdoorArrivalDialog = true
                                        },
                                        onIndoorArrival = {
                                            repository.reachTinkerspaceIndoorStop(
                                                isSimulated = false
                                            )
                                            showIndoorArrivalDialog = true
                                        },
                                        isOutdoorExpected =
                                            workflowState == TinkerspaceWorkflowState.IN_PROGRESS,
                                        isIndoorExpected =
                                            workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
                                    )
                                }
                            }
                        },
                        enabled =
                            !locationManager.hasLocationPermission() ||
                                (
                                    locationInfo.lastLatitude != null &&
                                        locationInfo.lastLongitude != null &&
                                        (
                                            locationInfo.accuracyMeters?.let {
                                                it <= TinkerspaceLocationConfig.REQUIRED_ACCURACY_METERS
                                            } == true
                                        )
                                    ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Calibrate Indoor Destination")
                    }

                    Text(
                        text = if (locationInfo.isIndoorCalibrated) {
                            "Indoor destination: ${
                                String.format(
                                    Locale.US,
                                    "%.6f",
                                    TinkerspaceLocationConfig.indoorLatitude
                                )
                            }, ${
                                String.format(
                                    Locale.US,
                                    "%.6f",
                                    TinkerspaceLocationConfig.indoorLongitude
                                )
                            }"
                        } else {
                            "Not calibrated. Calibrate the indoor point separately."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (locationInfo.isIndoorCalibrated) {
                                RoamlySuccess
                            } else {
                                RoamlyTextSecondary
                            },
                        lineHeight = 18.sp
                    )

                    Text(
                        text = "Calibration saves the phone's current coordinates as the destination. For reliable testing, wait until GPS accuracy is 10 metres or better.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            when (workflowState) {

                TinkerspaceWorkflowState.UPCOMING -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Before starting, calibrate both destinations above. The arrival workflow uses GPS rather than manual simulation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextSecondary,
                            lineHeight = 20.sp
                        )

                        Button(
                            onClick = {
                                if (locationManager.hasLocationPermission()) {
                                    repository.startTinkerspaceTrip()
                                } else {
                                    tripPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoamlyPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = RoamlySurface,
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Start Trip",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoamlySurface
                            )
                        }
                    }
                }

                TinkerspaceWorkflowState.IN_PROGRESS -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = RoamlyPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Current Objective",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = RoamlyPrimary
                            )

                            Text(
                                text = "Reach Outside Tinkerspace Building",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )

                            Text(
                                text = if (locationInfo.isOutdoorCalibrated) {
                                    "Walk to the calibrated outdoor destination. Arrival will be detected when a valid GPS fix places you within the 10-metre geofence."
                                } else {
                                    "The outdoor destination is not calibrated yet. Calibrate it in the GPS diagnostics card."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED -> {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = RoamlyPrimaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Stop 1 Reached",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyPrimary
                            )

                            Text(
                                text = "Outside Tinkerspace Building",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextPrimary
                            )

                            Text(
                                text = if (locationInfo.isIndoorCalibrated) {
                                    "Return to the calibrated indoor destination. Arrival will be detected using GPS."
                                } else {
                                    "Calibrate the indoor destination above before returning to it."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                TinkerspaceWorkflowState.INDOOR_STOP_REACHED,
                TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = RoamlySurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                RoamlyBorder,
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(RoamlyPrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Luggage,
                                        contentDescription = null,
                                        tint = RoamlyPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Before You Finish...",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyTextPrimary
                                    )

                                    Text(
                                        text = "Check that you have everything with you.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoamlyTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Personal Belongings Check",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            checkedBelongings.keys.toList().forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            checkedBelongings[item] =
                                                !(checkedBelongings[item] ?: false)
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Checkbox(
                                        checked =
                                            checkedBelongings[item] ?: false,
                                        onCheckedChange = { checked ->
                                            checkedBelongings[item] = checked
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = RoamlyPrimary,
                                            uncheckedColor = RoamlyBorder
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoamlyTextPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "This is a personal confirmation. Roamly does not physically scan your belongings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = {
                                    repository.confirmTinkerspaceBelongingsChecked()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoamlyPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = RoamlySurface,
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "I've Checked My Belongings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlySurface
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = onReviewChecklist,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Review My Checklist")
                            }
                        }
                    }
                }

                TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = RoamlySurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                RoamlyBorder,
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = RoamlySuccess,
                                    modifier = Modifier.size(28.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = "Trip Completed!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                            }

                            Text(
                                text = "You reached both destinations and confirmed your belongings check.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )

                            if (belongingsCheckedTime > 0) {
                                val timeString =
                                    SimpleDateFormat(
                                        "h:mm a",
                                        Locale.getDefault()
                                    ).format(Date(belongingsCheckedTime))

                                Text(
                                    text = "Belongings confirmed at $timeString",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                            }

                            Button(
                                onClick = {
                                    repository.endTinkerspaceTrip()
                                    onBack()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoamlyPrimary
                                )
                            ) {
                                Text(
                                    text = "End Trip",
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlySurface
                                )
                            }
                        }
                    }
                }

                TinkerspaceWorkflowState.COMPLETED -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = RoamlySurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                RoamlyBorder,
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = RoamlySuccess,
                                    modifier = Modifier.size(28.dp)
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = "Journey Complete",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                            }

                            Text(
                                text = "The Tinkerspace demonstration journey has been completed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary
                            )

                            OutlinedButton(
                                onClick = {
                                    repository.resetTinkerspaceDemo()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text("Reset Demo")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(
                horizontal = 10.dp,
                vertical = 5.dp
            )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = RoamlyTextSecondary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = RoamlyTextPrimary
        )
    }
}

@Composable
private fun StopItemRow(
    stopNumber: Int,
    stopName: String,
    stopDesc: String,
    isReached: Boolean,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) {
                    RoamlyPrimaryContainer.copy(alpha = 0.5f)
                } else {
                    RoamlySurfaceVariant
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isReached -> RoamlySuccess
                        isActive -> RoamlyPrimary
                        else -> RoamlyBorder
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isReached) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Reached",
                    tint = RoamlySurface,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = stopNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoamlySurface
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stopName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = stopDesc,
                style = MaterialTheme.typography.bodySmall,
                color = RoamlyTextSecondary,
                lineHeight = 16.sp
            )
        }

        if (isReached || isActive) {
            Spacer(modifier = Modifier.width(6.dp))

            StatusChip(
                text = if (isReached) "Reached" else "En Route",
                backgroundColor = if (isReached) {
                    RoamlySuccess.copy(alpha = 0.15f)
                } else {
                    RoamlyPrimary.copy(alpha = 0.15f)
                },
                textColor = if (isReached) {
                    RoamlySuccess
                } else {
                    RoamlyPrimary
                }
            )
        }
    }
}