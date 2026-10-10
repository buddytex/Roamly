package com.railguard.mobile.tripmate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.railguard.mobile.tripmate.ai.TripMateAiAssistant
import com.railguard.mobile.tripmate.data.TripMateRepository
import com.railguard.mobile.tripmate.models.Destination
import com.railguard.mobile.tripmate.models.UserRole
import com.railguard.mobile.tripmate.ui.guide.GuideDashboardScreen
import com.railguard.mobile.tripmate.ui.guide.TripCreateEditScreen
import com.railguard.mobile.tripmate.ui.tourist.*

enum class TripMateTab {
    HOME,
    ITINERARY,
    DESTINATION_GUIDE,
    CHECKLIST,
    AI_ASSISTANT,
    SOS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMateApp(
    repository: TripMateRepository,
    onOpenDiagnostics: () -> Unit = {}
) {
    val activeRole by repository.activeRole.collectAsStateWithLifecycle()
    val trips by repository.trips.collectAsStateWithLifecycle()
    val sosIncidents by repository.sosIncidents.collectAsStateWithLifecycle()

    val activeTrip = repository.getActiveTrip()
    val aiAssistant = remember { TripMateAiAssistant() }

    var currentTab by remember { mutableStateOf(TripMateTab.HOME) }
    var selectedDestinationForDetail by remember { mutableStateOf<Destination?>(null) }
    var showCreateTripScreen by remember { mutableStateOf(false) }

    // If guide creates a trip, or user returns from detail
    if (showCreateTripScreen) {
        TripCreateEditScreen(
            repository = repository,
            onBack = { showCreateTripScreen = false }
        )
        return
    }

    if (selectedDestinationForDetail != null && activeTrip != null) {
        DestinationDetailScreen(
            repository = repository,
            trip = activeTrip,
            destination = selectedDestinationForDetail!!,
            onNavigateToAi = {
                selectedDestinationForDetail = null
                currentTab = TripMateTab.AI_ASSISTANT
            },
            onBack = { selectedDestinationForDetail = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TripMate",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Color(0xFF2563EB)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color.LightGray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "LAYOVER '26",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // DEMO ROLE SELECTOR CHIP (Seamless switching between Tourist and Guide)
                    Surface(
                        color = if (activeRole == UserRole.GUIDE) Color(0xFF16A34A).copy(alpha = 0.15f) else Color(0xFF2563EB).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable {
                                val nextRole = if (activeRole == UserRole.TOURIST) UserRole.GUIDE else UserRole.TOURIST
                                repository.setRole(nextRole)
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (activeRole == UserRole.GUIDE) Color(0xFF16A34A) else Color(0xFF2563EB))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeRole == UserRole.GUIDE) "GUIDE MODE" else "TOURIST MODE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeRole == UserRole.GUIDE) Color(0xFF16A34A) else Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = "Switch Role",
                                modifier = Modifier.size(14.dp),
                                tint = if (activeRole == UserRole.GUIDE) Color(0xFF16A34A) else Color(0xFF2563EB)
                            )
                        }
                    }

                    // Diagnostic Hardware / Robot screen access (Preserves RailGuard foundation)
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(
                            Icons.Default.Sensors,
                            contentDescription = "Hardware Sensors",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                // Tab 1: Home / Dashboard
                NavigationBarItem(
                    selected = currentTab == TripMateTab.HOME,
                    onClick = { currentTab = TripMateTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(if (activeRole == UserRole.GUIDE) "Dashboard" else "Home", fontSize = 10.sp) }
                )

                // Tab 2: Itinerary
                NavigationBarItem(
                    selected = currentTab == TripMateTab.ITINERARY,
                    onClick = { currentTab = TripMateTab.ITINERARY },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text("Itinerary", fontSize = 10.sp) }
                )

                // Tab 3: Destination Guide
                NavigationBarItem(
                    selected = currentTab == TripMateTab.DESTINATION_GUIDE,
                    onClick = {
                        val current = activeTrip?.destinations?.getOrNull(activeTrip.currentDestinationIndex)
                        if (current != null) {
                            selectedDestinationForDetail = current
                        } else {
                            currentTab = TripMateTab.DESTINATION_GUIDE
                        }
                    },
                    icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                    label = { Text("Guide", fontSize = 10.sp) }
                )

                // Tab 4: Checklist
                NavigationBarItem(
                    selected = currentTab == TripMateTab.CHECKLIST,
                    onClick = { currentTab = TripMateTab.CHECKLIST },
                    icon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                    label = { Text("Packing", fontSize = 10.sp) }
                )

                // Tab 5: AI Assistant
                NavigationBarItem(
                    selected = currentTab == TripMateTab.AI_ASSISTANT,
                    onClick = { currentTab = TripMateTab.AI_ASSISTANT },
                    icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                    label = { Text("AI Guide", fontSize = 10.sp) }
                )

                // Tab 6: SOS
                NavigationBarItem(
                    selected = currentTab == TripMateTab.SOS,
                    onClick = { currentTab = TripMateTab.SOS },
                    icon = {
                        Badge(containerColor = if (sosIncidents.any { it.status == com.railguard.mobile.tripmate.models.SOSStatus.REPORTED_PENDING_ACK }) Color(0xFFDC2626) else Color.Transparent) {
                            Icon(Icons.Default.Emergency, contentDescription = null, tint = Color(0xFFDC2626))
                        }
                    },
                    label = { Text("SOS", fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                TripMateTab.HOME -> {
                    if (activeRole == UserRole.GUIDE) {
                        GuideDashboardScreen(
                            repository = repository,
                            trip = activeTrip,
                            sosIncidents = sosIncidents,
                            onCreateTrip = { showCreateTripScreen = true }
                        )
                    } else {
                        TouristHomeScreen(
                            repository = repository,
                            trip = activeTrip,
                            onNavigateToItinerary = { currentTab = TripMateTab.ITINERARY },
                            onNavigateToDestination = { dest -> selectedDestinationForDetail = dest },
                            onNavigateToChecklist = { currentTab = TripMateTab.CHECKLIST },
                            onNavigateToAi = { currentTab = TripMateTab.AI_ASSISTANT },
                            onNavigateToSos = { currentTab = TripMateTab.SOS }
                        )
                    }
                }

                TripMateTab.ITINERARY -> {
                    if (activeTrip != null) {
                        ItineraryScreen(
                            repository = repository,
                            trip = activeTrip,
                            onSelectDestination = { dest -> selectedDestinationForDetail = dest }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No active trip available.")
                        }
                    }
                }

                TripMateTab.DESTINATION_GUIDE -> {
                    val current = activeTrip?.destinations?.getOrNull(activeTrip.currentDestinationIndex)
                    if (current != null && activeTrip != null) {
                        DestinationDetailScreen(
                            repository = repository,
                            trip = activeTrip,
                            destination = current,
                            onNavigateToAi = { currentTab = TripMateTab.AI_ASSISTANT },
                            onBack = { currentTab = TripMateTab.HOME }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Select a destination from the itinerary to view its guide.")
                        }
                    }
                }

                TripMateTab.CHECKLIST -> {
                    if (activeTrip != null) {
                        PackingChecklistScreen(
                            repository = repository,
                            trip = activeTrip
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No packing checklist available.")
                        }
                    }
                }

                TripMateTab.AI_ASSISTANT -> {
                    AiAssistantScreen(
                        trip = activeTrip,
                        aiAssistant = aiAssistant
                    )
                }

                TripMateTab.SOS -> {
                    SosScreen(
                        repository = repository,
                        trip = activeTrip,
                        incidents = sosIncidents
                    )
                }
            }
        }
    }
}
