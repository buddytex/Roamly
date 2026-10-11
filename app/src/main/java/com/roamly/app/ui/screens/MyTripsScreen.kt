package com.roamly.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.roamly.app.model.DestinationItem
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.ui.components.BelongingsReminderDialog
import com.roamly.app.ui.components.PlanNewTripDialog
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
fun MyTripsScreen(
    repository: RoamlyRepository,
    initialSelectedTripId: String? = null,
    openItineraryDirectly: Boolean = false,
    onNavigateToTripsLanding: (() -> Unit)? = null
) {
    val trips by repository.trips.collectAsState()
    val selectedTripIds by repository.selectedTripIds.collectAsState()
    val activeTripId by repository.currentTripId.collectAsState()

    val myTrips = remember(trips, selectedTripIds) {
        trips.filter { it.id in selectedTripIds }
    }

    var activeTripForItinerary by remember(
        initialSelectedTripId,
        openItineraryDirectly
    ) {
        mutableStateOf(
            if (openItineraryDirectly) {
                trips.find {
                    it.id == (initialSelectedTripId ?: activeTripId)
                } ?: trips.firstOrNull()
            } else {
                null
            }
        )
    }

    var showPlanTripDialog by remember { mutableStateOf(false) }

    var showBelongingsDialogForDest by remember {
        mutableStateOf<DestinationItem?>(null)
    }

    // Trekking simulation navigation state.
    var showTrekkingSimulation by remember {
        mutableStateOf(false)
    }

    // Show the trekking simulation independently of the selected itinerary.
    if (showTrekkingSimulation) {
        TrekkingSimulationScreen(
            onBack = {
                showTrekkingSimulation = false
            }
        )
        return
    }

    if (showPlanTripDialog) {
        PlanNewTripDialog(
            onDismiss = {
                showPlanTripDialog = false
            },
            onTripCreated = { newTrip ->
                repository.addTrip(newTrip)
                activeTripForItinerary = newTrip
                showPlanTripDialog = false
            },
            onPlanWithAi = { _ ->
                showPlanTripDialog = false
            }
        )
    }

    if (
        showBelongingsDialogForDest != null &&
        activeTripForItinerary != null
    ) {
        BelongingsReminderDialog(
            destinationName = showBelongingsDialogForDest!!.name,
            onDismiss = {
                showBelongingsDialogForDest = null
            },
            onConfirmed = {
                val trip = activeTripForItinerary
                val destination = showBelongingsDialogForDest

                if (trip != null && destination != null) {
                    repository.markDepartureReminderAcknowledged(
                        trip.id,
                        destination.id
                    )

                    repository.markDestinationVisited(
                        trip.id,
                        destination.id,
                        true
                    )
                }

                showBelongingsDialogForDest = null
            }
        )
    }

    if (activeTripForItinerary == null) {
        TripSelectionOverview(
            trips = myTrips,
            onSelectTrip = { trip ->
                repository.selectTrip(trip.id)
                activeTripForItinerary = trip
            },
            onPlanNewTrip = {
                showPlanTripDialog = true
            },
            onOpenTrekkingSimulation = {
                showTrekkingSimulation = true
            },
            onNavigateToTripsLanding = onNavigateToTripsLanding
        )
    } else {
        if (activeTripForItinerary?.id == "trip_tinkerspace_demo") {
            TinkerspaceDemoScreen(
                repository = repository,
                onBack = {
                    activeTripForItinerary = null
                },
                onReviewChecklist = {
                    activeTripForItinerary = null
                }
            )
        } else {
            DailyItineraryView(
                trip = activeTripForItinerary!!,
                repository = repository,
                onBackToTrips = {
                    activeTripForItinerary = null
                },
                onTriggerBelongingsReminder = { destination ->
                    showBelongingsDialogForDest = destination
                }
            )
        }
    }
}

