package com.railguard.mobile.tripmate.models

import java.util.UUID

enum class UserRole {
    GUIDE,
    TOURIST
}

enum class TripStatus {
    UPCOMING,
    ACTIVE,
    COMPLETED
}

enum class DestinationStatus {
    UPCOMING,
    CURRENT,
    COMPLETED,
    SKIPPED
}

enum class SOSStatus {
    REPORTED_PENDING_ACK,
    ACKNOWLEDGED,
    RESOLVED
}

data class ActivityItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val time: String,
    val description: String,
    val isCompleted: Boolean = false
)

data class Destination(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val shortDescription: String,
    val fullOverview: String,
    val whyVisit: String,
    val plannedTime: String,
    val meetingPoint: String,
    val latitude: Double,
    val longitude: Double,
    val status: DestinationStatus = DestinationStatus.UPCOMING,
    val activities: List<ActivityItem> = emptyList(),
    val instructions: List<String> = emptyList(),
    val precautions: List<String> = emptyList(),
    val topSights: List<String> = emptyList(),
    val suggestedExploration: List<String> = emptyList(),
    val departureReminderAcknowledged: Boolean = false
)

data class PackingItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: String,
    val isPacked: Boolean = false,
    val isCustom: Boolean = false,
    val isRequiredByGuide: Boolean = false
)

data class BelongingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isVerified: Boolean = false,
    val isReportedMissing: Boolean = false,
    val lastSeenLocation: String = ""
)

data class Traveler(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val currentStop: String,
    val lastCheckInTime: String,
    val isCheckInOverdue: Boolean = false,
    val hasPendingSos: Boolean = false,
    val volunteeredLat: Double? = null,
    val volunteeredLng: Double? = null
)

data class SOSIncident(
    val id: String = UUID.randomUUID().toString(),
    val tripId: String,
    val travelerName: String,
    val travelerPhone: String,
    val destinationName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: SOSStatus = SOSStatus.REPORTED_PENDING_ACK,
    val message: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isOfflineStored: Boolean = false
)

data class TripAnnouncement(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: String,
    val isUrgent: Boolean = false
)

data class Trip(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val agencyName: String = "Voyage Horizon Tours",
    val guideName: String = "Sari Al-Mansoor",
    val guideContact: String = "+971 50 123 4567",
    val startDate: String,
    val meetingPoint: String,
    val status: TripStatus = TripStatus.ACTIVE,
    val destinations: List<Destination> = emptyList(),
    val travelers: List<Traveler> = emptyList(),
    val packingList: List<PackingItem> = emptyList(),
    val belongingsList: List<BelongingItem> = emptyList(),
    val announcements: List<TripAnnouncement> = emptyList(),
    val currentDestinationIndex: Int = 0
)
