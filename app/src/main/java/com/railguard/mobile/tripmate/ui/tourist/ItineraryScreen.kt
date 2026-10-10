package com.railguard.mobile.tripmate.ui.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.railguard.mobile.tripmate.models.Destination
import com.railguard.mobile.tripmate.models.DestinationStatus
import com.railguard.mobile.tripmate.models.Trip

@Composable
fun ItineraryScreen(
    repository: TripMateRepository,
    trip: Trip,
    onSelectDestination: (Destination) -> Unit
) {
    var departingDestination by remember { mutableStateOf<Destination?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header info
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = trip.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = trip.startDate, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${trip.destinations.size} Planned Stops", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Timeline list
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(trip.destinations) { index, dest ->
                val isCurrent = dest.status == DestinationStatus.CURRENT
                val isCompleted = dest.status == DestinationStatus.COMPLETED

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Timeline indicator column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCompleted -> Color(0xFF16A34A)
                                        isCurrent -> Color(0xFF2563EB)
                                        else -> Color.LightGray
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCurrent) Color.White else Color.DarkGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (index < trip.destinations.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(110.dp)
                                    .background(if (isCompleted) Color(0xFF16A34A).copy(alpha = 0.5f) else Color.LightGray.copy(alpha = 0.5f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Destination Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .clickable { onSelectDestination(dest) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isCurrent -> Color(0xFFEFF6FF)
                                isCompleted -> Color(0xFFF0FDF4)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dest.plannedTime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isCurrent -> Color(0xFF2563EB)
                                        isCompleted -> Color(0xFF16A34A)
                                        else -> Color.Gray
                                    }
                                )

                                Surface(
                                    color = when {
                                        isCurrent -> Color(0xFF2563EB)
                                        isCompleted -> Color(0xFF16A34A)
                                        else -> Color.Gray.copy(alpha = 0.2f)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = dest.status.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent || isCompleted) Color.White else Color.DarkGray,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = dest.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dest.shortDescription,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = dest.meetingPoint,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                            }

                            if (isCurrent) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Button(
                                        onClick = { departingDestination = dest },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.DepartureBoard, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Departure Check", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    departingDestination?.let { dest ->
        BelongingsReminderDialog(
            repository = repository,
            currentDestination = dest,
            belongings = trip.belongingsList,
            onDismiss = { departingDestination = null },
            onProceedToNext = { departingDestination = null }
        )
    }
}
