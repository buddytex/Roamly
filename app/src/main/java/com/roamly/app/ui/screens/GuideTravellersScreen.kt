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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.roamly.app.model.TravellerRecord
import com.roamly.app.ui.theme.RoamlyAccent
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GuideTravellersScreen(
    repository: RoamlyRepository
) {
    val travellers by repository.travellers.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedTravellerForDetail by remember { mutableStateOf<TravellerRecord?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val filterOptions = listOf("All", "Active", "Outside Tinkerspace", "Trip Selected", "Registered", "Completed")

    fun refreshTravellers() {
        coroutineScope.launch {
            isRefreshing = true
            val res = repository.fetchTravellers()
            if (res.isFailure) {
                fetchError = res.exceptionOrNull()?.message ?: "Unable to fetch travellers from backend"
            } else {
                fetchError = null
            }
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshTravellers()
    }

    val filteredTravellers = travellers.filter { traveller ->
        when (selectedFilter) {
            "Active" -> traveller.overallStatus == "Active"
            "Outside Tinkerspace" -> traveller.overallStatus == "Outside Tinkerspace"
            "Trip Selected" -> traveller.overallStatus == "Trip Selected"
            "Registered" -> traveller.overallStatus == "Registered"
            "Completed" -> traveller.overallStatus == "Completed"
            else -> true
        }
    }

    if (selectedTravellerForDetail != null) {
        TravellerDetailDialog(
            traveller = selectedTravellerForDetail!!,
            onDismiss = { selectedTravellerForDetail = null }
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

            // Screen Header with Refresh button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Travellers",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Registered travellers assigned to your tours and their verified statuses.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RoamlyTextSecondary,
                        lineHeight = 22.sp
                    )
                }

                IconButton(
                    onClick = { refreshTravellers() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(RoamlySurfaceVariant)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = RoamlyPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Travellers",
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (fetchError != null) {
                Spacer(modifier = Modifier.height(12.dp))
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
                                text = "Unable to load travellers: $fetchError",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyError
                            )
                        }
                        TextButton(onClick = { refreshTravellers() }) {
                            Text("Retry", color = RoamlyError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoamlyPrimary,
                            selectedLabelColor = RoamlySurface,
                            containerColor = RoamlySurface,
                            labelColor = RoamlyTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) RoamlyPrimary else RoamlyBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        if (filteredTravellers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(RoamlyBorder))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = RoamlyTextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = if (travellers.isEmpty()) "No Registered Travellers" else "No matching travellers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                        Text(
                            text = if (travellers.isEmpty())
                                "When travellers register and join your tours, their profiles and real-time status will appear here."
                            else
                                "No travellers currently match the '$selectedFilter' filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (travellers.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { refreshTravellers() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                            ) {
                                Text("Check for Travellers")
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredTravellers, key = { it.id }) { traveller ->
                TravellerCard(
                    traveller = traveller,
                    onClick = { selectedTravellerForDetail = traveller }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TravellerCard(
    traveller: TravellerRecord,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RoamlyBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(RoamlyPrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = traveller.name,
                        tint = RoamlyPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = traveller.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = traveller.assignedTripName,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }

                // Status Badge
                StatusBadge(status = traveller.overallStatus)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Details Row: Location Monitoring & Belongings Check
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RoamlySurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location monitoring flag (only verified active)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsWalk,
                        contentDescription = null,
                        tint = if (traveller.isLocationMonitoringActive) RoamlySuccess else RoamlyTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (traveller.isLocationMonitoringActive) "GPS: Active" else "GPS: Inactive",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (traveller.isLocationMonitoringActive) RoamlySuccess else RoamlyTextSecondary
                    )
                }

                // Belongings Check Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Luggage,
                        contentDescription = null,
                        tint = if (traveller.isBelongingsChecked) RoamlySuccess else RoamlyAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (traveller.isBelongingsChecked) "Belongings: Checked" else "Belongings: Pending",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (traveller.isBelongingsChecked) RoamlySuccess else RoamlyTextSecondary
                    )
                }
            }

            if (!traveller.lastRecordedEvent.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = RoamlyTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Last: ${traveller.lastRecordedEvent} • ${formatTimestamp(traveller.lastEventTimestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoamlyTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bgColor, textColor, icon) = when (status) {
        "Active" -> Triple(RoamlyPrimary.copy(alpha = 0.15f), RoamlyPrimary, Icons.Default.DirectionsWalk)
        "Outside Tinkerspace" -> Triple(RoamlyAccent.copy(alpha = 0.15f), RoamlyAccent, Icons.Default.DirectionsWalk)
        "Returned" -> Triple(RoamlySuccess.copy(alpha = 0.15f), RoamlySuccess, Icons.Default.CheckCircle)
        "Awaiting Belongings Check" -> Triple(RoamlyAccent.copy(alpha = 0.15f), RoamlyAccent, Icons.Default.Luggage)
        "Completed" -> Triple(RoamlySuccess.copy(alpha = 0.15f), RoamlySuccess, Icons.Default.CheckCircle)
        "Trip Selected" -> Triple(RoamlyPrimaryContainer, RoamlyPrimary, Icons.Default.Place)
        "Location Unavailable" -> Triple(RoamlyError.copy(alpha = 0.15f), RoamlyError, Icons.Default.Warning)
        "Registered" -> Triple(RoamlySurfaceVariant, RoamlyTextSecondary, Icons.Default.Person)
        "Ready" -> Triple(RoamlySuccess.copy(alpha = 0.15f), RoamlySuccess, Icons.Default.CheckCircle)
        "Preparation Incomplete" -> Triple(RoamlyAccent.copy(alpha = 0.15f), RoamlyAccent, Icons.Default.Warning)
        "Documents Pending" -> Triple(RoamlyError.copy(alpha = 0.15f), RoamlyError, Icons.Default.Info)
        else -> Triple(RoamlySurfaceVariant, RoamlyTextSecondary, Icons.Default.Info)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

@Composable
private fun TravellerDetailDialog(
    traveller: TravellerRecord,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = traveller.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Assigned Trip:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = traveller.assignedTripName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Current Status:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusBadge(status = traveller.overallStatus)
                }

                if (!traveller.email.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = traveller.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextPrimary
                        )
                    }
                }

                if (traveller.contactPhone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = traveller.contactPhone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RoamlySurfaceVariant)
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Location Monitoring: " + if (traveller.isLocationMonitoringActive) "Active (Live GPS)" else "Inactive",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (traveller.isLocationMonitoringActive) RoamlySuccess else RoamlyTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (traveller.departureTimestamp != null) {
                            Text(
                                text = "Departure: ${formatTimestamp(traveller.departureTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary
                            )
                        }
                        if (traveller.returnTimestamp != null) {
                            Text(
                                text = "Return: ${formatTimestamp(traveller.returnTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary
                            )
                        }
                        Text(
                            text = "Belongings Check: " + if (traveller.isBelongingsChecked) "Verified & Complete" else "Pending Check",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (traveller.isBelongingsChecked) RoamlySuccess else RoamlyTextSecondary
                        )
                        Text(
                            text = "Trip Completion: " + if (traveller.isTripCompleted) "Completed (${formatTimestamp(traveller.completedTimestamp)})" else "In Progress / Open",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (traveller.isTripCompleted) RoamlySuccess else RoamlyTextSecondary
                        )
                    }
                }

                if (!traveller.lastRecordedEvent.isNullOrBlank()) {
                    Text(
                        text = "Last Event: ${traveller.lastRecordedEvent} (${formatTimestamp(traveller.lastEventTimestamp)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyPrimary
                )
            }
        },
        containerColor = RoamlySurface,
        shape = RoundedCornerShape(20.dp)
    )
}

private fun formatTimestamp(epoch: Long?): String {
    if (epoch == null || epoch <= 0L) return "N/A"
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epoch))
}
