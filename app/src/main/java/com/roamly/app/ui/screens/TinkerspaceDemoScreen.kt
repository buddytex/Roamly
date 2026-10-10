package com.roamly.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.roamly.app.location.TinkerspaceLocationManager
import com.roamly.app.model.TinkerspaceWorkflowState
import com.roamly.app.ui.theme.RoamlyAccent
import com.roamly.app.ui.theme.RoamlyAccentContainer
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
    val isSimulated by repository.tinkerspaceIsSimulated.collectAsState()
    val belongingsCheckedTime by repository.tinkerspaceBelongingsCheckedTime.collectAsState()

    val locationManager = remember { TinkerspaceLocationManager(context) }
    val locationInfo by locationManager.locationInfo.collectAsState()

    var showOutdoorArrivalDialog by remember { mutableStateOf(false) }
    var showIndoorArrivalDialog by remember { mutableStateOf(false) }

    // Belongings quick checklist selections for this review
    val checkedBelongings = remember {
        mutableStateMapOf<String, Boolean>().apply {
            SampleData.belongingDepartureItems.take(5).forEach { put(it, false) }
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            repository.startTinkerspaceTrip()
            locationManager.startMonitoring(
                onOutdoorArrival = {
                    repository.reachTinkerspaceOutdoorStop(isSimulated = false)
                    showOutdoorArrivalDialog = true
                },
                onIndoorArrival = {
                    repository.reachTinkerspaceIndoorStop(isSimulated = false)
                    showIndoorArrivalDialog = true
                },
                isOutdoorExpected = workflowState == TinkerspaceWorkflowState.IN_PROGRESS,
                isIndoorExpected = workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
            )
        } else {
            // Permission denied: start trip anyway with simulation fallback available
            repository.startTinkerspaceTrip()
        }
    }

    // Location monitoring lifecycle effect
    DisposableEffect(workflowState) {
        if (workflowState == TinkerspaceWorkflowState.IN_PROGRESS ||
            workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
        ) {
            if (locationManager.hasLocationPermission()) {
                locationManager.startMonitoring(
                    onOutdoorArrival = {
                        repository.reachTinkerspaceOutdoorStop(isSimulated = false)
                        showOutdoorArrivalDialog = true
                    },
                    onIndoorArrival = {
                        repository.reachTinkerspaceIndoorStop(isSimulated = false)
                        showIndoorArrivalDialog = true
                    },
                    isOutdoorExpected = workflowState == TinkerspaceWorkflowState.IN_PROGRESS,
                    isIndoorExpected = workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
                )
            }
        } else {
            locationManager.stopMonitoring()
        }

        onDispose {
            locationManager.stopMonitoring()
        }
    }

    // STOP 1 ARRIVAL DIALOG
    if (showOutdoorArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showOutdoorArrivalDialog = false },
            title = {
                Text(
                    text = "You've Arrived!",
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You've reached the outside of Tinkerspace building.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextPrimary
                    )
                    Text(
                        text = "Next destination: Back Inside Tinkerspace Building",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RoamlyPrimaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "1 of 2 locations reached",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showOutdoorArrivalDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                ) {
                    Text("Continue Journey")
                }
            }
        )
    }

    // STOP 2 ARRIVAL DIALOG
    if (showIndoorArrivalDialog) {
        AlertDialog(
            onDismissRequest = { showIndoorArrivalDialog = false },
            title = {
                Text(
                    text = "Welcome Back!",
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You've reached your final destination.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextPrimary
                    )
                    Text(
                        text = "Your trip is complete!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = RoamlySuccess
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RoamlyPrimaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "2 of 2 locations reached",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showIndoorArrivalDialog = false
                        repository.proceedToBelongingsCheck()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                ) {
                    Text("Check Belongings")
                }
            }
        )
    }

    // MAIN CONTENT
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // TOP BAR
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
                        .border(1.dp, RoamlyBorder, CircleShape)
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
                        text = "Location Demonstration Journey",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }
            }
        }

        // 1. ROUTE & PROGRESS CARD ("Your Short Trip")
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoamlyBorder, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
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
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (workflowState == TinkerspaceWorkflowState.COMPLETED) RoamlySuccess.copy(alpha = 0.15f)
                                    else RoamlyPrimaryContainer
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (workflowState == TinkerspaceWorkflowState.COMPLETED) RoamlySuccess else RoamlyPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "A short demonstration trip showing location-aware arrivals, trip progress, and belongings verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Progress Bar & Counter
                    val progressRatio = when (workflowState) {
                        TinkerspaceWorkflowState.UPCOMING -> 0.0f
                        TinkerspaceWorkflowState.IN_PROGRESS -> 0.0f
                        TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED -> 0.5f
                        else -> 1.0f
                    }
                    val progressText = when (workflowState) {
                        TinkerspaceWorkflowState.UPCOMING,
                        TinkerspaceWorkflowState.IN_PROGRESS -> "0 of 2 locations reached"
                        TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED -> "1 of 2 locations reached"
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

                    // Route Stops List
                    Text(
                        text = "Route:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stop 1
                    val stop1Reached = workflowState != TinkerspaceWorkflowState.UPCOMING &&
                            workflowState != TinkerspaceWorkflowState.IN_PROGRESS
                    val stop1Active = workflowState == TinkerspaceWorkflowState.IN_PROGRESS

                    StopItemRow(
                        stopNumber = 1,
                        stopName = "Outside Tinkerspace Building",
                        stopDesc = "The traveller leaves the building and reaches the designated outdoor arrival zone.",
                        isReached = stop1Reached,
                        isActive = stop1Active
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stop 2
                    val stop2Reached = workflowState == TinkerspaceWorkflowState.INDOOR_STOP_REACHED ||
                            workflowState == TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK ||
                            workflowState == TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION ||
                            workflowState == TinkerspaceWorkflowState.COMPLETED
                    val stop2Active = workflowState == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED

                    StopItemRow(
                        stopNumber = 2,
                        stopName = "Back Inside Tinkerspace Building",
                        stopDesc = "The traveller returns to the designated indoor arrival zone, completing the route.",
                        isReached = stop2Reached,
                        isActive = stop2Active
                    )
                }
            }
        }

        // 2. LOCATION STATUS / DETECTION INFO
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Location Detection Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (locationInfo.hasPermission) {
                        Text(
                            text = "Automatic mode is active. Roamly continuously monitors GPS boundaries to trigger arrival notifications when you enter demonstration zones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            lineHeight = 18.sp
                        )
                        if (locationInfo.accuracyMeters != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "GPS status: ±${locationInfo.accuracyMeters?.toInt()}m accuracy",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoamlyPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = "Location access is optional. If GPS is unavailable indoors or permissions are not granted, use the 'Demo: Simulate Arrival' fallback button below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            lineHeight = 18.sp
                        )
                    }

                    if (isSimulated && workflowState != TinkerspaceWorkflowState.UPCOMING) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(RoamlyAccentContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Demonstration simulation was used for this event",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoamlyAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 3. WORKFLOW ACTIONS ACCORDING TO STATE MACHINE
        item {
            when (workflowState) {
                TinkerspaceWorkflowState.UPCOMING -> {
                    Button(
                        onClick = {
                            if (locationManager.hasLocationPermission()) {
                                repository.startTinkerspaceTrip()
                            } else {
                                permissionLauncher.launch(
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
                        colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
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

                TinkerspaceWorkflowState.IN_PROGRESS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Current Objective: Reach Outside Tinkerspace Building",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = RoamlyTextPrimary
                        )

                        // Fallback Simulation Button
                        OutlinedButton(
                            onClick = {
                                repository.reachTinkerspaceOutdoorStop(isSimulated = true)
                                showOutdoorArrivalDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, RoamlyPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Demo: Simulate Arrival",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyPrimary
                            )
                        }
                    }
                }

                TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RoamlyPrimaryContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Stop 1 Reached: Outside Tinkerspace Building",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Next expected destination: Back Inside Tinkerspace Building",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextPrimary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                repository.reachTinkerspaceIndoorStop(isSimulated = true)
                                showIndoorArrivalDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, RoamlyPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Demo: Simulate Arrival (Return Indoors)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyPrimary
                            )
                        }
                    }
                }

                TinkerspaceWorkflowState.INDOOR_STOP_REACHED,
                TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK -> {
                    // Belongings Verification Screen
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, RoamlyBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        text = "Please check that you have everything with you.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoamlyTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Personal Belongings Check:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            checkedBelongings.keys.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable { checkedBelongings[item] = !(checkedBelongings[item] ?: false) }
                                ) {
                                    Checkbox(
                                        checked = checkedBelongings[item] ?: false,
                                        onCheckedChange = { isChecked ->
                                            checkedBelongings[item] = isChecked
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
                                text = "Note: This is a personal traveller confirmation to help you avoid leaving items behind. Roamly does not physically scan belongings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Action 1: "I've Checked My Belongings"
                            Button(
                                onClick = {
                                    repository.confirmTinkerspaceBelongingsChecked()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
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

                            // Action 2: "Review My Checklist"
                            OutlinedButton(
                                onClick = onReviewChecklist,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, RoamlyPrimary)
                            ) {
                                Text(
                                    text = "Review My Checklist",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
                            }
                        }
                    }
                }

                TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, RoamlyBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "You reached both destinations and confirmed your belongings check. Thank you for travelling with Roamly!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RoamlyPrimaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "2 of 2 locations reached",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
                            }

                            if (belongingsCheckedTime > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault())
                                    .format(Date(belongingsCheckedTime))
                                Text(
                                    text = "Belongings confirmed at $timeStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Primary Button: "End Trip"
                            Button(
                                onClick = {
                                    repository.endTinkerspaceTrip()
                                    onBack()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                            ) {
                                Text(
                                    text = "End Trip",
                                    style = MaterialTheme.typography.titleMedium,
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
                        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, RoamlyBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Outside Tinkerspace demonstration journey completed successfully (2 of 2 locations reached).",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedButton(
                                onClick = {
                                    repository.resetTinkerspaceDemo()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, RoamlyPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = RoamlyPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Reset Demo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
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
            .background(if (isActive) RoamlyPrimaryContainer.copy(alpha = 0.5f) else RoamlySurfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isReached) RoamlySuccess
                    else if (isActive) RoamlyPrimary
                    else RoamlyBorder
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isReached) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = RoamlySurface,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "$stopNumber",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoamlySurface
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
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

        if (isReached) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RoamlySuccess.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Reached",
                    style = MaterialTheme.typography.labelSmall,
                    color = RoamlySuccess,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        } else if (isActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RoamlyPrimary.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "En Route",
                    style = MaterialTheme.typography.labelSmall,
                    color = RoamlyPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}
