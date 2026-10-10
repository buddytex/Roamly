package com.roamly.app.model

enum class UserRole(val displayName: String) {
    TRAVELLER("Traveller"),
    GUIDE("Tour Guide")
}

enum class TripStatus(val displayName: String) {
    PLANNING("Planning"),
    UPCOMING("Upcoming"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

enum class TinkerspaceWorkflowState {
    UPCOMING,
    IN_PROGRESS,
    OUTDOOR_STOP_REACHED,
    INDOOR_STOP_REACHED,
    AWAITING_BELONGINGS_CHECK,
    AWAITING_FINAL_CONFIRMATION,
    COMPLETED
}

enum class ChecklistCategory(val displayName: String) {
    DOCUMENTS("Travel Documents"),
    ESSENTIALS("Daily Essentials"),
    CLOTHING("Clothing & Footwear"),
    ELECTRONICS("Tech & Gear"),
    HEALTH("Health & Toiletries"),
    ACTIVITIES("Activity Specific")
}

enum class ChatSender {
    USER,
    ROAMLY_AI
}

data class ActivityItem(
    val id: String,
    val title: String,
    val isDone: Boolean = false
)

data class DestinationItem(
    val id: String,
    val name: String,
    val category: String,
    val timeSlot: String,
    val duration: String,
    val shortDescription: String,
    val fullDescription: String,
    val practicalTips: List<String> = emptyList(),
    val precautions: List<String> = emptyList(),
    val activities: List<ActivityItem> = emptyList(),
    val isCompleted: Boolean = false,
    val departureReminderChecked: Boolean = false,
    val locationTag: String = "Singapore"
)

data class TripDay(
    val dayNumber: Int,
    val title: String,
    val date: String,
    val destinations: List<DestinationItem>
)

data class Trip(
    val id: String,
    val title: String,
    val destination: String,
    val country: String,
    val dates: String,
    val durationDays: Int,
    val travelerCount: Int,
    val travelers: List<String> = emptyList(),
    val status: TripStatus,
    val summary: String,
    val days: List<TripDay> = emptyList(),
    val meetingPoint: String = "Hotel Lobby / Airport Arrival",
    val announcements: List<String> = emptyList(),
    val maxGroupSize: Int = 10,
    val isGuideManaged: Boolean = true
)

data class ChecklistItem(
    val id: String,
    val title: String,
    val category: ChecklistCategory,
    val isPacked: Boolean = false,
    val isBelongingItem: Boolean = false
)

data class ChatMessage(
    val id: String,
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val promptChips: List<String> = emptyList()
)

data class DocumentGuide(
    val title: String,
    val description: String,
    val officialSource: String,
    val officialUrl: String
)

data class TravellerRecord(
    val id: String,
    val name: String,
    val assignedTripId: String,
    val assignedTripName: String,
    val preparationStatus: String, // "Ready", "Preparation Incomplete", "Documents Pending"
    val packedItemsCount: Int,
    val totalItemsCount: Int,
    val visitedActivitiesCount: Int,
    val totalActivitiesCount: Int,
    val contactPhone: String = "+65 8123 4567"
)
