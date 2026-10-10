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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.roamly.app.data.SampleData
import com.roamly.app.model.ChecklistCategory
import com.roamly.app.model.ChecklistItem
import com.roamly.app.ui.components.BelongingsReminderDialog
import com.roamly.app.ui.theme.RoamlyAccent
import com.roamly.app.ui.theme.RoamlyAccentContainer
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyError
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySuccess
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlySurfaceVariant
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary

@Composable
fun ChecklistsScreen(
    repository: RoamlyRepository
) {
    val checklist by repository.checklist.collectAsState()
    val trips by repository.trips.collectAsState()
    val currentTrip = repository.getCurrentTrip() ?: trips.firstOrNull()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Packing List, 1: Travel Preparation, 2: Belongings Reminder
    var selectedCategoryFilter by remember { mutableStateOf<ChecklistCategory?>(null) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showTestReminderDialog by remember { mutableStateOf(false) }

    val totalItems = checklist.size
    val checkedItems = checklist.count { it.isPacked }
    val progress = if (totalItems > 0) checkedItems.toFloat() / totalItems.toFloat() else 0f

    val categories = listOf(
        ChecklistCategory.DOCUMENTS,
        ChecklistCategory.CLOTHING,
        ChecklistCategory.ESSENTIALS,
        ChecklistCategory.ELECTRONICS,
        ChecklistCategory.HEALTH
    )

    if (showTestReminderDialog) {
        BelongingsReminderDialog(
            destinationName = "Current Location",
            onDismiss = { showTestReminderDialog = false },
            onConfirmed = { showTestReminderDialog = false }
        )
    }

    if (showAddItemDialog) {
        var newItemTitle by remember { mutableStateOf("") }
        var newItemCategory by remember { mutableStateOf(ChecklistCategory.ESSENTIALS) }

        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = {
                Text(
                    text = "Add an Item",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = newItemTitle,
                        onValueChange = { newItemTitle = it },
                        label = { Text("Item name") },
                        placeholder = { Text("e.g., Sun hat, Universal plug...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoamlyPrimary,
                            unfocusedBorderColor = RoamlyBorder
                        )
                    )

                    Text(
                        text = "Category:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RoamlyTextPrimary
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            val isSelected = newItemCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { newItemCategory = cat },
                                label = { Text(cat.displayName, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoamlyPrimary,
                                    selectedLabelColor = RoamlySurface
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemTitle.isNotBlank()) {
                            repository.addChecklistItem(newItemTitle, newItemCategory)
                            showAddItemDialog = false
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                ) {
                    Text("Add Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Cancel", color = RoamlyTextSecondary)
                }
            },
            containerColor = RoamlySurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
    ) {
        // Screen Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RoamlySurface)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .border(0.5.dp, RoamlyBorder)
        ) {
            Text(
                text = "Things to Remember",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = RoamlyTextPrimary
            )
            Text(
                text = "Organise your packing, documents, and non-destructive departure checks.",
                style = MaterialTheme.typography.bodySmall,
                color = RoamlyTextSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = RoamlySurface,
                contentColor = RoamlyPrimary,
                indicator = { tabPositions ->
                    androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = RoamlyPrimary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Packing List",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Preparation",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Belongings Check",
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // TAB 0: PACKING LIST
                val filteredItems = checklist.filter { item ->
                    selectedCategoryFilter == null || item.category == selectedCategoryFilter
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Card: "8 of 12 items checked."
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, RoamlyBorder, RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$checkedItems of $totalItems items checked",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyTextPrimary
                                    )
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = RoamlyPrimary,
                                    trackColor = RoamlySurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category Filters & Add Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Categories",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )

                            Button(
                                onClick = { showAddItemDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add an Item",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryFilter == null,
                                    onClick = { selectedCategoryFilter = null },
                                    label = { Text("All (${checklist.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoamlyPrimary,
                                        selectedLabelColor = RoamlySurface
                                    )
                                )
                            }
                            items(categories) { cat ->
                                val count = checklist.count { it.category == cat }
                                val isSelected = selectedCategoryFilter == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategoryFilter = if (isSelected) null else cat },
                                    label = { Text("${cat.displayName} ($count)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = RoamlyPrimary,
                                        selectedLabelColor = RoamlySurface
                                    )
                                )
                            }
                        }
                    }

                    items(filteredItems, key = { it.id }) { item ->
                        ChecklistItemRow(
                            item = item,
                            onToggle = { repository.toggleChecklistItem(item.id) },
                            onDelete = { repository.deleteChecklistItem(item.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            1 -> {
                // TAB 1: TRAVEL PREPARATION (3 Main Tasks)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Travel Preparation Tasks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                        Text(
                            text = "Complete these three essential steps before travelling.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextSecondary
                        )
                    }

                    // Task 1: Check My Documents
                    item {
                        PreparationTaskCard(
                            taskNumber = "1",
                            title = "Check My Documents",
                            description = "Submit your electronic SG Arrival Card (SGAC) within 3 days before entry. Ensure passport has at least 6 months validity.",
                            isDone = checkedItems >= 2,
                            actionLabel = "Review Official Guidance",
                            officialSource = "Official ICA Singapore Portal (https://eservices.ica.gov.sg/sgarrivalcard)"
                        )
                    }

                    // Task 2: Prepare My Packing List
                    item {
                        PreparationTaskCard(
                            taskNumber = "2",
                            title = "Prepare My Packing List",
                            description = "Review essentials: clothing, power adapters (Type G 3-pin for Singapore), medications, and electronics.",
                            isDone = checkedItems >= totalItems / 2,
                            actionLabel = "Open Packing List",
                            onClickAction = { selectedTab = 0 }
                        )
                    }

                    // Task 3: Review My Travel Plan
                    item {
                        PreparationTaskCard(
                            taskNumber = "3",
                            title = "Review My Travel Plan",
                            description = "Familiarise yourself with Day 1 Marina Bay and Day 2 Chinatown itineraries. Check activity opening hours and river cruise times.",
                            isDone = true,
                            actionLabel = "View Itinerary"
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = RoamlySurfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = RoamlyPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Official Advisory Disclaimer",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = RoamlyTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Roamly provides helpful checklists and direct links to government authorities. Completing this checklist does not issue or guarantee visas. Always verify entry conditions on official government portals.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoamlyTextSecondary,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            2 -> {
                // TAB 2: BELONGINGS REMINDER
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Belongings Check",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                        Text(
                            text = "A gentle, non-destructive check whenever you are preparing to leave a hotel, restaurant, or attraction.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextSecondary,
                            lineHeight = 22.sp
                        )
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = RoamlySurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, RoamlyAccent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(RoamlyAccentContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Luggage,
                                            contentDescription = null,
                                            tint = RoamlyAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "Before you leave, check that you have everything.",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = RoamlyTextPrimary
                                        )
                                        Text(
                                            text = "Non-destructive reminder • Your list is never cleared",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = RoamlyTextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Quickly verify your most critical belongings:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RoamlyTextPrimary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                val essentialBelongings = listOf(
                                    "Passport & Travel Documents",
                                    "Wallet & Credit Cards",
                                    "Smartphone & Charger",
                                    "Keys / Hotel Keycard",
                                    "Prescription Glasses / Sunglasses",
                                    "Water Bottle"
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    essentialBelongings.forEach { item ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = RoamlyPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = RoamlyTextPrimary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                Button(
                                    onClick = { showTestReminderDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Simulate Departure Check",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RoamlyBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Large touch target checkbox
            Checkbox(
                checked = item.isPacked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = RoamlyPrimary,
                    uncheckedColor = RoamlyBorder
                ),
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isPacked) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (item.isPacked) RoamlyTextSecondary else RoamlyTextPrimary,
                    fontSize = 17.sp
                )
                Text(
                    text = item.category.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoamlyTextSecondary,
                    fontSize = 12.sp
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete item",
                    tint = RoamlyTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PreparationTaskCard(
    taskNumber: String,
    title: String,
    description: String,
    isDone: Boolean,
    actionLabel: String,
    officialSource: String? = null,
    onClickAction: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RoamlySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RoamlyBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isDone) RoamlySuccess.copy(alpha = 0.2f) else RoamlyPrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = taskNumber,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDone) RoamlySuccess else RoamlyPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyTextPrimary
                    )
                }

                if (isDone) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RoamlySuccess.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RoamlySuccess
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = RoamlyTextSecondary,
                lineHeight = 20.sp
            )

            if (officialSource != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = RoamlyPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = officialSource,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (onClickAction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClickAction,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimaryContainer)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = RoamlyPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
