package com.railguard.mobile.tripmate.ui.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.railguard.mobile.tripmate.models.SOSIncident
import com.railguard.mobile.tripmate.models.SOSStatus
import com.railguard.mobile.tripmate.models.Trip

@Composable
fun SosScreen(
    repository: TripMateRepository,
    trip: Trip?,
    incidents: List<SOSIncident>
) {
    val currentDest = trip?.destinations?.getOrNull(trip.currentDestinationIndex)
    var selectedReason by remember { mutableStateOf("Separated from Tour Group") }
    var customMessage by remember { mutableStateOf("") }
    var requestSubmittedConfirmation by remember { mutableStateOf(false) }

    val reasons = listOf(
        "Separated from Tour Group",
        "Medical / First Aid Needed",
        "Lost Critical Belonging / Passport",
        "Transport / Meeting Point Emergency"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // High visibility Emergency Banner
        Surface(
            color = Color(0xFFFEF2F2),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Emergency,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Assistance & SOS Dispatch",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF991B1B)
                        )
                        Text(
                            text = "Direct alert to Lead Guide: ${trip?.guideName ?: "Tour Lead"}",
                            fontSize = 12.sp,
                            color = Color(0xFF7F1D1D)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Current Segment: ${currentDest?.name ?: "In Transit"}\n" +
                            "Estimated GPS Coordinates: %.4f, %.4f".format(currentDest?.latitude ?: 25.1972, currentDest?.longitude ?: 55.2744),
                    fontSize = 12.sp,
                    color = Color(0xFF991B1B),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reason Selector
        Text("Select Emergency Category:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        reasons.forEach { reason ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedReason == reason),
                    onClick = { selectedReason = reason },
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFDC2626))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = reason, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = customMessage,
            onValueChange = { customMessage = it },
            label = { Text("Details or Notes for Guide (Optional)") },
            placeholder = { Text("e.g. Near Souk entrance, need assistance locating the bus") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dispatch Button
        Button(
            onClick = {
                val fullMsg = "$selectedReason" + if (customMessage.isNotBlank()) " - $customMessage" else ""
                repository.submitSOS(
                    travelerName = "Elena Rostova (You)",
                    travelerPhone = "+1 415 555 0192",
                    destinationName = currentDest?.name ?: "Active Tour Route",
                    message = fullMsg,
                    lat = currentDest?.latitude,
                    lng = currentDest?.longitude
                )
                customMessage = ""
                requestSubmittedConfirmation = true
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("REQUEST IMMEDIATE ASSISTANCE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        if (requestSubmittedConfirmation) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "✓ Assistance request logged. Stored offline and dispatched to Guide Dashboard.",
                    fontSize = 12.sp,
                    color = Color(0xFF1D4ED8),
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Incident Tracking
        if (incidents.isNotEmpty()) {
            Text("Recent Incident Reports:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            incidents.forEach { incident ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(incident.destinationName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Surface(
                                color = when (incident.status) {
                                    SOSStatus.REPORTED_PENDING_ACK -> Color(0xFFF59E0B)
                                    SOSStatus.ACKNOWLEDGED -> Color(0xFF2563EB)
                                    SOSStatus.RESOLVED -> Color(0xFF16A34A)
                                },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = when (incident.status) {
                                        SOSStatus.REPORTED_PENDING_ACK -> "PENDING GUIDE ACK"
                                        SOSStatus.ACKNOWLEDGED -> "ACKNOWLEDGED BY GUIDE"
                                        SOSStatus.RESOLVED -> "RESOLVED"
                                    },
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(incident.message, fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Local Emergency Contacts
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Emergency Hotlines (Dubai, UAE)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Police Emergency: 999", fontSize = 12.sp)
                Text("• Ambulance: 998", fontSize = 12.sp)
                Text("• Tourist Police Assistance: 901", fontSize = 12.sp)
                Text("• Tour Lead Direct Call: ${trip?.guideContact ?: "+971 50 123 4567"}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
            }
        }
    }
}
