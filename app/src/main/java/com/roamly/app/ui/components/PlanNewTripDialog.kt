package com.roamly.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.ui.theme.RoamlyAccent
import com.roamly.app.ui.theme.RoamlyAccentContainer
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlySurfaceVariant
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary
import java.util.UUID

@Composable
fun PlanNewTripDialog(
    onDismiss: () -> Unit,
    onTripCreated: (Trip) -> Unit,
    onPlanWithAi: (destination: String) -> Unit
) {
    var destination by remember { mutableStateOf("") }
    var departureDate by remember { mutableStateOf("Nov 15, 2026") }
    var durationDays by remember { mutableIntStateOf(4) }
    var travelerCount by remember { mutableIntStateOf(2) }
    val selectedInterests = remember { mutableStateListOf("🏛️ Culture", "🍜 Local Food") }

    val allInterests = listOf("🏛️ Culture", "🍜 Local Food", "🌿 Nature & Parks", "☕ Relaxed Café", "🛍️ Shopping")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = RoamlySurface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(RoamlyPrimaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlightTakeoff,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Plan a New Trip",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )
                            Text(
                                text = "Essential travel details",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = RoamlyTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Field 1: Destination
                Text(
                    text = "1. Where are you travelling to?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    placeholder = { Text("e.g., Tokyo, Japan or Paris, France") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoamlyPrimary,
                        unfocusedBorderColor = RoamlyBorder
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Field 2: Dates & Duration
                Text(
                    text = "2. Departure Date & Duration",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = departureDate,
                        onValueChange = { departureDate = it },
                        label = { Text("Start Date") },
                        modifier = Modifier.weight(1.3f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = "$durationDays days",
                        onValueChange = {
                            val num = it.filter { char -> char.isDigit() }.toIntOrNull()
                            if (num != null && num in 1..30) durationDays = num
                        },
                        label = { Text("Duration") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field 3: Number of Travellers
                Text(
                    text = "3. Number of Travellers",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(1 to "Solo (1)", 2 to "Couple (2)", 4 to "Group (4)", 6 to "Family (6)").forEach { (count, label) ->
                        val isSelected = travelerCount == count
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) RoamlyPrimary else RoamlySurfaceVariant)
                                .border(1.dp, if (isSelected) RoamlyPrimary else RoamlyBorder, RoundedCornerShape(10.dp))
                                .clickable { travelerCount = count }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RoamlySurface else RoamlyTextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field 4: Interests
                Text(
                    text = "4. Travel Interests",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allInterests.take(3).forEach { interest ->
                        val isSelected = selectedInterests.contains(interest)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) RoamlyPrimaryContainer else RoamlySurfaceVariant)
                                .border(1.dp, if (isSelected) RoamlyPrimary else RoamlyBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    if (isSelected) selectedInterests.remove(interest) else selectedInterests.add(interest)
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = interest,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) RoamlyPrimary else RoamlyTextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // OPTION A: Create My Plan
                Button(
                    onClick = {
                        val destName = if (destination.isNotBlank()) destination.trim() else "Tokyo, Japan"
                        val newTrip = Trip(
                            id = "trip_" + UUID.randomUUID().toString().take(8),
                            title = "$destName Journey",
                            destination = destName,
                            country = destName.substringAfterLast(", ").ifBlank { destName },
                            dates = "$departureDate ($durationDays days)",
                            durationDays = durationDays,
                            travelerCount = travelerCount,
                            travelers = emptyList(),
                            status = TripStatus.PLANNING,
                            summary = "Personal trip to $destName focused on ${selectedInterests.joinToString(", ")}.",
                            meetingPoint = "Hotel Lobby / Central Station",
                            announcements = listOf("Trip created! Review packing list and preparation documents."),
                            maxGroupSize = travelerCount + 2,
                            isGuideManaged = false,
                            days = emptyList()
                        )
                        onTripCreated(newTrip)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Create My Plan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // OPTION B: Plan with Travel Help
                OutlinedButton(
                    onClick = {
                        val destName = if (destination.isNotBlank()) destination.trim() else "my next trip"
                        onPlanWithAi(destName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoamlyPrimary)
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = RoamlyPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Plan with Travel Help",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyPrimary
                    )
                }
            }
        }
    }
}