@Composable
private fun TripSelectionOverview(
    trips: List<Trip>,
    onSelectTrip: (Trip) -> Unit,
    onPlanNewTrip: () -> Unit,
    onOpenTrekkingSimulation: () -> Unit,
    onNavigateToTripsLanding: (() -> Unit)? = null
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "My Trips",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary,
                        fontSize = 28.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Your selected travel experiences and demonstration journeys.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = RoamlyTextSecondary,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onPlanNewTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoamlyPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = RoamlySurface,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "+ Plan a New Trip",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoamlySurface
                )
            }

            // New trekking simulation button.
            Spacer(modifier = Modifier.height(2.dp))

            OutlinedButton(
                onClick = onOpenTrekkingSimulation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Start Trekking Simulation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RoamlyPrimaryContainer.copy(alpha = 0.4f))
                    .border(
                        1.dp,
                        RoamlyPrimary.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = RoamlyPrimary,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (trips.isEmpty()) {
                            "Select Singapore or Outside of Tinkerspace from the Trips screen."
                        } else {
                            "You have ${trips.size} active experience(s) in My Trips."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RoamlyTextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        if (trips.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = RoamlySurface
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            RoamlyBorder
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Luggage,
                            contentDescription = null,
                            tint = RoamlyPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Trips Selected Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Select a travel package or demonstration journey from the Trips screen to add it here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (onNavigateToTripsLanding != null) {
                            Button(
                                onClick = onNavigateToTripsLanding,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoamlyPrimary
                                )
                            ) {
                                Text("Browse Travel Packages")
                            }
                        }
                    }
                }
            }
        } else {
            items(
                items = trips,
                key = { it.id }
            ) { trip ->
                TripCardItem(
                    trip = trip,
                    onOpenTrip = {
                        onSelectTrip(trip)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TripCardItem(
    trip: Trip,
    onOpenTrip: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = RoamlySurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                RoamlyBorder,
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onOpenTrip)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TripStatusBadge(status = trip.status)

                if (trip.id == "trip_singapore_2026") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RoamlyPrimaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "5-Day Journey",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlyPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (trip.id == "trip_tinkerspace_demo") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RoamlyAccentContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Demo Journey",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoamlyAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = trip.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = trip.destination,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RoamlyPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = RoamlyTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (trip.dates.isNotBlank()) {
                            "${trip.dates} (${trip.durationDays} days)"
                        } else {
                            "${trip.durationDays} days"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = RoamlyTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = "${trip.travelerCount} travellers",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onOpenTrip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoamlyPrimary
                )
            ) {
                Text(
                    text = if (trip.id == "trip_tinkerspace_demo") {
                        "Launch Demo Journey"
                    } else {
                        "View Travel Itinerary"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoamlySurface
                )
            }
        }
    }
}

