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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun GuideManageTripScreen(
    tripId: String,
    repository: RoamlyRepository,
    onBack: () -> Unit
) {
    val trips by repository.trips.collectAsState()
    val allTravellers by repository.travellers.collectAsState()
    val trip = trips.find { it.id == tripId } ?: trips.firstOrNull()

    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var newAnnouncementText by remember { mutableStateOf("") }

    var showMeetingPointDialog by remember { mutableStateOf(false) }
    var newMeetingPointText by remember { mutableStateOf(trip?.meetingPoint ?: "") }

    if (trip == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(RoamlyBackground).padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Trip record not found.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Go Back") }
        }
        return
    }

    val tripTravellers = allTravellers.filter { it.assignedTripId == trip.id }
    val totalActivities = trip.days.flatMap { it.destinations }.flatMap { it.activities }.size
    val visitedActivities = trip.days.flatMap { it.destinations }.flatMap { it.activities }.count { it.isDone }

    if (showAnnouncementDialog) {
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            title = {
                Text(
                    text = "Post Trip Announcement",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This announcement will be saved locally and displayed in this trip's updates feed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                    OutlinedTextField(
                        value = newAnnouncementText,
                        onValueChange = { newAnnouncementText = it },
                        placeholder = { Text("e.g., Meeting point changed to hotel lobby at 09:30 AM.") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAnnouncementText.isNotBlank()) {
                            repository.addTripAnnouncement(trip.id, newAnnouncementText)
                            newAnnouncementText = ""
                            showAnnouncementDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                ) {
                    Text("Broadcast Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnnouncementDialog = false }) {
                    Text("Cancel", color = RoamlyTextSecondary)
                }
            }
        )
    }

    if (showMeetingPointDialog) {
        AlertDialog(
            onDismissRequest = { showMeetingPointDialog = false },
            title = {
                Text(
                    text = "Edit Meeting Point",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = newMeetingPointText,
                    onValueChange = { newMeetingPointText = it },
                    placeholder = { Text("e.g., Terminal 3 Arrival Hall Pillar 4") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newMeetingPointText.isNotBlank()) {
                            repository.updateTripMeetingPoint(trip.id, newMeetingPointText)
                            showMeetingPointDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMeetingPointDialog = false }) {
                    Text("Cancel", color = RoamlyTextSecondary)
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
            Spacer(modifier = Modifier.height(10.dp))
            // Navigation Back Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = RoamlyTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Manage: ${trip.title}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                    Text(
                        text = "Operational Controls & Traveller Oversight",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }
            }
        }

        // SECTION A: TRIP OVERVIEW
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Section A • Trip Overview",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${trip.destination}, ${trip.country}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dates: ${trip.dates} (${trip.durationDays} Days)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Meeting Point Box
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RoamlySurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = RoamlyPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Meeting Point:", style = MaterialTheme.typography.labelSmall, color = RoamlyTextSecondary)
                                Text(trip.meetingPoint, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = RoamlyTextPrimary)
                            }
                        }
                        IconButton(onClick = {
                            newMeetingPointText = trip.meetingPoint
                            showMeetingPointDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = RoamlyPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // SECTION E: TRIP STATUS TRANSITIONS
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Section E • Manage Trip Status",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Current Status: ${trip.status.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TripStatus.values().forEach { status ->
                            val isCurrent = trip.status == status
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) RoamlyPrimary else RoamlySurfaceVariant)
                                    .border(1.dp, if (isCurrent) RoamlyPrimary else RoamlyBorder, RoundedCornerShape(8.dp))
                                    .clickable { repository.updateTripStatus(trip.id, status) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = status.displayName.take(8),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) RoamlySurface else RoamlyTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECTION C: TRIP PROGRESS
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Section C • Trip Activity Progress",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (totalActivities > 0) "$visitedActivities of $totalActivities planned activities marked visited." else "No activities configured yet.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reflects activity checklist checkoffs recorded in the demonstration itinerary. Does not imply physical GPS location.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // SECTION D: IMPORTANT UPDATES & ANNOUNCEMENTS
        item {
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
                            text = "Section D • Announcements",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyPrimary
                        )
                        Button(
                            onClick = { showAnnouncementDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Notice", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (trip.announcements.isEmpty()) {
                        Text("No updates broadcast yet for this trip.", style = MaterialTheme.typography.bodySmall, color = RoamlyTextSecondary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            trip.announcements.forEach { notice ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RoamlyPrimaryContainer.copy(alpha = 0.4f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Default.Campaign, contentDescription = null, tint = RoamlyPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(notice, style = MaterialTheme.typography.bodySmall, color = RoamlyTextPrimary, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION B: TRAVELLERS ASSIGNED TO THIS TRIP
        item {
            Text(
                text = "Section B • Assigned Travellers (${tripTravellers.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary
            )
        }

        if (tripTravellers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                    modifier = Modifier.fillMaxWidth().border(1.dp, RoamlyBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No travellers have been assigned to this trip yet.", style = MaterialTheme.typography.bodyMedium, color = RoamlyTextSecondary)
                    }
                }
            }
        } else {
            items(tripTravellers) { trav ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, RoamlyBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(RoamlyPrimaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = RoamlyPrimary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(trav.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RoamlyTextPrimary)
                                    Text(trav.contactPhone, style = MaterialTheme.typography.bodySmall, color = RoamlyTextSecondary)
                                }
                            }

                            val badgeColor = when (trav.preparationStatus) {
                                "Ready" -> RoamlySuccess
                                "Preparation Incomplete" -> RoamlyAccent
                                else -> RoamlyTextSecondary
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(trav.preparationStatus, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Packing Checklist: ${trav.packedItemsCount} of ${trav.totalItemsCount} packed", style = MaterialTheme.typography.bodySmall, color = RoamlyTextSecondary)
                            Text("Visited: ${trav.visitedActivitiesCount}/${trav.totalActivitiesCount}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = RoamlyPrimary)
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
