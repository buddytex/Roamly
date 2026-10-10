package com.railguard.mobile.tripmate.ui.tourist

import androidx.compose.foundation.background
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
import com.railguard.mobile.tripmate.models.Trip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingChecklistScreen(
    repository: TripMateRepository,
    trip: Trip
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newItemTitle by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf("Personal Essentials") }

    val packedCount = trip.packingList.count { it.isPacked }
    val totalCount = trip.packingList.size
    val progress = if (totalCount > 0) packedCount.toFloat() / totalCount else 0f

    val categories = remember(trip.packingList) {
        trip.packingList.map { it.category }.distinct()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Packing Item")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Header Card with Progress Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Trip Preparation", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                            Text("Smart Packing Checklist", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            color = if (progress == 1f) Color(0xFF16A34A) else Color(0xFF2563EB),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$packedCount / $totalCount",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (progress == 1f) Color(0xFF16A34A) else Color(0xFF2563EB),
                        trackColor = Color.LightGray.copy(alpha = 0.4f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (progress == 1f) "✓ Everything packed and verified!" else "${totalCount - packedCount} items remaining before departure",
                        fontSize = 12.sp,
                        color = if (progress == 1f) Color(0xFF16A34A) else Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Categorized List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                categories.forEach { category ->
                    val categoryItems = trip.packingList.filter { it.category == category }
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.LightGray.copy(alpha = 0.4f))
                        }
                    }

                    items(categoryItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isPacked) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = item.isPacked,
                                        onCheckedChange = { repository.togglePackingItem(item.id) },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF16A34A))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = item.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (item.isPacked) FontWeight.Normal else FontWeight.Medium,
                                            color = if (item.isPacked) Color.Gray else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (item.isRequiredByGuide) {
                                            Text(
                                                text = "Required by Tour Guide",
                                                fontSize = 11.sp,
                                                color = Color(0xFFD97706),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                if (item.isCustom) {
                                    IconButton(
                                        onClick = { repository.removePackingItem(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(70.dp))
                }
            }
        }

        // Add Custom Item Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Packing Item") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newItemTitle,
                            onValueChange = { newItemTitle = it },
                            label = { Text("Item Name") },
                            placeholder = { Text("e.g. Extra Sun Hat, Power Cord") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newItemCategory,
                            onValueChange = { newItemCategory = it },
                            label = { Text("Category") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newItemTitle.isNotBlank()) {
                                repository.addCustomPackingItem(newItemTitle.trim(), newItemCategory.trim())
                                newItemTitle = ""
                                showAddDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
