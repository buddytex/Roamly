package com.roamly.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roamly.app.R
import com.roamly.app.auth.model.AuthUser
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.data.SampleData
import com.roamly.app.model.Trip
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
import java.util.Locale

@Composable
fun HomeScreen(
    repository: RoamlyRepository,
    currentUser: AuthUser? = null,
    onViewTravelPlan: (Trip) -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToAi: (prompt: String?) -> Unit,
    onNavigateToChecklists: () -> Unit
) {
    val checklist by repository.checklist.collectAsState()
    val selectedTripIds by repository.selectedTripIds.collectAsState()

    val totalItems = checklist.size
    val packedItems = checklist.count { it.isPacked }

    val singaporeTrip = SampleData.singaporeTrip
    val tinkerspaceTrip = SampleData.tinkerspaceTrip

    val isSingaporeSelected = selectedTripIds.contains(singaporeTrip.id)
    val isTinkerspaceSelected = selectedTripIds.contains(tinkerspaceTrip.id)

    var showSingaporeDetailsDialog by remember { mutableStateOf(false) }

    // Personalized greeting derived from AuthUser without hardcoded names
    val greetingName = currentUser?.fullName?.trim()?.takeIf { it.isNotBlank() }
        ?: currentUser?.email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }

    val greetingTitle = if (!greetingName.isNullOrBlank() && !greetingName.equals("Explorer", ignoreCase = true)) {
        val firstName = greetingName.split(Regex("\\s+")).firstOrNull()?.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        } ?: greetingName
        "Hello, $firstName!"
    } else {
        "Hello, Traveller!"
    }

    if (showSingaporeDetailsDialog) {
        SingaporePackageDetailsDialog(
            trip = singaporeTrip,
            isSelected = isSingaporeSelected,
            onDismiss = { showSingaporeDetailsDialog = false },
            onSelectTrip = {
                repository.addTripToMyTrips(singaporeTrip.id)
            },
            onOpenFullItinerary = {
                showSingaporeDetailsDialog = false
                repository.selectTrip(singaporeTrip.id)
                onViewTravelPlan(singaporeTrip)
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Header: Personalized Greeting & Demo Weather Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = greetingTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary,
                        fontSize = 26.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Your next adventure awaits.",
                        style = MaterialTheme.typography.bodyLarge,
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

        // Section Title: Selectable Travel Packages
        item {
            Column {
                Text(
                    text = "Selectable Travel Packages",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Choose an experience to view details and add it to My Trips.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoamlyTextSecondary
                )
            }
        }

        // -------------------------------------------------------------
        // TRIP OPTION A: SINGAPORE PACKAGE CARD
        // -------------------------------------------------------------
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, if (isSingaporeSelected) RoamlyPrimary else RoamlyBorder, RoundedCornerShape(20.dp))
            ) {
                Column {
                    // Singapore Destination Image Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.singapore_destination),
                            contentDescription = "Singapore Destination",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Subtle dark gradient for contrast
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.15f),
                                            Color.Black.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                        )

                        // Badges at top
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RoamlyPrimary)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "5-DAY PACKAGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoamlySurface,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            if (isSingaporeSelected) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RoamlySuccess)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = RoamlySurface,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "IN MY TRIPS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RoamlySurface,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Title overlaid at bottom of image
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Singapore",
                                style = MaterialTheme.typography.labelLarge,
                                color = RoamlySurface.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = singaporeTrip.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoamlySurface
                            )
                        }
                    }

                    // Card Body
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Package Price & Duration Info
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoamlyPrimaryContainer.copy(alpha = 0.35f))
                                .border(1.dp, RoamlyPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PACKAGE PRICE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoamlyPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "₹85,000–₹1,00,000",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                                Text(
                                    text = "Per person · ₹1.70–₹2.00L for 2 adults",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = RoamlyPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${singaporeTrip.durationDays} Days / 4 Nights",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyTextPrimary
                                    )
                                }
                                Text(
                                    text = singaporeTrip.dates,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = singaporeTrip.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextSecondary,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Itinerary Highlights Preview
                        Text(
                            text = "Itinerary Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val previewDays = listOf(
                            "Day 1" to "Marina Bay Sands SkyPark & Lau Pa Sat Hawker Market",
                            "Day 2" to "Gardens by the Bay & Cloud Forest",
                            "Day 3" to "Sentosa Island & Universal Studios Singapore",
                            "Day 4" to "Buddha Tooth Relic Temple & Kampong Glam",
                            "Day 5" to "Singapore Botanic Gardens & Jewel Changi Rain Vortex"
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            previewDays.forEach { (day, highlights) ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(RoamlySurfaceVariant)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = day,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = RoamlyPrimary,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = highlights,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RoamlyTextPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Actions: View Package & Add to My Trips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showSingaporeDetailsDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, RoamlyPrimary)
                            ) {
                                Text(
                                    text = "View Package",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
                            }

                            Button(
                                onClick = {
                                    repository.addTripToMyTrips(singaporeTrip.id)
                                    if (isSingaporeSelected) {
                                        onNavigateToTrips()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSingaporeSelected) RoamlySuccess else RoamlyPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isSingaporeSelected) Icons.Default.Check else Icons.Default.Map,
                                    contentDescription = null,
                                    tint = RoamlySurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSingaporeSelected) "In My Trips ✓" else "+ Add to My Trips",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlySurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // TRIP OPTION B: OUTSIDE OF TINKERSPACE CARD
        // -------------------------------------------------------------
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (isTinkerspaceSelected) RoamlyAccent else RoamlyBorder,
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header Badges & Option Label
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RoamlyAccentContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "DEMO JOURNEY",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoamlyAccent,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        if (isTinkerspaceSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RoamlySuccess)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = RoamlySurface,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "IN MY TRIPS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RoamlySurface,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Outside of Tinkerspace",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Tinkerspace · 1-Day Demonstration Route",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RoamlyAccent
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "A location-aware demonstration journey showing GPS boundary detection, live tracking outside and inside Tinkerspace, and belongings verification check before ending the trip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Waypoints list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(RoamlySurfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(RoamlyAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoamlySurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Outside Tinkerspace Building",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                                Text(
                                    text = "Outdoor arrival boundary detection",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(RoamlyAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "2",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoamlySurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Back Inside Tinkerspace Building",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                                Text(
                                    text = "Indoor return & belongings checklist check",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selection Button
                    // CRITICAL: Selecting does NOT launch the demo immediately; adds it to My Trips where user can launch it
                    if (isTinkerspaceSelected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(RoamlySuccess.copy(alpha = 0.15f))
                                    .border(1.dp, RoamlySuccess.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = RoamlySuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "In My Trips",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlySuccess
                                    )
                                }
                            }

                            Button(
                                onClick = onNavigateToTrips,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                            ) {
                                Text(
                                    text = "Go to My Trips",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                repository.addTripToMyTrips(tinkerspaceTrip.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = RoamlySurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Outside of Tinkerspace",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoamlySurface
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // QUICK TRAVEL ACTIONS
        // -------------------------------------------------------------
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
                            text = "What to Pack?",
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
                            text = "Travel Help",
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

                // ACTION 3: My Trips Shortcut
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
                        .clickable { onNavigateToTrips() },
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
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "My Trips",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedTripIds.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // TRAVEL REMINDERS
        // -------------------------------------------------------------
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

// =====================================================================
// SINGAPORE PACKAGE DETAILS MODAL / DIALOG
// =====================================================================
@Composable
private fun SingaporePackageDetailsDialog(
    trip: Trip,
    isSelected: Boolean,
    onDismiss: () -> Unit,
    onSelectTrip: () -> Unit,
    onOpenFullItinerary: () -> Unit
) {
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = RoamlySurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Header Image & Close Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.singapore_destination),
                        contentDescription = "Singapore Explorer",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Singapore",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = trip.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(modifier = Modifier.padding(20.dp)) {
                    // Date & Group info
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
                                text = "${trip.dates} (${trip.durationDays} days)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = RoamlyTextPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(RoamlyPrimaryContainer)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "5-Day Holiday",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoamlyPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = trip.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextSecondary,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    HorizontalDivider(color = RoamlyBorder)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Estimated Pricing Breakdown (from Expense.txt)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Estimated Package Price",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(RoamlyPrimaryContainer.copy(alpha = 0.35f))
                            .border(1.dp, RoamlyPrimary.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Per Person (Demo Package)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                                Text(
                                    text = "₹85,000–₹1,00,000",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Estimated for 2 Adults (Twin Sharing)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary
                                )
                                Text(
                                    text = "₹1.70–₹2.00 lakh",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Includes return flights, Hotel 1900 Chinatown, and major attraction admissions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Day-by-Day Schedule Highlights
                    Text(
                        text = "Day-by-Day Itinerary Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    trip.days.forEach { day ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(RoamlySurfaceVariant)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Day ${day.dayNumber} · ${day.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = RoamlyTextPrimary
                                )
                                Text(
                                    text = day.date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoamlyPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            day.destinations.forEach { dest ->
                                Row(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = null,
                                        tint = RoamlyPrimary,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "${dest.name} (${dest.timeSlot})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RoamlyTextPrimary
                                        )
                                        Text(
                                            text = dest.shortDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = RoamlyTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Meeting Point
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(RoamlySurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Meeting Point",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )
                            Text(
                                text = trip.meetingPoint,
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons inside Details
                    Button(
                        onClick = onOpenFullItinerary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
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
                            text = "Explore Full Interactive Schedule",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onSelectTrip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isSelected) RoamlySuccess else RoamlyPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isSelected) RoamlySuccess else RoamlyPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSelected) "In My Trips ✓" else "+ Add to My Trips",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) RoamlySuccess else RoamlyPrimary
                        )
                    }
                }
            }
        }
    }
}
