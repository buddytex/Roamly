package com.railguard.mobile.tripmate.ui.tourist

import androidx.compose.foundation.background
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
import com.railguard.mobile.tripmate.models.DestinationStatus
import com.railguard.mobile.tripmate.models.Trip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationDetailScreen(
    repository: TripMateRepository,
    trip: Trip,
    destination: Destination,
    onNavigateToAi: () -> Unit,
    onBack: () -> Unit
) {
    var showBelongingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destination.name, maxLines = 1, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToAi) {
                        Icon(Icons.Default.SmartToy, contentDescription = "AI Assistant", tint = Color(0xFF2563EB))
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
            // Status and timing banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (destination.status) {
                        DestinationStatus.CURRENT -> Color(0xFF2563EB).copy(alpha = 0.1f)
                        DestinationStatus.COMPLETED -> Color(0xFF16A34A).copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (destination.status) {
                                DestinationStatus.CURRENT -> Icons.Default.Navigation
                                DestinationStatus.COMPLETED -> Icons.Default.CheckCircle
                                else -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            tint = when (destination.status) {
                                DestinationStatus.CURRENT -> Color(0xFF2563EB)
                                DestinationStatus.COMPLETED -> Color(0xFF16A34A)
                                else -> Color.Gray
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = destination.status.name,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = when (destination.status) {
                                    DestinationStatus.CURRENT -> Color(0xFF2563EB)
                                    DestinationStatus.COMPLETED -> Color(0xFF16A34A)
                                    else -> Color.Gray
                                }
                            )
                            Text(destination.plannedTime, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        color = Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "📍 %.4f, %.4f".format(destination.latitude, destination.longitude),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.DarkGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Overview Section
            Text("Overview", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(destination.fullOverview, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(14.dp))

            // Why Visit Card
            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Why It's Essential", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1D4ED8))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(destination.whyVisit, fontSize = 13.sp, color = Color(0xFF1E3A8A), lineHeight = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Assembly / Meeting Point
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Assembly / Meeting Point", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Text(destination.meetingPoint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Planned Activities
            Text("Scheduled Activities", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            destination.activities.forEach { activity ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = activity.isCompleted,
                            onCheckedChange = { repository.toggleActivityCompleted(destination.id, activity.id) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF16A34A))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(activity.time, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(activity.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(activity.description, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Precautions Card
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Safety & Cultural Precautions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF991B1B))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    destination.precautions.forEach { prec ->
                        Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Text("⚠", fontSize = 12.sp, color = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(prec, fontSize = 13.sp, color = Color(0xFF7F1D1D), lineHeight = 18.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top Sights
            Text("Top Sights to Look For", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            destination.topSights.forEach { sight ->
                Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(sight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (destination.status == DestinationStatus.CURRENT) {
                Button(
                    onClick = { showBelongingsDialog = true },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FactCheck, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check Belongings & Depart Stop", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedButton(
                onClick = onNavigateToAi,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QuestionAnswer, contentDescription = null, tint = Color(0xFF2563EB))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ask AI Assistant About This Location", fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        if (showBelongingsDialog) {
            BelongingsReminderDialog(
                repository = repository,
                currentDestination = destination,
                belongings = trip.belongingsList,
                onDismiss = { showBelongingsDialog = false },
                onProceedToNext = {
                    showBelongingsDialog = false
                    onBack()
                }
            )
        }
    }
}
