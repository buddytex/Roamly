package com.railguard.mobile.tripmate.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railguard.mobile.tripmate.data.TripMateRepository
import com.railguard.mobile.tripmate.models.SOSIncident
import com.railguard.mobile.tripmate.models.SOSStatus
import com.railguard.mobile.tripmate.models.Trip

@Composable
fun GuideDashboardScreen(
    repository: TripMateRepository,
    trip: Trip?,
    sosIncidents: List<SOSIncident>,
    onCreateTrip: () -> Unit
) {
    if (trip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onCreateTrip) {
                Text("Create First Trip")
            }
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val overdueTravelers = trip.travelers.filter { it.isCheckInOverdue }
    val pendingSos = sosIncidents.filter { it.status == SOSStatus.REPORTED_PENDING_ACK }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Guide Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Tour Lead: ${trip.guideName}", fontSize = 13.sp, color = Color.Gray)
                Text(trip.agencyName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = Color(0xFF16A34A).copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF16A34A)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Guide Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Metrics Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Travelers Count Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("TRAVELERS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("${trip.travelers.size}", fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("All Assigned", fontSize = 10.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Medium)
                }
            }

            // Overdue Alert Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (overdueTravelers.isNotEmpty()) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("CHECK-IN OVERDUE", fontSize = 10.sp, color = if (overdueTravelers.isNotEmpty()) Color(0xFFB45309) else Color.Gray, fontWeight = FontWeight.Bold)
                    Text("${overdueTravelers.size}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (overdueTravelers.isNotEmpty()) Color(0xFFD97706) else Color.DarkGray)
                    Text(if (overdueTravelers.isNotEmpty()) "Action Needed" else "All On Schedule", fontSize = 10.sp, color = if (overdueTravelers.isNotEmpty()) Color(0xFFB45309) else Color.Gray, fontWeight = FontWeight.Medium)
                }
            }

            // Pending SOS Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pendingSos.isNotEmpty()) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("SOS REQUESTS", fontSize = 10.sp, color = if (pendingSos.isNotEmpty()) Color(0xFFB91C1C) else Color.Gray, fontWeight = FontWeight.Bold)
                    Text("${pendingSos.size}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (pendingSos.isNotEmpty()) Color(0xFFDC2626) else Color.DarkGray)
                    Text(if (pendingSos.isNotEmpty()) "Urgent" else "Clear", fontSize = 10.sp, color = if (pendingSos.isNotEmpty()) Color(0xFFB91C1C) else Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Urgent SOS Notification Banner
        if (pendingSos.isNotEmpty()) {
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Active Assistance Request Received!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    pendingSos.forEach { incident ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Traveler: ${incident.travelerName} (${incident.travelerPhone})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Location: ${incident.destinationName}", fontSize = 11.sp, color = Color.Gray)
                            Text("Issue: ${incident.message}", fontSize = 12.sp, color = Color(0xFF7F1D1D))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { repository.acknowledgeSOS(incident.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Acknowledge", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { repository.resolveSOS(incident.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Mark Resolved", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tabs: Group Travelers vs Trip Itinerary
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Travelers (${trip.travelers.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Trip Progress") })
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (selectedTab == 0) {
            // Travelers List
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(trip.travelers) { traveler ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (traveler.isCheckInOverdue) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(traveler.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    if (traveler.isCheckInOverdue) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = Color(0xFFDC2626),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "OVERDUE",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Last Location: ${traveler.currentStop}", fontSize = 11.sp, color = Color.Gray)
                                Text("Last Check-In: ${traveler.lastCheckInTime}", fontSize = 11.sp, color = if (traveler.isCheckInOverdue) Color(0xFFD97706) else Color.DarkGray)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFF2563EB).copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = traveler.phone,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Itinerary / Destinations Progress
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(trip.destinations) { dest ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dest.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("${dest.plannedTime} • Assembly: ${dest.meetingPoint}", fontSize = 11.sp, color = Color.Gray)
                            }

                            Surface(
                                color = when (dest.status) {
                                    com.railguard.mobile.tripmate.models.DestinationStatus.CURRENT -> Color(0xFF2563EB)
                                    com.railguard.mobile.tripmate.models.DestinationStatus.COMPLETED -> Color(0xFF16A34A)
                                    else -> Color.Gray.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = dest.status.name,
                                    fontSize = 10.sp,
                                    color = if (dest.status == com.railguard.mobile.tripmate.models.DestinationStatus.UPCOMING) Color.DarkGray else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Guide Actions Footer
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCreateTrip,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create New Trip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { repository.resetToDemoData() },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Demo Data", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
