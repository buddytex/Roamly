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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.ui.theme.RoamlyAccent
import com.roamly.app.ui.theme.RoamlyAccentContainer
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySkyBlue
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlySurfaceVariant
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary

@Composable
fun HomeScreen(
    repository: RoamlyRepository,
    onViewTravelPlan: (com.roamly.app.model.Trip) -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToAi: (prompt: String?) -> Unit,
    onNavigateToChecklists: () -> Unit
) {
    val trips by repository.trips.collectAsState()
    val checklist by repository.checklist.collectAsState()
    val currentTrip = repository.getCurrentTrip()

    val totalItems = checklist.size
    val packedItems = checklist.count { it.isPacked }
    val packProgress = if (totalItems > 0) packedItems.toFloat() / totalItems.toFloat() else 0f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Header: Greeting & Demo Weather Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hello, Alex! 🌿",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Welcome to your ${currentTrip.destination} trip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary
                    )
                }

                // Weather indicator badge clearly labelled as demo data
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(RoamlySurfaceVariant)
                        .border(1.dp, RoamlyBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = "Weather",
                                tint = RoamlyAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "31°C Sunny",
                                style = MaterialTheme.typography.labelMedium,
                                color = RoamlyTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Demo Weather",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlyTextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // TRIP SWITCHER TABS (Singapore Explorer vs Outside Tinkerspace)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trips.forEach { trip ->
                    val isSelected = trip.id == currentTrip.id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) RoamlyPrimary else RoamlySurface)
                            .border(1.dp, if (isSelected) RoamlyPrimary else RoamlyBorder, RoundedCornerShape(12.dp))
                            .clickable { repository.selectTrip(trip.id) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = trip.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RoamlySurface else RoamlyTextPrimary
                            )
                            Text(
                                text = if (trip.id == "trip_tinkerspace_demo") "Demo Journey" else "5-Day Holiday",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isSelected) RoamlySurface.copy(alpha = 0.8f) else RoamlyTextSecondary
                            )
                        }
                    }
                }
            }
        }

        // MAIN TRIP CARD
        item {
            Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, RoamlyBorder, RoundedCornerShape(20.dp))
                ) {
                    Column {
                        // Visual Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(125.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            RoamlyPrimary,
                                            RoamlyPrimary.copy(alpha = 0.85f),
                                            RoamlySkyBlue.copy(alpha = 0.75f)
                                        )
                                    )
                                )
                                .padding(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(RoamlySurface.copy(alpha = 0.25f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = currentTrip.status.displayName.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RoamlySurface,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    // Duration / Demo Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(RoamlySurface.copy(alpha = 0.25f))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (currentTrip.id == "trip_singapore_2026") "5-DAY TRIP" else "DEMO JOURNEY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RoamlySurface,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = currentTrip.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlySurface
                                )
                            }
                        }

                        // Trip Details & Primary/Secondary Actions
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = RoamlyPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${currentTrip.dates} (${currentTrip.durationDays} days)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = RoamlyTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = RoamlyTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${currentTrip.travelerCount} travellers",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RoamlyTextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = currentTrip.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Primary Action Button: "View My Travel Plan"
                            Button(
                                onClick = { onViewTravelPlan(currentTrip) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = null,
                                    tint = RoamlySurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (currentTrip.id == "trip_tinkerspace_demo") "Open Demo Journey" else "View My Travel Plan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlySurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = RoamlySurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Secondary Action: "My Trips"
                            OutlinedButton(
                                onClick = onNavigateToTrips,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, RoamlyPrimary.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "My Trips",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
                            }
                        }
                    }
                }
            }

        // THREE CLEAR USEFUL ACTIONS
        item {
            Text(
                text = "Quick Travel Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ACTION 1: What Should I Pack?
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
                        .clickable { onNavigateToChecklists() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(RoamlyAccentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Luggage,
                                contentDescription = null,
                                tint = RoamlyAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "What Should I Pack?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$packedItems/$totalItems packed",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // ACTION 2: Ask for Travel Help
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
                        .clickable { onNavigateToAi(null) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(RoamlyPrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Ask Travel Help",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Offline assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // ACTION 3: Places to Visit
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
                        .clickable { onViewTravelPlan(currentTrip) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = RoamlySurface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(RoamlySurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Places to Visit",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Daily schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // TRAVEL REMINDERS
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoamlyBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Important Reminders",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Check your travel documents: Submit your electronic SG Arrival Card (SGAC) within 3 days before entry.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Review your packing list: Ensure UK 3-pin adapters and prescription medications are prepared.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Check your belongings before leaving: Use Roamly's reminder at every stop so nothing is left behind.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
