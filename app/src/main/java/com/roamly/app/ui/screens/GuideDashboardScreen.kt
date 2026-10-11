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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.ui.components.PlanNewTripDialog
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

@Composable
fun GuideDashboardScreen(
    repository: RoamlyRepository,
    onManageTrip: (Trip) -> Unit,
    onSwitchRole: () -> Unit
) {
    val trips by repository.trips.collectAsState()
    val travellers by repository.travellers.collectAsState()
    var showCreateTripDialog by remember { mutableStateOf(false) }

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
                        subtitle = "In demo database",
                        modifier = Modifier.weight(1f)
                    )
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
                            Text(
                                text = "${trip.travelerCount} Travellers",
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
