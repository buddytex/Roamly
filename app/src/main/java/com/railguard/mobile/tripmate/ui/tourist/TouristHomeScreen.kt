package com.railguard.mobile.tripmate.ui.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railguard.mobile.tripmate.data.TripMateRepository
import com.railguard.mobile.tripmate.models.Destination
import com.railguard.mobile.tripmate.models.Trip

@Composable
fun TouristHomeScreen(
    repository: TripMateRepository,
    trip: Trip?,
    onNavigateToItinerary: () -> Unit,
    onNavigateToDestination: (Destination) -> Unit,
    onNavigateToChecklist: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToSos: () -> Unit
) {
    if (trip == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No trip loaded. Please ask your tour guide to share an itinerary.")
        }
        return
    }

    var showBelongingsDialog by remember { mutableStateOf(false) }

    val currentDest = trip.destinations.getOrNull(trip.currentDestinationIndex)
    val nextDest = trip.destinations.getOrNull(trip.currentDestinationIndex + 1)
    val nextActivity = currentDest?.activities?.firstOrNull { !it.isCompleted }

    val packedCount = trip.packingList.count { it.isPacked }
    val totalPacking = trip.packingList.size
    val packingProgress = if (totalPacking > 0) packedCount.toFloat() / totalPacking else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Welcome and Tour Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Good Morning, Elena", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Text("Your Travel Companion", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tourist Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Trip Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trip.agencyName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB),
                        letterSpacing = 1.sp
                    )
                    Surface(
                        color = Color(0xFF16A34A).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = trip.status.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(trip.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(trip.startDate, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Guide: ${trip.guideName}", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Current Destination Card
        currentDest?.let { dest ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToDestination(dest) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFF2563EB),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "CURRENT STOP (${trip.currentDestinationIndex + 1} OF ${trip.destinations.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(dest.plannedTime, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(dest.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(dest.shortDescription, fontSize = 13.sp, color = Color.DarkGray, maxLines = 2)

                    Spacer(modifier = Modifier.height(10.dp))

                    if (nextActivity != null) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Upcoming Activity: ${nextActivity.title}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("${nextActivity.time} • ${nextActivity.description}", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onNavigateToDestination(dest) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Open Destination Guide →", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        }

                        Button(
                            onClick = { showBelongingsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.DepartureBoard, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Depart Stop", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Next Stop Preview Card
        nextDest?.let { next ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Next Destination: ${next.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${next.plannedTime} • Assemble at ${next.meetingPoint}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Smart Packing Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToChecklist() },
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Luggage, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Packing Checklist", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("$packedCount / $totalPacking", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { packingProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = Color(0xFF16A34A)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (packingProgress == 1f) "All items packed!" else "Tap to check off remaining belongings",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AI Travel Assistant Banner
        Surface(
            color = Color(0xFFF3E8FF),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToAi() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = Color(0xFF7E22CE), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("TripMate AI Assistant", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF581C87))
                    Text("Ask about history, sights, or upcoming plans", fontSize = 12.sp, color = Color(0xFF6B21A8))
                }
                Text("Chat →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7E22CE))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SOS Assistance Shortcut
        Surface(
            color = Color(0xFFFEF2F2),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToSos() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("SOS / Request Assistance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                    Text("Alert guide or access emergency numbers", fontSize = 12.sp, color = Color(0xFF7F1D1D))
                }
                Text("SOS →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showBelongingsDialog && currentDest != null) {
        BelongingsReminderDialog(
            repository = repository,
            currentDestination = currentDest,
            belongings = trip.belongingsList,
            onDismiss = { showBelongingsDialog = false },
            onProceedToNext = { showBelongingsDialog = false }
        )
    }
}
