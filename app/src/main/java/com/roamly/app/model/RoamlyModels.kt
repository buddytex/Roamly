package com.roamly.app.model

enum class TripStatus {
    ACTIVE,
    UPCOMING,
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
    val travelers: List<String>,
    val status: TripStatus,
    val summary: String,
    val days: List<TripDay>
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
