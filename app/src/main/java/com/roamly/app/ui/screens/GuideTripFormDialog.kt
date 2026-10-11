package com.roamly.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyError
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Validated values produced by [GuideTripFormDialog].
 * Only contains fields that exist on [Trip] and can be persisted by
 * RoamlyRepository / Supabase trips table. Price and cover image are
 * intentionally omitted: they have no column in the current schema.
 */
data class ValidatedTripForm(
    val title: String,
    val destination: String,
    val country: String,
    val summary: String,
    val datesLabel: String,
    val durationDays: Int,
    val meetingPoint: String,
    val maxGroupSize: Int,
    val travelerCount: Int,
    val status: TripStatus
)

private val DisplayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

private fun epochToLocalDate(epoch: Long?): LocalDate? {
    if (epoch == null) return null
    return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun localDateToEpoch(date: LocalDate): Long {
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideTripFormDialog(
    initialTrip: Trip? = null,
    isSaving: Boolean = false,
    serverError: String? = null,
    onDismiss: () -> Unit,
    onSubmit: (ValidatedTripForm) -> Unit
) {
    val isEdit = initialTrip != null

    var title by remember { mutableStateOf(initialTrip?.title ?: "") }
    var destination by remember { mutableStateOf(initialTrip?.destination ?: "") }
    var country by remember { mutableStateOf(initialTrip?.country ?: "") }
    var summary by remember { mutableStateOf(initialTrip?.summary ?: "") }
    var meetingPoint by remember { mutableStateOf(initialTrip?.meetingPoint ?: "") }
    var maxGroupSizeText by remember { mutableStateOf((initialTrip?.maxGroupSize ?: 10).toString()) }
    var travelerCountText by remember { mutableStateOf((initialTrip?.travelerCount ?: 0).toString()) }
    var status by remember { mutableStateOf(initialTrip?.status ?: TripStatus.UPCOMING) }

    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf<String?>(null) }
    var destinationError by remember { mutableStateOf<String?>(null) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var meetingError by remember { mutableStateOf<String?>(null) }
    var capacityError by remember { mutableStateOf<String?>(null) }
    var statusExpanded by remember { mutableStateOf(false) }

    fun validate(): ValidatedTripForm? {
        titleError = if (title.trim().isBlank()) "Trip title is required." else null
        destinationError = if (destination.trim().isBlank()) "Destination is required." else null
        summaryError = if (summary.trim().isBlank()) "Description is required." else null
        meetingError = if (meetingPoint.trim().isBlank()) "Meeting location is required." else null

        if (startDate == null || endDate == null) {
            // For edits, dates are optional: keep the existing label/duration.
            if (isEdit) {
                dateError = null
            } else {
                dateError = "Select a start and end date."
            }
        } else if (endDate!!.isBefore(startDate)) {
            dateError = "End date cannot be before the start date."
        } else {
            dateError = null
        }

        val maxGroup = maxGroupSizeText.trim().toIntOrNull()
        val travCount = travelerCountText.trim().toIntOrNull()
        capacityError = when {
            maxGroup == null -> "Enter a valid group size (1-100)."
            maxGroup !in 1..100 -> "Group size must be between 1 and 100."
            travCount == null -> "Enter a valid traveller count (0 or more)."
            travCount < 0 -> "Traveller count cannot be negative."
            travCount > maxGroup -> "Traveller count cannot exceed group size."
            else -> null
        }

        if (titleError != null || destinationError != null || summaryError != null ||
            dateError != null || meetingError != null || capacityError != null
        ) {
            return null
        }

        val resolvedCountry = country.trim().ifBlank { destination.trim() }
        val (datesLabel, durationDays) = if (startDate != null && endDate != null) {
            val days = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            val label = if (startDate == endDate) {
                startDate!!.format(DisplayFormatter)
            } else {
                "${startDate!!.format(DisplayFormatter)} – ${endDate!!.format(DisplayFormatter)}"
            }
            label to days.coerceAtLeast(1)
        } else {
            // Edit without touching dates: preserve existing values.
            (initialTrip?.dates ?: "") to (initialTrip?.durationDays ?: 1)
        }

        return ValidatedTripForm(
            title = title.trim(),
            destination = destination.trim(),
            country = resolvedCountry,
            summary = summary.trim(),
            datesLabel = datesLabel,
            durationDays = durationDays,
            meetingPoint = meetingPoint.trim(),
            maxGroupSize = maxGroup!!,
            travelerCount = travCount!!,
            status = status
        )
    }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = RoamlySurface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isEdit) "Edit trip" else "Create new trip",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = RoamlyTextPrimary
                )
                Text(
                    text = if (isEdit) {
                        "Update the details your travellers will see."
                    } else {
                        "Publish a trip your travellers can join."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoamlyTextSecondary
                )

                if (serverError != null) {
                    Text(
                        text = serverError,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyError
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; titleError = null },
                    label = { Text("Trip title *") },
                    placeholder = { Text("e.g. Singapore Explorer") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = { if (titleError != null) Text(titleError!!, color = RoamlyError) },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it; destinationError = null },
                        label = { Text("Destination *") },
                        placeholder = { Text("Singapore") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = destinationError != null,
                        supportingText = { if (destinationError != null) Text(destinationError!!, color = RoamlyError) },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country") },
                        placeholder = { Text("Singapore") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it; summaryError = null },
                    label = { Text("Description *") },
                    placeholder = { Text("What makes this trip special?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    isError = summaryError != null,
                    supportingText = { if (summaryError != null) Text(summaryError!!, color = RoamlyError) },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startDate?.format(DisplayFormatter) ?: if (isEdit) initialTrip?.dates ?: "" else "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isEdit) "Start date (optional)" else "Start date *") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { showStartPicker = true }, enabled = !isSaving) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick start date")
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = endDate?.format(DisplayFormatter) ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (isEdit) "End date (optional)" else "End date *") },
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { showEndPicker = true }, enabled = !isSaving) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick end date")
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (dateError != null) {
                    Text(text = dateError!!, style = MaterialTheme.typography.bodySmall, color = RoamlyError)
                } else if (startDate != null && endDate != null) {
                    Text(
                        text = "Duration: ${ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1} day(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                } else if (isEdit) {
                    Text(
                        text = "Current: ${initialTrip?.dates} (${initialTrip?.durationDays} days). Pick dates only to change the schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoamlyTextSecondary
                    )
                }

                OutlinedTextField(
                    value = meetingPoint,
                    onValueChange = { meetingPoint = it; meetingError = null },
                    label = { Text("Meeting location *") },
                    placeholder = { Text("e.g. Changi Airport T3 Arrival Hall") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = meetingError != null,
                    supportingText = { if (meetingError != null) Text(meetingError!!, color = RoamlyError) },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = maxGroupSizeText,
                        onValueChange = { maxGroupSizeText = it.filter { c -> c.isDigit() }.take(3); capacityError = null },
                        label = { Text("Max group *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = capacityError != null,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = travelerCountText,
                        onValueChange = { travelerCountText = it.filter { c -> c.isDigit() }.take(3); capacityError = null },
                        label = { Text("Travellers") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = capacityError != null,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                if (capacityError != null) {
                    Text(text = capacityError!!, style = MaterialTheme.typography.bodySmall, color = RoamlyError)
                }

                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { if (!isSaving) statusExpanded = !statusExpanded }
                ) {
                    OutlinedTextField(
                        value = status.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Trip status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        TripStatus.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = { status = option; statusExpanded = false }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val validated = validate()
                        if (validated != null) onSubmit(validated)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary),
                    enabled = !isSaving
                ) {
                    Text(
                        text = if (isSaving) "Saving…" else if (isEdit) "Save changes" else "Create trip",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = { if (!isSaving) onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Text("Cancel", color = RoamlyTextSecondary)
                }
            }
        }
    }

    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.let { localDateToEpoch(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = epochToLocalDate(state.selectedDateMillis)
                    dateError = null
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.let { localDateToEpoch(it) }
                ?: startDate?.let { localDateToEpoch(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = epochToLocalDate(state.selectedDateMillis)
                    dateError = null
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** Builds a new [Trip] owned by [guideId] from a validated form. */
fun validatedFormToNewTrip(form: ValidatedTripForm, guideId: String?): Trip {
    return Trip(
        id = "trip_" + UUID.randomUUID().toString().take(8),
        title = form.title,
        destination = form.destination,
        country = form.country,
        dates = form.datesLabel,
        durationDays = form.durationDays,
        travelerCount = form.travelerCount,
        travelers = emptyList(),
        status = form.status,
        summary = form.summary,
        days = emptyList(),
        meetingPoint = form.meetingPoint,
        announcements = listOf("Trip created! Review packing list and preparation documents."),
        maxGroupSize = form.maxGroupSize,
        isGuideManaged = true,
        guideId = guideId
    )
}
