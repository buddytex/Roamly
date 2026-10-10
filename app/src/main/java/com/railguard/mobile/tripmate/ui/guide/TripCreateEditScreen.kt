package com.railguard.mobile.tripmate.ui.guide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import com.railguard.mobile.tripmate.models.DestinationStatus
import com.railguard.mobile.tripmate.models.Trip
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCreateEditScreen(
    repository: TripMateRepository,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("Abu Dhabi Architectural & Cultural Day Tour") }
    var description by remember { mutableStateOf("Full day expedition visiting Sheikh Zayed Grand Mosque, Louvre Abu Dhabi, and Corniche.") }
    var agencyName by remember { mutableStateOf("Voyage Horizon Tours") }
    var guideName by remember { mutableStateOf("Sari Al-Mansoor") }
    var guideContact by remember { mutableStateOf("+971 50 123 4567") }
    var startDate by remember { mutableStateOf("Tomorrow, 08:00 AM") }
    var meetingPoint by remember { mutableStateOf("Abu Dhabi Central Station / Grand Canal Pier") }

    var dest1Name by remember { mutableStateOf("Sheikh Zayed Grand Mosque") }
    var dest1Time by remember { mutableStateOf("09:00 AM - 12:00 PM") }
    var dest1Meeting by remember { mutableStateOf("North Visitor Entrance Dome") }
    var dest1Overview by remember { mutableStateOf("One of the world's largest mosques featuring 82 white marble domes and reflective pools.") }

    var dest2Name by remember { mutableStateOf("Louvre Abu Dhabi") }
    var dest2Time by remember { mutableStateOf("01:30 PM - 04:30 PM") }
    var dest2Meeting by remember { mutableStateOf("Rain of Light Dome Plaza") }
    var dest2Overview by remember { mutableStateOf("Universal museum situated on Saadiyat Island under Jean Nouvel's geometric dome.") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Trip Itinerary", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Trip Details", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Trip Title") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Trip Summary / Description") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start Date / Time") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = agencyName,
                    onValueChange = { agencyName = it },
                    label = { Text("Agency Name") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = meetingPoint,
                onValueChange = { meetingPoint = it },
                label = { Text("Primary Meeting Point") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Destinations & Stops", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            // Destination 1 Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Stop 1 (Initial Active Stop)", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dest1Name,
                        onValueChange = { dest1Name = it },
                        label = { Text("Destination Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dest1Time,
                            onValueChange = { dest1Time = it },
                            label = { Text("Planned Time") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dest1Meeting,
                            onValueChange = { dest1Meeting = it },
                            label = { Text("Assembly Point") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dest1Overview,
                        onValueChange = { dest1Overview = it },
                        label = { Text("Overview & Sights") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Destination 2 Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Stop 2 (Subsequent Stop)", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dest2Name,
                        onValueChange = { dest2Name = it },
                        label = { Text("Destination Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dest2Time,
                            onValueChange = { dest2Time = it },
                            label = { Text("Planned Time") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dest2Meeting,
                            onValueChange = { dest2Meeting = it },
                            label = { Text("Assembly Point") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = dest2Overview,
                        onValueChange = { dest2Overview = it },
                        label = { Text("Overview & Sights") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Submit Button
            Button(
                onClick = {
                    val d1 = Destination(
                        name = dest1Name,
                        shortDescription = dest1Overview.take(80) + "...",
                        fullOverview = dest1Overview,
                        whyVisit = "Iconic landmark featuring world-class architecture and cultural heritage.",
                        plannedTime = dest1Time,
                        meetingPoint = dest1Meeting,
                        latitude = 24.4128,
                        longitude = 54.4750,
                        status = DestinationStatus.CURRENT,
                        precautions = listOf("Strict dress etiquette applies: loose, non-transparent attire.", "Remove footwear before carpet entry areas.")
                    )

                    val d2 = Destination(
                        name = dest2Name,
                        shortDescription = dest2Overview.take(80) + "...",
                        fullOverview = dest2Overview,
                        whyVisit = "Groundbreaking cultural museum celebrating universal human creativity across civilizations.",
                        plannedTime = dest2Time,
                        meetingPoint = dest2Meeting,
                        latitude = 24.5336,
                        longitude = 54.3986,
                        status = DestinationStatus.UPCOMING,
                        precautions = listOf("No flash photography allowed inside main galleries.", "Quiet zones in high-acoustics dome walkways.")
                    )

                    val newTrip = Trip(
                        title = title,
                        description = description,
                        agencyName = agencyName,
                        guideName = guideName,
                        guideContact = guideContact,
                        startDate = startDate,
                        meetingPoint = meetingPoint,
                        destinations = listOf(d1, d2),
                        travelers = repository.getActiveTrip()?.travelers ?: emptyList(),
                        packingList = repository.getActiveTrip()?.packingList ?: emptyList(),
                        belongingsList = repository.getActiveTrip()?.belongingsList ?: emptyList()
                    )

                    repository.createTrip(newTrip)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Publish & Distribute Itinerary", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
