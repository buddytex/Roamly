package com.railguard.mobile.tripmate.ui.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.railguard.mobile.tripmate.models.BelongingItem
import com.railguard.mobile.tripmate.models.Destination

@Composable
fun BelongingsReminderDialog(
    repository: TripMateRepository,
    currentDestination: Destination,
    belongings: List<BelongingItem>,
    onDismiss: () -> Unit,
    onProceedToNext: () -> Unit
) {
    var showMissingReportDialog by remember { mutableStateOf<BelongingItem?>(null) }
    var destinationCompletedConfirmed by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Departure Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Leaving: ${currentDestination.name}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "💡 Before leaving this location, check your personal belongings to ensure nothing is left behind.",
                            fontSize = 13.sp,
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = "Verify your essentials:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 220.dp).fillMaxWidth()) {
                    items(belongings) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (item.isReportedMissing) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(
                                    checked = item.isVerified,
                                    onCheckedChange = { repository.toggleBelongingItem(item.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF16A34A))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = item.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (item.isReportedMissing) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.isReportedMissing) {
                                        Text(
                                            text = "Reported Missing! Guide Alerted",
                                            fontSize = 11.sp,
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (!item.isReportedMissing) {
                                TextButton(
                                    onClick = { showMissingReportDialog = item },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Missing?", fontSize = 11.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = destinationCompletedConfirmed,
                        onCheckedChange = { destinationCompletedConfirmed = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mark '${currentDestination.name}' as completed",
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = "Note: Acknowledging departure retains your belongings checklist intact for subsequent stops.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    repository.acknowledgeDepartureReminder(currentDestination.id)
                    if (destinationCompletedConfirmed) {
                        repository.completeCurrentDestinationAndProceed()
                    }
                    onProceedToNext()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Acknowledge & Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )

    // Missing Item Dialog
    showMissingReportDialog?.let { missingItem ->
        AlertDialog(
            onDismissRequest = { showMissingReportDialog = null },
            icon = { Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626)) },
            title = { Text("Report Missing Item") },
            text = {
                Column {
                    Text(
                        text = "Are you sure you cannot find \"${missingItem.name}\"?",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Location will be recorded as: ${currentDestination.name}.\nThis will immediately log an assistance ticket with your Tour Lead (Sari Al-Mansoor) to help locate it.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.reportBelongingMissing(missingItem.id, currentDestination.name)
                        showMissingReportDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Report & Alert Guide")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMissingReportDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
