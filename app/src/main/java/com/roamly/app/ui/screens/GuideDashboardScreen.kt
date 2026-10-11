package com.roamly.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.model.DemoEventRecord
import com.roamly.app.model.DemoStatus
import com.roamly.app.model.TravellerDemoStatus
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.ui.components.PlanNewTripDialog
import com.roamly.app.ui.theme.RoamlyAccent
import com.roamly.app.ui.theme.RoamlyAccentContainer
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyError
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySuccess
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlySurfaceVariant
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary
import androidx.compose.material3.TextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuideDashboardScreen(
    repository: RoamlyRepository,
    onManageTrip: (Trip) -> Unit,
    onSwitchRole: () -> Unit
) {
    val trips by repository.trips.collectAsState()
    val travellers by repository.travellers.collectAsState()
    val demoStatuses by repository.activeDemoStatuses.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showCreateTripDialog by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedTravellerForHistory by remember { mutableStateOf<TravellerDemoStatus?>(null) }
    var historyEvents by remember { mutableStateOf<List<DemoEventRecord>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }
    var travellersError by remember { mutableStateOf<String?>(null) }
    var isLoadingTravellers by remember { mutableStateOf(false) }

    suspend fun refreshAllData() {
        isRefreshing = true
        repository.fetchDemoSessions()
        val result = repository.fetchTravellers()
        if (result.isFailure) {
            travellersError = result.exceptionOrNull()?.message ?: "Unable to fetch travellers"
        } else {
            travellersError = null
        }
        isRefreshing = false
    }

    // Periodic polling for live demo status and real traveller roster updates
    LaunchedEffect(Unit) {
        isLoadingTravellers = true
        repository.fetchDemoSessions()
        val initialResult = repository.fetchTravellers()
        if (initialResult.isFailure) {
            travellersError = initialResult.exceptionOrNull()?.message
        }
        isLoadingTravellers = false

        while (isActive) {
            delay(5000L)
            repository.fetchDemoSessions()
            repository.fetchTravellers()
        }
    }

    // Calculated metrics from actual repository records
    val upcomingCount = trips.count { it.status == TripStatus.UPCOMING }
    val inProgressCount = trips.count { it.status == TripStatus.IN_PROGRESS }
    val completedCount = trips.count { it.status == TripStatus.COMPLETED }
    val totalTravellers = travellers.size


    if (showCreateTripDialog) {
        PlanNewTripDialog(
            onDismiss = { showCreateTripDialog = false },
            onTripCreated = { newTrip ->
                repository.addTrip(newTrip.copy(isGuideManaged = true))
                showCreateTripDialog = false
            },
            onPlanWithAi = { dest ->
                showCreateTripDialog = false
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
            Spacer(modifier = Modifier.height(10.dp))
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RoamlyPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CompassCalibration,
                                contentDescription = null,
                                tint = RoamlySurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Guide Command",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Welcome. Here's your trip overview.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary
                    )
                }

                // Switch Role Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(RoamlySurfaceVariant)
                        .border(1.dp, RoamlyBorder, RoundedCornerShape(10.dp))
                        .clickable { onSwitchRole() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Switch Role",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoamlyPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Summary Metric Cards Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Upcoming Trips",
                        value = upcomingCount.toString(),
                        subtitle = "Ready for departure",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Active / Ongoing",
                        value = inProgressCount.toString(),
                        subtitle = "Currently touring",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Completed Trips",
                        value = completedCount.toString(),
                        subtitle = "Past journeys",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Travellers",
                        value = totalTravellers.toString(),
                        subtitle = if (totalTravellers == 0) "No travellers yet" else "Assigned roster",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (travellersError != null) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlyError.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RoamlyError.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RoamlyError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Traveller sync issue: $travellersError",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyError
                            )
                        }
                        TextButton(
                            onClick = {
                                coroutineScope.launch { refreshAllData() }
                            }
                        ) {
                            Text("Retry", color = RoamlyError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Prominent "+ Create New Trip" Button
        item {
            Button(
                onClick = { showCreateTripDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create New Trip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoamlySurface
                )
            }
        }

        // -------------------------------------------------------------
        // LIVE DEMO MONITORING SECTION
        // -------------------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Live Demo Monitoring",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(RoamlySuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "POLLING 5S",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoamlySuccess,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = "Real-time traveller status for Tinkerspace demo",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            refreshAllData()
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(RoamlySurfaceVariant)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = RoamlyPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh demo status",
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (demoStatuses.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = RoamlyTextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No Active Demo Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                        Text(
                            text = "When a traveller starts the Tinkerspace demo from My Trips, their real-time progress will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(demoStatuses, key = { it.travellerId }) { session ->
                DemoSessionCard(
                    session = session,
                    onViewHistory = {
                        selectedTravellerForHistory = session
                        coroutineScope.launch {
                            isLoadingHistory = true
                            historyEvents = repository.fetchDemoEvents(session.travellerId)
                            isLoadingHistory = false
                        }
                    }
                )
            }
        }

        item {
            Text(
                text = "Managed Journeys (${trips.size})",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary
            )
        }


        items(trips, key = { it.id }) { trip ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = trip.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )

                        val statusColor = when (trip.status) {
                            TripStatus.UPCOMING -> RoamlyPrimary
                            TripStatus.IN_PROGRESS -> RoamlyAccent
                            TripStatus.COMPLETED -> RoamlySuccess
                            TripStatus.PLANNING -> RoamlyTextSecondary
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = trip.status.displayName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = RoamlyTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = trip.dates,
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = RoamlyTextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val assignedCount = travellers.count { it.assignedTripId == trip.id }
                            Text(
                                text = "$assignedCount Travellers",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = trip.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onManageTrip(trip) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Manage Trip",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (selectedTravellerForHistory != null) {
        val traveller = selectedTravellerForHistory!!
        AlertDialog(
            onDismissRequest = { selectedTravellerForHistory = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = RoamlyPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${traveller.travellerName} Event History",
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoadingHistory) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = RoamlyPrimary)
                        }
                    } else {
                        val events = if (historyEvents.isNotEmpty()) historyEvents else traveller.eventHistory
                        if (events.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No events recorded yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = RoamlyTextSecondary
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(events, key = { it.id }) { event ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = RoamlySurfaceVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = event.eventType,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RoamlyPrimary
                                                )
                                                Text(
                                                    text = formatDemoDateTime(event.timestamp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = RoamlyTextSecondary,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = event.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = RoamlyTextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedTravellerForHistory = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun DemoSessionCard(
    session: TravellerDemoStatus,
    onViewHistory: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Traveller & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RoamlyPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = session.travellerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                        Text(
                            text = session.tripName,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary
                        )
                    }
                }

                val (badgeColor, textColor) = when (session.status) {
                    DemoStatus.NOT_STARTED -> RoamlySurfaceVariant to RoamlyTextSecondary
                    DemoStatus.ACTIVE -> RoamlyPrimaryContainer to RoamlyPrimary
                    DemoStatus.DEPARTED -> RoamlyAccentContainer to RoamlyAccent
                    DemoStatus.RETURNED -> RoamlyPrimaryContainer to RoamlyPrimary
                    DemoStatus.AWAITING_BELONGINGS_CHECK -> RoamlyAccentContainer to RoamlyAccent
                    DemoStatus.COMPLETED -> RoamlySuccess.copy(alpha = 0.15f) to RoamlySuccess
                    DemoStatus.LOCATION_UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = session.status.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            // Location Monitoring Availability Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (session.isLocationAvailable) RoamlySurfaceVariant
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (session.isLocationAvailable) Icons.Default.LocationOn else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (session.isLocationAvailable) RoamlySuccess else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (session.isLocationAvailable) "Location Monitoring: Verified Active"
                           else "Location Monitoring: Unavailable / Revoked",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.isLocationAvailable) RoamlyTextPrimary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            // Timings & Check status Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RoamlySurfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DEPARTURE TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlyTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = formatDemoTime(session.departureTime),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RETURN TIME",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlyTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = formatDemoTime(session.returnTime),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BELONGINGS CHECK",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlyTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (session.isBelongingsChecked) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = RoamlySuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified (${formatDemoTime(session.belongingsCheckedTime)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlySuccess
                                )
                            } else {
                                Text(
                                    text = "Pending",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COMPLETION",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlyTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (session.isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = RoamlySuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ended (${formatDemoTime(session.completedTime)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlySuccess
                                )
                            } else {
                                Text(
                                    text = "In Progress",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Last Event Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LAST EVENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoamlyTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = session.lastEvent,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = formatDemoDateTime(session.lastEventTimestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = RoamlyTextSecondary,
                    fontSize = 10.sp
                )
            }

            // View Event History Button
            OutlinedButton(
                onClick = onViewHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "View Event History (${session.eventHistory.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatDemoTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "Pending"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDemoDateTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "—"
    return SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = RoamlyTextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = RoamlyPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = RoamlyTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

