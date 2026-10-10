package com.roamly.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.roamly.app.model.ChatSender
import com.roamly.app.model.ChatMessage
import com.roamly.app.model.ChecklistCategory
import com.roamly.app.model.ChecklistItem
import com.roamly.app.model.DestinationItem
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class RoamlyRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("roamly_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _currentTripId = MutableStateFlow<String>("trip_singapore_2026")
    val currentTripId: StateFlow<String> = _currentTripId.asStateFlow()

    private val _checklist = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val checklist: StateFlow<List<ChecklistItem>> = _checklist.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val tripsJson = prefs.getString(KEY_TRIPS, null)
        if (tripsJson != null) {
            val type = object : TypeToken<List<Trip>>() {}.type
            _trips.value = gson.fromJson(tripsJson, type) ?: SampleData.sampleTrips
        } else {
            _trips.value = SampleData.sampleTrips
            saveTrips(_trips.value)
        }

        val checklistJson = prefs.getString(KEY_CHECKLIST, null)
        if (checklistJson != null) {
            val type = object : TypeToken<List<ChecklistItem>>() {}.type
            _checklist.value = gson.fromJson(checklistJson, type) ?: SampleData.sampleChecklist
        } else {
            _checklist.value = SampleData.sampleChecklist
            saveChecklist(_checklist.value)
        }

        val chatJson = prefs.getString(KEY_CHAT, null)
        if (chatJson != null) {
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            _chatMessages.value = gson.fromJson(chatJson, type) ?: createInitialChat()
        } else {
            _chatMessages.value = createInitialChat()
            saveChat(_chatMessages.value)
        }
    }

    private fun createInitialChat(): List<ChatMessage> {
        return listOf(
            ChatMessage(
                id = "msg_welcome",
                sender = ChatSender.ROAMLY_AI,
                text = "Hello Alex & Maya! 🌿 I am your Roamly travel companion for Singapore. I have your 5-day itinerary, packing checklist, and local neighborhood guides ready. How can I help you prepare today?",
                promptChips = listOf(
                    "What should I explore near Marina Bay?",
                    "What can I do with three hours free?",
                    "What should I pack for Singapore?",
                    "Explain the history of Singapore simply"
                )
            )
        )
    }

    fun getCurrentTrip(): Trip? {
        return _trips.value.find { it.id == _currentTripId.value } ?: _trips.value.firstOrNull()
    }

    fun selectTrip(tripId: String) {
        _currentTripId.value = tripId
    }

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

    fun addChatMessage(message: ChatMessage) {
        val updated = _chatMessages.value + message
        _chatMessages.value = updated
        saveChat(updated)
    }

    fun resetToDemoData() {
        _trips.value = SampleData.sampleTrips
        saveTrips(_trips.value)
        _checklist.value = SampleData.sampleChecklist
        saveChecklist(_checklist.value)
        _chatMessages.value = createInitialChat()
        saveChat(_chatMessages.value)
        _currentTripId.value = "trip_singapore_2026"
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

    companion object {
        private const val KEY_TRIPS = "key_trips"
        private const val KEY_CHECKLIST = "key_checklist"
        private const val KEY_CHAT = "key_chat"

        @Volatile
        private var instance: RoamlyRepository? = null

        fun getInstance(context: Context): RoamlyRepository {
            return instance ?: synchronized(this) {
                instance ?: RoamlyRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