@Composable
private fun DailyItineraryView(
    trip: Trip,
    repository: RoamlyRepository,
    onBackToTrips: () -> Unit,
    onTriggerBelongingsReminder: (DestinationItem) -> Unit
) {
    val trips by repository.trips.collectAsState()
    val liveTrip = trips.find { it.id == trip.id } ?: trip

    var selectedDayIndex by remember {
        mutableIntStateOf(0)
    }

    val days = liveTrip.days

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RoamlySurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .border(0.5.dp, RoamlyBorder)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackToTrips
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Trips",
                        tint = RoamlyPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = liveTrip.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )

                    Text(
                        text = "${liveTrip.dates} • ${liveTrip.durationDays} Days",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }
            }
        }

        if (days.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "This trip doesn't have a daily plan yet.",
                    style = MaterialTheme.typography.titleMedium,
                    color = RoamlyTextSecondary
                )
            }
        } else {
            ScrollableTabRow(
                selectedTabIndex = selectedDayIndex.coerceIn(0, days.size - 1),
                containerColor = RoamlySurface,
                contentColor = RoamlyPrimary,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                days.forEachIndexed { index, day ->
                    val isSelected = selectedDayIndex == index

                    Tab(
                        selected = isSelected,
                        onClick = {
                            selectedDayIndex = index
                        },
                        text = {
                            Text(
                                text = "Day ${day.dayNumber}",
                                fontWeight = if (isSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                                fontSize = 15.sp,
                                color = if (isSelected) {
                                    RoamlyPrimary
                                } else {
                                    RoamlyTextSecondary
                                }
                            )
                        }
                    )
                }
            }

            val currentDay = days.getOrNull(selectedDayIndex) ?: days.first()
            val totalDestinations = currentDay.destinations.size
            val visitedDestinations =
                currentDay.destinations.count { it.isCompleted }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = currentDay.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = currentDay.date,
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(RoamlyPrimaryContainer)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$visitedDestinations of $totalDestinations places visited today",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyPrimary
                            )
                        }
                    }
                }

                items(
                    items = currentDay.destinations,
                    key = { it.id }
                ) { destination ->
                    ActivityDestinationCard(
                        destination = destination,
                        onToggleVisited = { isDone ->
                            repository.markDestinationVisited(
                                liveTrip.id,
                                destination.id,
                                isDone
                            )
                        },
                        onDepartLocation = {
                            onTriggerBelongingsReminder(destination)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityDestinationCard(
    destination: DestinationItem,
    onToggleVisited: (Boolean) -> Unit,
    onDepartLocation: () -> Unit
) {
    var isExpanded by remember {
        mutableStateOf(false)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = RoamlySurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                if (destination.isCompleted) {
                    RoamlySuccess.copy(alpha = 0.5f)
                } else {
                    RoamlyBorder
                },
                RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RoamlyPrimaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(14.dp)
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            Text(
                                text = "${destination.timeSlot} • ${destination.duration}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = destination.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        onToggleVisited(!destination.isCompleted)
                    }
                ) {
                    Checkbox(
                        checked = destination.isCompleted,
                        onCheckedChange = onToggleVisited,
                        colors = CheckboxDefaults.colors(
                            checkedColor = RoamlySuccess,
                            uncheckedColor = RoamlyBorder
                        )
                    )

                    Text(
                        text = if (destination.isCompleted) {
                            "Visited"
                        } else {
                            "Mark visited"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (destination.isCompleted) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (destination.isCompleted) {
                            RoamlySuccess
                        } else {
                            RoamlyTextSecondary
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = destination.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary,
                fontSize = 19.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = destination.shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = RoamlyTextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            isExpanded = !isExpanded
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide details" else "View details",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyPrimary
                    )

                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = null,
                        tint = RoamlyPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                OutlinedButton(
                    onClick = onDepartLocation,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RoamlyAccent
                    ),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Luggage,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "Departing Stop",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RoamlySurfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = destination.fullDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoamlyTextPrimary,
                        lineHeight = 20.sp
                    )

                    if (destination.practicalTips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = RoamlyAccent,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Practical Tips",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        destination.practicalTips.forEach { tip ->
                            Text(
                                text = "• $tip",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary,
                                modifier = Modifier.padding(
                                    start = 8.dp,
                                    bottom = 2.dp
                                )
                            )
                        }
                    }

                    if (destination.precautions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RoamlyPrimary,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            Text(
                                text = "Accessibility & Advice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        destination.precautions.forEach { precaution ->
                            Text(
                                text = "• $precaution",
                                style = MaterialTheme.typography.bodySmall,
                                color = RoamlyTextSecondary,
                                modifier = Modifier.padding(
                                    start = 8.dp,
                                    bottom = 2.dp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripStatusBadge(
    status: TripStatus
) {
    val (backgroundColor, textColor) = when (status) {
        TripStatus.PLANNING ->
            Pair(RoamlySurfaceVariant, RoamlyTextSecondary)

        TripStatus.UPCOMING ->
            Pair(RoamlyPrimaryContainer, RoamlyPrimary)

        TripStatus.IN_PROGRESS ->
            Pair(RoamlyAccentContainer, RoamlyAccent)

        TripStatus.COMPLETED ->
            Pair(RoamlySuccess.copy(alpha = 0.15f), RoamlySuccess)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}