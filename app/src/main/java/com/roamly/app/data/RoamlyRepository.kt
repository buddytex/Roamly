package com.roamly.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.roamly.app.model.ChatMessage
import com.roamly.app.model.ChatSender
import com.roamly.app.model.ChecklistCategory
import com.roamly.app.model.ChecklistItem
import com.roamly.app.model.DestinationItem
import com.roamly.app.model.TravellerRecord
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class RoamlyRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("roamly_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole.asStateFlow()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _currentTripId = MutableStateFlow<String>("trip_singapore_2026")
    val currentTripId: StateFlow<String> = _currentTripId.asStateFlow()

    private val _checklist = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val checklist: StateFlow<List<ChecklistItem>> = _checklist.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _travellers = MutableStateFlow<List<TravellerRecord>>(emptyList())
    val travellers: StateFlow<List<TravellerRecord>> = _travellers.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        // Load Selected Role
        val savedRole = prefs.getString(KEY_ROLE, null)
        _selectedRole.value = if (savedRole != null) {
            try { UserRole.valueOf(savedRole) } catch (e: Exception) { null }
        } else null

        // Load Trips with safe error handling
        try {
            val tripsJson = prefs.getString(KEY_TRIPS, null)
            if (tripsJson != null) {
                val type = object : TypeToken<List<Trip>>() {}.type
                val loaded: List<Trip>? = gson.fromJson(tripsJson, type)
                _trips.value = if (!loaded.isNullOrEmpty()) loaded else SampleData.sampleTrips
            } else {
                _trips.value = SampleData.sampleTrips
                saveTrips(_trips.value)
            }
        } catch (e: Exception) {
            _trips.value = SampleData.sampleTrips
        }

        // Load Checklist
        try {
            val checklistJson = prefs.getString(KEY_CHECKLIST, null)
            if (checklistJson != null) {
                val type = object : TypeToken<List<ChecklistItem>>() {}.type
                val loaded: List<ChecklistItem>? = gson.fromJson(checklistJson, type)
                _checklist.value = if (!loaded.isNullOrEmpty()) loaded else SampleData.sampleChecklist
            } else {
                _checklist.value = SampleData.sampleChecklist
                saveChecklist(_checklist.value)
            }
        } catch (e: Exception) {
            _checklist.value = SampleData.sampleChecklist
        }

        // Load Chat
        try {
            val chatJson = prefs.getString(KEY_CHAT, null)
            if (chatJson != null) {
                val type = object : TypeToken<List<ChatMessage>>() {}.type
                val loaded: List<ChatMessage>? = gson.fromJson(chatJson, type)
                _chatMessages.value = if (!loaded.isNullOrEmpty()) loaded else createInitialChat()
            } else {
                _chatMessages.value = createInitialChat()
                saveChat(_chatMessages.value)
            }
        } catch (e: Exception) {
            _chatMessages.value = createInitialChat()
        }

        // Load Travellers
        try {
            val travellersJson = prefs.getString(KEY_TRAVELLERS, null)
            if (travellersJson != null) {
                val type = object : TypeToken<List<TravellerRecord>>() {}.type
                val loaded: List<TravellerRecord>? = gson.fromJson(travellersJson, type)
                _travellers.value = if (!loaded.isNullOrEmpty()) loaded else SampleData.sampleTravellers
            } else {
                _travellers.value = SampleData.sampleTravellers
                saveTravellers(_travellers.value)
            }
        } catch (e: Exception) {
            _travellers.value = SampleData.sampleTravellers
        }
    }

    private fun createInitialChat(): List<ChatMessage> {
        return listOf(
            ChatMessage(
                id = "msg_welcome",
                sender = ChatSender.ROAMLY_AI,
                text = "Hello! 🌿 Welcome to Roamly Travel Help. I'm your offline travel assistant. Ask me anything about your trip, packing essentials, places to explore, or local precautions.",
                promptChips = listOf(
                    "What should I pack?",
                    "What can I visit today?",
                    "Near Marina Bay",
                    "What should I know before visiting?"
                )
            )
        )
    }

    // Role Management
    fun setUserRole(role: UserRole) {
        _selectedRole.value = role
        prefs.edit().putString(KEY_ROLE, role.name).apply()
    }

    fun clearUserRole() {
        _selectedRole.value = null
        prefs.edit().remove(KEY_ROLE).apply()
    }

    // Trip Selection & Management
    fun getCurrentTrip(): Trip? {
        return _trips.value.find { it.id == _currentTripId.value } ?: _trips.value.firstOrNull()
    }

    fun selectTrip(tripId: String) {
        _currentTripId.value = tripId
    }

    fun addTrip(trip: Trip) {
        val updated = listOf(trip) + _trips.value
        _trips.value = updated
        _currentTripId.value = trip.id
        saveTrips(updated)
    }

    fun updateTripStatus(tripId: String, newStatus: TripStatus) {
        val updated = _trips.value.map { trip ->
            if (trip.id == tripId) trip.copy(status = newStatus) else trip
        }
        _trips.value = updated
        saveTrips(updated)
    }

    fun updateTripMeetingPoint(tripId: String, meetingPoint: String) {
        val updated = _trips.value.map { trip ->
            if (trip.id == tripId) trip.copy(meetingPoint = meetingPoint.trim()) else trip
        }
        _trips.value = updated
        saveTrips(updated)
    }

    fun addTripAnnouncement(tripId: String, announcement: String) {
        if (announcement.isBlank()) return
        val updated = _trips.value.map { trip ->
            if (trip.id == tripId) {
                trip.copy(announcements = listOf(announcement.trim()) + trip.announcements)
            } else trip
        }
        _trips.value = updated
        saveTrips(updated)
    }

    // Checklist Operations
    fun toggleChecklistItem(id: String) {
        val updated = _checklist.value.map { item ->
            if (item.id == id) item.copy(isPacked = !item.isPacked) else item
        }
        _checklist.value = updated
        saveChecklist(updated)
    }

    fun addChecklistItem(title: String, category: ChecklistCategory, isBelonging: Boolean = false) {
        if (title.isBlank()) return
        val newItem = ChecklistItem(
            id = "item_" + UUID.randomUUID().toString().take(8),
            title = title.trim(),
            category = category,
            isPacked = false,
            isBelongingItem = isBelonging
        )
        val updated = _checklist.value + newItem
        _checklist.value = updated
        saveChecklist(updated)
    }

    fun deleteChecklistItem(id: String) {
        val updated = _checklist.value.filterNot { it.id == id }
        _checklist.value = updated
        saveChecklist(updated)
    }

    // Itinerary Activity & Visited Operations
    fun toggleActivity(tripId: String, destinationId: String, activityId: String) {
        val updatedTrips = _trips.value.map { trip ->
            if (trip.id == tripId) {
                val updatedDays = trip.days.map { day ->
                    val updatedDests = day.destinations.map { dest ->
                        if (dest.id == destinationId) {
                            val updatedActs = dest.activities.map { act ->
                                if (act.id == activityId) act.copy(isDone = !act.isDone) else act
                            }
                            dest.copy(activities = updatedActs)
                        } else dest
                    }
                    day.copy(destinations = updatedDests)
                }
                trip.copy(days = updatedDays)
            } else trip
        }
        _trips.value = updatedTrips
        saveTrips(updatedTrips)
    }

    fun markDestinationVisited(tripId: String, destinationId: String, isCompleted: Boolean) {
        val updatedTrips = _trips.value.map { trip ->
            if (trip.id == tripId) {
                val updatedDays = trip.days.map { day ->
                    val updatedDests = day.destinations.map { dest ->
                        if (dest.id == destinationId) {
                            dest.copy(isCompleted = isCompleted)
                        } else dest
                    }
                    day.copy(destinations = updatedDests)
                }
                trip.copy(days = updatedDays)
            } else trip
        }
        _trips.value = updatedTrips
        saveTrips(updatedTrips)
    }

    fun markDepartureReminderAcknowledged(tripId: String, destinationId: String) {
        val updatedTrips = _trips.value.map { trip ->
            if (trip.id == tripId) {
                val updatedDays = trip.days.map { day ->
                    val updatedDests = day.destinations.map { dest ->
                        if (dest.id == destinationId) {
                            dest.copy(departureReminderChecked = true)
                        } else dest
                    }
                    day.copy(destinations = updatedDests)
                }
                trip.copy(days = updatedDays)
            } else trip
        }
        _trips.value = updatedTrips
        saveTrips(updatedTrips)
    }

    // Chat Operations
    fun addChatMessage(message: ChatMessage) {
        val updated = _chatMessages.value + message
        _chatMessages.value = updated
        saveChat(updated)
    }

    // Guide Calculated Metric Helpers (derived directly from repository records)
    fun getUpcomingTripsCount(): Int {
        return _trips.value.count { it.status == TripStatus.UPCOMING }
    }

    fun getInProgressTripsCount(): Int {
        return _trips.value.count { it.status == TripStatus.IN_PROGRESS }
    }

    fun getCompletedTripsCount(): Int {
        return _trips.value.count { it.status == TripStatus.COMPLETED }
    }

    fun getPlanningTripsCount(): Int {
        return _trips.value.count { it.status == TripStatus.PLANNING }
    }

    fun getTotalTravellersCount(): Int {
        return _travellers.value.size
    }

    // Reset Demo Data
    fun resetToDemoData() {
        _trips.value = SampleData.sampleTrips
        saveTrips(_trips.value)
        _checklist.value = SampleData.sampleChecklist
        saveChecklist(_checklist.value)
        _travellers.value = SampleData.sampleTravellers
        saveTravellers(_travellers.value)
        _chatMessages.value = createInitialChat()
        saveChat(_chatMessages.value)
        _currentTripId.value = "trip_singapore_2026"
        _selectedRole.value = null
        prefs.edit().remove(KEY_ROLE).apply()
    }

    private fun saveTrips(trips: List<Trip>) {
        prefs.edit().putString(KEY_TRIPS, gson.toJson(trips)).apply()
    }

    private fun saveChecklist(checklist: List<ChecklistItem>) {
        prefs.edit().putString(KEY_CHECKLIST, gson.toJson(checklist)).apply()
    }

    private fun saveChat(messages: List<ChatMessage>) {
        prefs.edit().putString(KEY_CHAT, gson.toJson(messages)).apply()
    }

    private fun saveTravellers(travellers: List<TravellerRecord>) {
        prefs.edit().putString(KEY_TRAVELLERS, gson.toJson(travellers)).apply()
    }

    companion object {
        private const val KEY_ROLE = "key_role"
        private const val KEY_TRIPS = "key_trips"
        private const val KEY_CHECKLIST = "key_checklist"
        private const val KEY_CHAT = "key_chat"
        private const val KEY_TRAVELLERS = "key_travellers"

        @Volatile
        private var instance: RoamlyRepository? = null

        fun getInstance(context: Context): RoamlyRepository {
            return instance ?: synchronized(this) {
                instance ?: RoamlyRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
