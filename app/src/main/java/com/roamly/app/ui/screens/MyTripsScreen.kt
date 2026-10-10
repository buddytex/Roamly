package com.roamly.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.roamly.app.model.DestinationItem
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.ui.components.BelongingsReminderDialog
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
fun MyTripsScreen(
    repository: RoamlyRepository
) {
    val trips by repository.trips.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Upcoming, 1: Active, 2: Completed
    val currentTrip = trips.firstOrNull { it.id == "trip_singapore_2026" } ?: trips.firstOrNull()

    // Departure reminder dialog state
    var showBelongingsDialogForDest by remember { mutableStateOf<DestinationItem?>(null) }

    if (showBelongingsDialogForDest != null && currentTrip != null) {
        BelongingsReminderDialog(
            destinationName = showBelongingsDialogForDest!!.name,
            onDismiss = { showBelongingsDialogForDest = null },
            onConfirmed = {
                repository.markDepartureReminderAcknowledged(currentTrip.id, showBelongingsDialogForDest!!.id)
                repository.markDestinationVisited(currentTrip.id, showBelongingsDialogForDest!!.id, true)
                showBelongingsDialogForDest = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
    ) {
        // Top App Bar Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RoamlySurface)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "My Journeys",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary
            )
            Text(
                text = "Track your itinerary, planned activities, and stops",
                style = MaterialTheme.typography.bodySmall,
                color = RoamlyTextSecondary
            )
        }

        // Status Tabs (Upcoming, Active, Completed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf("Upcoming (1)", "Active (0)", "Completed (1)")
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) RoamlyPrimary else RoamlySurface)
                        .border(1.dp, if (isSelected) RoamlyPrimary else RoamlyBorder, RoundedCornerShape(10.dp))
                        .clickable { selectedTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) RoamlySurface else RoamlyTextSecondary
                    )
                }
            }
        }

        if (selectedTab == 2) {
            // Completed Trips
            val completedTrips = trips.filter { it.status == TripStatus.COMPLETED }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(completedTrips) { trip ->
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
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(RoamlyPrimaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "COMPLETED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoamlyPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${trip.dates} • ${trip.destination}, ${trip.country}",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = trip.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary
                            )
                        }
                    }
                }
            }
        } else {
            // Main 5-Day Singapore Itinerary (Upcoming / Active)
            if (currentTrip != null) {
                var selectedDayIndex by remember { mutableIntStateOf(0) }
                val currentDay = currentTrip.days.getOrNull(selectedDayIndex) ?: currentTrip.days.firstOrNull()

                // Overall Progress Calculation
                val allDestinations = currentTrip.days.flatMap { it.destinations }
                val totalDests = allDestinations.size
                val completedDests = allDestinations.count { it.isCompleted }
                val tripProgress = if (totalDests > 0) completedDests.toFloat() / totalDests.toFloat() else 0f

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    // Trip Progress Header Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = currentTrip.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyTextPrimary
                                    )
                                    Text(
                                        text = "${currentTrip.dates} • ${currentTrip.durationDays} Days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RoamlyTextSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RoamlyPrimaryContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$completedDests of $totalDests visited",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { tripProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = RoamlyPrimary,
                                trackColor = RoamlySurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Day Tabs
                    ScrollableTabRow(
                        selectedTabIndex = selectedDayIndex,
                        containerColor = RoamlyBackground,
                        contentColor = RoamlyPrimary,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        currentTrip.days.forEachIndexed { idx, day ->
                            val isDaySelected = selectedDayIndex == idx
                            Tab(
                                selected = isDaySelected,
                                onClick = { selectedDayIndex = idx },
                                text = {
                                    Text(
                                        text = "Day ${day.dayNumber}",
                                        fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isDaySelected) RoamlyPrimary else RoamlyTextSecondary
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (currentDay != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                // Day Theme Title
                                Column {
                                    Text(
                                        text = currentDay.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyTextPrimary
                                    )
                                    Text(
                                        text = currentDay.date,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RoamlyTextSecondary
                                    )
                                }
                            }

                            items(currentDay.destinations) { destination ->
                                DestinationCard(
                                    destination = destination,
                                    onToggleActivity = { actId ->
                                        repository.toggleActivity(currentTrip.id, destination.id, actId)
                                    },
                                    onToggleCompleted = { completed ->
                                        if (completed && !destination.departureReminderChecked) {
                                            // Prompt belongings reminder before completing!
                                            showBelongingsDialogForDest = destination
                                        } else {
                                            repository.markDestinationVisited(currentTrip.id, destination.id, completed)
                                        }
                                    },
                                    onDeparting = {
                                        showBelongingsDialogForDest = destination
                                    }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(30.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationCard(
    destination: DestinationItem,
    onToggleActivity: (activityId: String) -> Unit,
    onToggleCompleted: (Boolean) -> Unit,
    onDeparting: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (destination.isCompleted) RoamlySuccess.copy(alpha = 0.4f) else RoamlyBorder,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Time, Category & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = RoamlyTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${destination.timeSlot} (${destination.duration})",
                        style = MaterialTheme.typography.labelSmall,
                        color = RoamlyTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RoamlySurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = destination.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = RoamlyTextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Visited checkmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = destination.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (destination.isCompleted) RoamlySuccess else RoamlyTextPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (destination.isCompleted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RoamlySuccess.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Visited",
                            tint = RoamlySuccess,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Visited",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlySuccess,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = destination.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = RoamlyTextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Expand/Collapse Details Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Hide activities & tips" else "View activities & practical tips",
                    style = MaterialTheme.typography.labelMedium,
                    color = RoamlyPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = RoamlyPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = destination.fullDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary,
                        lineHeight = 18.sp
                    )

                    // Activities Checklist
                    if (destination.activities.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoamlySurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Planned Activities",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            destination.activities.forEach { act ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleActivity(act.id) }
                                        .padding(vertical = 3.dp)
                                ) {
                                    Checkbox(
                                        checked = act.isDone,
                                        onCheckedChange = { onToggleActivity(act.id) },
                                        colors = CheckboxDefaults.colors(checkedColor = RoamlyPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = act.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (act.isDone) RoamlyPrimary else RoamlyTextPrimary,
                                        fontWeight = if (act.isDone) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Practical Tips
                    if (destination.practicalTips.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoamlyPrimaryContainer.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = RoamlyPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Practical Instructions",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            destination.practicalTips.forEach { tip ->
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(text = "•", color = RoamlyPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                                    Text(text = tip, style = MaterialTheme.typography.bodySmall, color = RoamlyTextPrimary)
                                }
                            }
                        }
                    }

                    // Precautions
                    if (destination.precautions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoamlyAccentContainer.copy(alpha = 0.6f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = RoamlyAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Local Precautions",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            destination.precautions.forEach { precaution ->
                                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(text = "•", color = RoamlyAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
                                    Text(text = precaution, style = MaterialTheme.typography.bodySmall, color = RoamlyTextPrimary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Departure & Completion Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDeparting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Luggage,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = RoamlyPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Depart Stop", color = RoamlyPrimary, style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = { onToggleCompleted(!destination.isCompleted) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (destination.isCompleted) RoamlySuccess else RoamlyPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (destination.isCompleted) "Completed" else "Mark Visited",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
