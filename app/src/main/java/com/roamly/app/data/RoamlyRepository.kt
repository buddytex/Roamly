package com.roamly.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.roamly.app.auth.data.SupabaseAuthRepository
import com.roamly.app.auth.network.DemoEventDto
import com.roamly.app.auth.network.DemoSessionDto
import com.roamly.app.model.ChatMessage
import com.roamly.app.model.ChatSender
import com.roamly.app.model.ChecklistCategory
import com.roamly.app.model.ChecklistItem
import com.roamly.app.model.DemoEventRecord
import com.roamly.app.model.DemoStatus
import com.roamly.app.model.DestinationItem
import com.roamly.app.model.TinkerspaceWorkflowState
import com.roamly.app.model.TravellerDemoStatus
import com.roamly.app.model.TravellerRecord
import com.roamly.app.model.Trip
import com.roamly.app.model.TripStatus
import com.roamly.app.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class RoamlyRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("roamly_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _selectedRole = MutableStateFlow<UserRole?>(null)
    val selectedRole: StateFlow<UserRole?> = _selectedRole.asStateFlow()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _selectedTripIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTripIds: StateFlow<Set<String>> = _selectedTripIds.asStateFlow()

    private val _currentTripId = MutableStateFlow<String>("trip_singapore_2026")
    val currentTripId: StateFlow<String> = _currentTripId.asStateFlow()

    private val _checklist = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val checklist: StateFlow<List<ChecklistItem>> = _checklist.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _travellers = MutableStateFlow<List<TravellerRecord>>(emptyList())
    val travellers: StateFlow<List<TravellerRecord>> = _travellers.asStateFlow()

    private val _tinkerspaceWorkflowState = MutableStateFlow<TinkerspaceWorkflowState>(TinkerspaceWorkflowState.UPCOMING)
    val tinkerspaceWorkflowState: StateFlow<TinkerspaceWorkflowState> = _tinkerspaceWorkflowState.asStateFlow()

    private val _tinkerspaceIsSimulated = MutableStateFlow<Boolean>(false)
    val tinkerspaceIsSimulated: StateFlow<Boolean> = _tinkerspaceIsSimulated.asStateFlow()

    private val _tinkerspaceBelongingsCheckedTime = MutableStateFlow<Long>(0L)
    val tinkerspaceBelongingsCheckedTime: StateFlow<Long> = _tinkerspaceBelongingsCheckedTime.asStateFlow()

    private val _activeDemoStatuses = MutableStateFlow<List<TravellerDemoStatus>>(emptyList())
    val activeDemoStatuses: StateFlow<List<TravellerDemoStatus>> = _activeDemoStatuses.asStateFlow()

    private var lastRecordedEventType: String? = null

    init {
        loadData()
    }


    private fun loadData() {
        // Load Selected Role
        val savedRole = prefs.getString(KEY_ROLE, null)
        _selectedRole.value = if (savedRole != null) {
            try { UserRole.valueOf(savedRole) } catch (e: Exception) { null }
        } else null

        // Load Selected Trip IDs for Traveller
        try {
            val savedSelected = prefs.getStringSet(KEY_SELECTED_TRIP_IDS, null)
            _selectedTripIds.value = savedSelected?.toSet() ?: emptySet()
        } catch (e: Exception) {
            _selectedTripIds.value = emptySet()
        }

        // Load Trips with safe error handling
        try {
            val tripsJson = prefs.getString(KEY_TRIPS, null)
            if (tripsJson != null) {
                val type = object : TypeToken<List<Trip>>() {}.type
                val loaded: List<Trip>? = gson.fromJson(tripsJson, type)
                val validTrips = loaded?.filter {
                    it.id == "trip_singapore_2026" || it.id == "trip_tinkerspace_demo" || (!it.id.startsWith("trip_penang") && !it.id.startsWith("trip_kyoto"))
                }?.toMutableList() ?: mutableListOf()

                if (validTrips.none { it.id == "trip_singapore_2026" }) {
                    validTrips.add(0, SampleData.singaporeTrip)
                }
                if (validTrips.none { it.id == "trip_tinkerspace_demo" }) {
                    validTrips.add(SampleData.tinkerspaceTrip)
                }
                _trips.value = validTrips
                saveTrips(validTrips)
            } else {
                _trips.value = SampleData.sampleTrips
                saveTrips(_trips.value)
            }
        } catch (e: Exception) {
            _trips.value = SampleData.sampleTrips
        }

        // Load Tinkerspace Workflow State
        try {
            val savedTinkerState = prefs.getString(KEY_TINKERSPACE_WORKFLOW_STATE, null)
            _tinkerspaceWorkflowState.value = if (savedTinkerState != null) {
                try {
                    TinkerspaceWorkflowState.valueOf(savedTinkerState)
                } catch (e: Exception) {
                    TinkerspaceWorkflowState.UPCOMING
                }
            } else {
                val tinkerTrip = _trips.value.find { it.id == "trip_tinkerspace_demo" }
                when (tinkerTrip?.status) {
                    TripStatus.COMPLETED -> TinkerspaceWorkflowState.COMPLETED
                    TripStatus.IN_PROGRESS -> TinkerspaceWorkflowState.IN_PROGRESS
                    else -> TinkerspaceWorkflowState.UPCOMING
                }
            }
            _tinkerspaceIsSimulated.value = prefs.getBoolean(KEY_TINKERSPACE_IS_SIMULATED, false)
            _tinkerspaceBelongingsCheckedTime.value = prefs.getLong(KEY_TINKERSPACE_BELONGINGS_TIME, 0L)
        } catch (e: Exception) {
            _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.UPCOMING
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

        // Load Travellers from local cache (empty initially if no real travellers fetched yet)
        try {
            val travellersJson = prefs.getString(KEY_TRAVELLERS, null)
            if (travellersJson != null) {
                val type = object : TypeToken<List<TravellerRecord>>() {}.type
                val loaded: List<TravellerRecord>? = gson.fromJson(travellersJson, type)
                _travellers.value = loaded ?: emptyList()
            } else {
                _travellers.value = emptyList()
                saveTravellers(_travellers.value)
            }
        } catch (e: Exception) {
            _travellers.value = emptyList()
        }

        // Load Demo Statuses
        try {
            val demoJson = prefs.getString(KEY_DEMO_STATUSES, null)
            if (demoJson != null) {
                val type = object : TypeToken<List<TravellerDemoStatus>>() {}.type
                val loaded: List<TravellerDemoStatus>? = gson.fromJson(demoJson, type)
                if (!loaded.isNullOrEmpty()) {
                    _activeDemoStatuses.value = loaded
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }

        // If local traveller starts the demo on this device, initialize their session with their real user info
        if (_activeDemoStatuses.value.isEmpty() && _tinkerspaceWorkflowState.value != TinkerspaceWorkflowState.UPCOMING) {
            val (travellerId, travellerName) = getCurrentTravellerInfo()
            val initialDemoStatus = when (_tinkerspaceWorkflowState.value) {
                TinkerspaceWorkflowState.UPCOMING -> DemoStatus.NOT_STARTED
                TinkerspaceWorkflowState.IN_PROGRESS -> DemoStatus.ACTIVE
                TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED -> DemoStatus.DEPARTED
                TinkerspaceWorkflowState.INDOOR_STOP_REACHED -> DemoStatus.RETURNED
                TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK,
                TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION -> DemoStatus.AWAITING_BELONGINGS_CHECK
                TinkerspaceWorkflowState.COMPLETED -> DemoStatus.COMPLETED
            }
            val initial = TravellerDemoStatus(
                travellerId = travellerId,
                travellerName = travellerName,
                tripId = "trip_tinkerspace_demo",
                tripName = "Outside Tinkerspace",
                status = initialDemoStatus,
                isLocationAvailable = true,
                lastEvent = "Demo in state ${initialDemoStatus.displayName}",
                lastEventTimestamp = System.currentTimeMillis(),
                isBelongingsChecked = _tinkerspaceBelongingsCheckedTime.value > 0L,
                belongingsCheckedTime = if (_tinkerspaceBelongingsCheckedTime.value > 0L) _tinkerspaceBelongingsCheckedTime.value else null,
                isCompleted = initialDemoStatus == DemoStatus.COMPLETED
            )
            _activeDemoStatuses.value = listOf(initial)
            saveDemoStatuses(_activeDemoStatuses.value)
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
    fun getCurrentTrip(): Trip {
        return _trips.value.find { it.id == _currentTripId.value }
            ?: _trips.value.find { it.id == "trip_singapore_2026" }
            ?: _trips.value.firstOrNull()
            ?: SampleData.sampleTrips.first()
    }

    fun selectTrip(tripId: String) {
        _currentTripId.value = tripId
    }

    fun isTripSelected(tripId: String): Boolean {
        return _selectedTripIds.value.contains(tripId)
    }

    fun addTripToMyTrips(tripId: String) {
        val updated = _selectedTripIds.value + tripId
        _selectedTripIds.value = updated
        prefs.edit().putStringSet(KEY_SELECTED_TRIP_IDS, updated).apply()
    }

    fun removeTripFromMyTrips(tripId: String) {
        val updated = _selectedTripIds.value - tripId
        _selectedTripIds.value = updated
        prefs.edit().putStringSet(KEY_SELECTED_TRIP_IDS, updated).apply()
    }

    fun addTrip(trip: Trip) {
        val updated = listOf(trip) + _trips.value.filterNot { it.id == trip.id }
        _trips.value = updated
        _currentTripId.value = trip.id
        saveTrips(updated)
        addTripToMyTrips(trip.id)
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

    /**
     * Guide-scoped helpers.
     *
     * Ownership model (local cache, mirrors Supabase trips.created_by + trip_members):
     * - If [guideId] is null, return legacy guide-managed demo trips (guideId == null).
     * - Otherwise return trips owned by that guide OR legacy demo trips without an owner,
     *   so demo guides keep seeing the Singapore demo roster. Trips owned by a
     *   different guide are never returned.
     */
    fun getGuideTrips(guideId: String?): List<Trip> {
        return _trips.value.filter { trip ->
            if (!trip.isGuideManaged) return@filter false
            if (trip.guideId == null) {
                // Legacy demo data: visible when no ownership is set, or to demo guides.
                guideId == null || guideId.startsWith("demo_user") || trip.id == "trip_singapore_2026"
            } else {
                trip.guideId == guideId
            }
        }
    }

    fun getTripById(tripId: String): Trip? {
        return _trips.value.find { it.id == tripId }
    }

    fun getTravellersForTripIds(tripIds: Set<String>): List<TravellerRecord> {
        if (tripIds.isEmpty()) return emptyList()
        return _travellers.value.filter { it.assignedTripId in tripIds }
    }

    data class TripDetailsUpdate(
        val title: String,
        val destination: String,
        val country: String,
        val dates: String,
        val durationDays: Int,
        val summary: String,
        val meetingPoint: String,
        val maxGroupSize: Int,
        val travelerCount: Int,
        val status: TripStatus
    )

    /** Returns false when the trip no longer exists. */
    fun updateTripDetails(tripId: String, update: TripDetailsUpdate): Boolean {
        var found = false
        val updated = _trips.value.map { trip ->
            if (trip.id == tripId) {
                found = true
                trip.copy(
                    title = update.title.trim(),
                    destination = update.destination.trim(),
                    country = update.country.trim().ifBlank { update.destination.trim() },
                    dates = update.dates.trim(),
                    durationDays = update.durationDays,
                    summary = update.summary.trim(),
                    meetingPoint = update.meetingPoint.trim().ifBlank { trip.meetingPoint },
                    maxGroupSize = update.maxGroupSize,
                    travelerCount = update.travelerCount,
                    status = update.status
                )
            } else trip
        }
        if (!found) return false
        _trips.value = updated
        saveTrips(updated)
        return true
    }

    /** Returns false when the trip no longer exists. */
    fun deleteTrip(tripId: String): Boolean {
        val existing = _trips.value.any { it.id == tripId }
        if (!existing) return false
        // Protect the canonical demo itinerary from accidental deletion.
        if (tripId == "trip_singapore_2026" || tripId == "trip_tinkerspace_demo") return false
        val updated = _trips.value.filterNot { it.id == tripId }
        _trips.value = updated
        saveTrips(updated)
        removeTripFromMyTrips(tripId)
        if (_currentTripId.value == tripId) {
            _currentTripId.value = "trip_singapore_2026"
        }
        return true
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
        _travellers.value = emptyList()
        saveTravellers(_travellers.value)
        _chatMessages.value = createInitialChat()
        saveChat(_chatMessages.value)
        _currentTripId.value = "trip_singapore_2026"
        _selectedTripIds.value = emptySet()
        prefs.edit().remove(KEY_SELECTED_TRIP_IDS).apply()
        _selectedRole.value = null
        prefs.edit().remove(KEY_ROLE).apply()
        resetTinkerspaceDemo()
    }

    // Tinkerspace Demo Workflow Operations
    fun startTinkerspaceTrip() {
        _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.IN_PROGRESS
        prefs.edit().putString(KEY_TINKERSPACE_WORKFLOW_STATE, TinkerspaceWorkflowState.IN_PROGRESS.name).apply()
        updateTripStatus("trip_tinkerspace_demo", TripStatus.IN_PROGRESS)
        recordDemoTransition(
            status = DemoStatus.ACTIVE,
            eventType = "DEMO_STARTED",
            description = "Traveller started Outside Tinkerspace demo"
        )
    }

    fun reachTinkerspaceOutdoorStop(isSimulated: Boolean = false) {
        if (_tinkerspaceWorkflowState.value == TinkerspaceWorkflowState.UPCOMING ||
            _tinkerspaceWorkflowState.value == TinkerspaceWorkflowState.IN_PROGRESS) {
            _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED
            _tinkerspaceIsSimulated.value = isSimulated
            prefs.edit()
                .putString(KEY_TINKERSPACE_WORKFLOW_STATE, TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED.name)
                .putBoolean(KEY_TINKERSPACE_IS_SIMULATED, isSimulated)
                .apply()
            markDestinationCompleted("trip_tinkerspace_demo", "dest_tinker_outside")
            recordDemoTransition(
                status = DemoStatus.DEPARTED,
                eventType = "DEPARTED",
                description = "Traveller departed Tinkerspace (Outdoor stop reached)",
                departureTime = System.currentTimeMillis()
            )
        }
    }

    fun reachTinkerspaceIndoorStop(isSimulated: Boolean = false) {
        if (_tinkerspaceWorkflowState.value == TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED) {
            _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.INDOOR_STOP_REACHED
            _tinkerspaceIsSimulated.value = isSimulated
            prefs.edit()
                .putString(KEY_TINKERSPACE_WORKFLOW_STATE, TinkerspaceWorkflowState.INDOOR_STOP_REACHED.name)
                .putBoolean(KEY_TINKERSPACE_IS_SIMULATED, isSimulated)
                .apply()
            markDestinationCompleted("trip_tinkerspace_demo", "dest_tinker_inside")
            recordDemoTransition(
                status = DemoStatus.RETURNED,
                eventType = "RETURNED",
                description = "Traveller returned to Tinkerspace (Indoor stop reached)",
                returnTime = System.currentTimeMillis()
            )
        }
    }

    fun proceedToBelongingsCheck() {
        _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK
        prefs.edit().putString(KEY_TINKERSPACE_WORKFLOW_STATE, TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK.name).apply()
        recordDemoTransition(
            status = DemoStatus.AWAITING_BELONGINGS_CHECK,
            eventType = "AWAITING_BELONGINGS_CHECK",
            description = "Traveller arrived back and entered belongings check"
        )
    }

    fun confirmTinkerspaceBelongingsChecked(timestamp: Long = System.currentTimeMillis()) {
        _tinkerspaceBelongingsCheckedTime.value = timestamp
        _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION
        prefs.edit()
            .putString(KEY_TINKERSPACE_WORKFLOW_STATE, TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION.name)
            .putLong(KEY_TINKERSPACE_BELONGINGS_TIME, timestamp)
            .apply()
        recordDemoTransition(
            status = DemoStatus.AWAITING_BELONGINGS_CHECK,
            eventType = "BELONGINGS_CHECKED",
            description = "Traveller verified all personal belongings",
            belongingsChecked = true,
            belongingsCheckedTime = timestamp
        )
    }

    fun endTinkerspaceTrip() {
        _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.COMPLETED
        prefs.edit().putString(KEY_TINKERSPACE_WORKFLOW_STATE, TinkerspaceWorkflowState.COMPLETED.name).apply()
        updateTripStatus("trip_tinkerspace_demo", TripStatus.COMPLETED)
        recordDemoTransition(
            status = DemoStatus.COMPLETED,
            eventType = "TRIP_COMPLETED",
            description = "Traveller explicitly completed and ended the trip",
            isCompleted = true,
            completedTime = System.currentTimeMillis()
        )
    }

    fun resetTinkerspaceDemo() {
        _tinkerspaceWorkflowState.value = TinkerspaceWorkflowState.UPCOMING
        _tinkerspaceIsSimulated.value = false
        _tinkerspaceBelongingsCheckedTime.value = 0L
        lastRecordedEventType = null
        prefs.edit()
            .putString(KEY_TINKERSPACE_WORKFLOW_STATE, TinkerspaceWorkflowState.UPCOMING.name)
            .putBoolean(KEY_TINKERSPACE_IS_SIMULATED, false)
            .putLong(KEY_TINKERSPACE_BELONGINGS_TIME, 0L)
            .apply()

        val updatedTrips = _trips.value.map { trip ->
            if (trip.id == "trip_tinkerspace_demo") {
                trip.copy(
                    status = TripStatus.UPCOMING,
                    days = trip.days.map { day ->
                        day.copy(destinations = day.destinations.map { it.copy(isCompleted = false) })
                    }
                )
            } else trip
        }
        _trips.value = updatedTrips
        saveTrips(updatedTrips)

        recordDemoTransition(
            status = DemoStatus.NOT_STARTED,
            eventType = "DEMO_RESET",
            description = "Demo session reset to initial state",
            isCompleted = false,
            belongingsChecked = false
        )
    }

    fun recordLocationAvailability(available: Boolean, message: String = "") {
        val currentStatus = _activeDemoStatuses.value.firstOrNull()?.status ?: DemoStatus.NOT_STARTED
        if (currentStatus == DemoStatus.NOT_STARTED || currentStatus == DemoStatus.COMPLETED) {
            return
        }
        val eventType = if (available) "LOCATION_RESTORED" else "LOCATION_UNAVAILABLE"
        val desc = if (message.isNotBlank()) message else if (available) "GPS location tracking active" else "GPS location tracking unavailable"
        val newStatus = if (!available && currentStatus != DemoStatus.COMPLETED) DemoStatus.LOCATION_UNAVAILABLE else currentStatus
        recordDemoTransition(
            status = newStatus,
            eventType = eventType,
            description = desc,
            locationAvailable = available
        )
    }

    fun recordDemoTransition(
        status: DemoStatus,
        eventType: String,
        description: String,
        locationAvailable: Boolean = true,
        departureTime: Long? = null,
        returnTime: Long? = null,
        belongingsChecked: Boolean? = null,
        belongingsCheckedTime: Long? = null,
        isCompleted: Boolean? = null,
        completedTime: Long? = null
    ) {
        val (travellerId, travellerName) = getCurrentTravellerInfo()
        val currentList = _activeDemoStatuses.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.travellerId == travellerId }
        val existing = if (existingIndex >= 0) currentList[existingIndex] else null

        // Duplicate GPS/transition callback deduplication
        if (lastRecordedEventType == eventType && existing?.status == status) {
            return
        }
        lastRecordedEventType = eventType

        val now = System.currentTimeMillis()
        val newEvent = DemoEventRecord(
            eventType = eventType,
            description = description,
            timestamp = now
        )
        val updatedHistory = if (existing != null) {
            listOf(newEvent) + existing.eventHistory.take(29)
        } else {
            listOf(newEvent)
        }

        val updatedStatus = TravellerDemoStatus(
            travellerId = travellerId,
            travellerName = travellerName,
            tripId = "trip_tinkerspace_demo",
            tripName = "Outside Tinkerspace",
            status = status,
            isLocationAvailable = locationAvailable,
            lastEvent = description,
            lastEventTimestamp = now,
            departureTime = departureTime ?: existing?.departureTime,
            returnTime = returnTime ?: existing?.returnTime,
            isBelongingsChecked = belongingsChecked ?: existing?.isBelongingsChecked ?: false,
            belongingsCheckedTime = belongingsCheckedTime ?: existing?.belongingsCheckedTime,
            isCompleted = isCompleted ?: existing?.isCompleted ?: false,
            completedTime = completedTime ?: existing?.completedTime,
            eventHistory = updatedHistory
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = updatedStatus
        } else {
            currentList.add(0, updatedStatus)
        }
        _activeDemoStatuses.value = currentList
        saveDemoStatuses(currentList)

        // Asynchronously sync to Supabase if signed in and backend configured
        repositoryScope.launch {
            syncDemoSessionToBackend(updatedStatus, newEvent)
        }
    }

    private fun getCurrentTravellerInfo(): Pair<String, String> {
        return try {
            val authRepo = SupabaseAuthRepository.getInstance(appContext)
            val user = authRepo.currentUser.value
            if (user != null) {
                val name = user.fullName.trim().ifBlank {
                    user.email.substringBefore('@').ifBlank { "Registered Traveller" }
                }
                user.id to name
            } else {
                val cached = prefs.getString(KEY_USER_ID, null)
                val id = cached ?: "traveller"
                id to "Registered Traveller"
            }
        } catch (_: Exception) {
            "traveller" to "Registered Traveller"
        }
    }

    private suspend fun syncDemoSessionToBackend(session: TravellerDemoStatus, event: DemoEventRecord) {
        try {
            val authRepo = SupabaseAuthRepository.getInstance(appContext)
            val client = authRepo.getActiveNetworkClient() ?: return
            val user = authRepo.currentUser.value ?: return

            val actualTravellerId = user.id
            val tripUuid = "00000000-0000-0000-0000-000000000002"

            val sessionDto = DemoSessionDto(
                tripId = tripUuid,
                tripSlug = "trip_tinkerspace_demo",
                travellerId = actualTravellerId,
                travellerName = session.travellerName,
                status = session.status.name,
                locationAvailable = session.isLocationAvailable,
                lastEvent = session.lastEvent,
                lastEventTime = session.lastEventTimestamp.toIso8601(),
                departureTime = session.departureTime?.toIso8601(),
                returnTime = session.returnTime?.toIso8601(),
                belongingsChecked = session.isBelongingsChecked,
                belongingsCheckedTime = session.belongingsCheckedTime?.toIso8601(),
                isCompleted = session.isCompleted,
                completedTime = session.completedTime?.toIso8601(),
                updatedAt = System.currentTimeMillis().toIso8601()
            )

            client.postgrestApi.upsertDemoSession(sessionDto)

            val eventDto = DemoEventDto(
                tripId = tripUuid,
                travellerId = actualTravellerId,
                eventType = event.eventType,
                description = event.description,
                eventTimestamp = event.timestamp.toIso8601()
            )
            client.postgrestApi.insertDemoEvent(eventDto)
        } catch (_: Exception) {
            // Gracefully ignore network errors; local state remains consistent
        }
    }

    suspend fun fetchDemoSessions(): Result<List<TravellerDemoStatus>> = withContext(Dispatchers.IO) {
        try {
            val authRepo = SupabaseAuthRepository.getInstance(appContext)
            val client = authRepo.getActiveNetworkClient()
            if (client == null) {
                return@withContext Result.success(_activeDemoStatuses.value)
            }

            val response = client.postgrestApi.getDemoSessions(tripSlug = "trip_tinkerspace_demo")
            if (response.isSuccessful && response.body() != null) {
                val dtos = response.body()!!
                val mapped = dtos.map { dto ->
                    val statusEnum = try {
                        DemoStatus.valueOf(dto.status)
                    } catch (e: Exception) {
                        DemoStatus.ACTIVE
                    }
                    val existing = _activeDemoStatuses.value.find { it.travellerId == dto.travellerId }
                    TravellerDemoStatus(
                        travellerId = dto.travellerId,
                        travellerName = dto.travellerName,
                        tripId = "trip_tinkerspace_demo",
                        tripName = "Outside Tinkerspace",
                        status = statusEnum,
                        isLocationAvailable = dto.locationAvailable,
                        lastEvent = dto.lastEvent ?: "Status updated",
                        lastEventTimestamp = dto.lastEventTime.parseIso8601ToEpoch() ?: System.currentTimeMillis(),
                        departureTime = dto.departureTime.parseIso8601ToEpoch(),
                        returnTime = dto.returnTime.parseIso8601ToEpoch(),
                        isBelongingsChecked = dto.belongingsChecked,
                        belongingsCheckedTime = dto.belongingsCheckedTime.parseIso8601ToEpoch(),
                        isCompleted = dto.isCompleted,
                        completedTime = dto.completedTime.parseIso8601ToEpoch(),
                        eventHistory = existing?.eventHistory ?: emptyList()
                    )
                }

                _activeDemoStatuses.value = mapped
                saveDemoStatuses(mapped)
                return@withContext Result.success(_activeDemoStatuses.value)
            } else {
                return@withContext Result.failure(Exception("Failed to load demo sessions: ${response.code()}"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun fetchDemoEvents(travellerId: String): List<DemoEventRecord> = withContext(Dispatchers.IO) {
        try {
            val authRepo = SupabaseAuthRepository.getInstance(appContext)
            val client = authRepo.getActiveNetworkClient()
            if (client != null) {
                val response = client.postgrestApi.getDemoEvents(travellerIdFilter = "eq.$travellerId")
                if (response.isSuccessful && response.body() != null) {
                    val remoteEvents = response.body()!!.map { dto ->
                        DemoEventRecord(
                            id = dto.id ?: UUID.randomUUID().toString(),
                            eventType = dto.eventType,
                            description = dto.description,
                            timestamp = dto.eventTimestamp.parseIso8601ToEpoch() ?: System.currentTimeMillis()
                        )
                    }
                    if (remoteEvents.isNotEmpty()) {
                        val currentList = _activeDemoStatuses.value.toMutableList()
                        val idx = currentList.indexOfFirst { it.travellerId == travellerId }
                        if (idx >= 0) {
                            currentList[idx] = currentList[idx].copy(eventHistory = remoteEvents)
                            _activeDemoStatuses.value = currentList
                            saveDemoStatuses(currentList)
                        }
                        return@withContext remoteEvents
                    }
                }
            }
        } catch (_: Exception) {}

        return@withContext _activeDemoStatuses.value.find { it.travellerId == travellerId }?.eventHistory ?: emptyList()
    }

    suspend fun fetchTravellers(guideId: String? = null): Result<List<TravellerRecord>> = withContext(Dispatchers.IO) {
        try {
            val authRepo = SupabaseAuthRepository.getInstance(appContext)
            val client = authRepo.getActiveNetworkClient()
            if (client == null) {
                return@withContext Result.success(_travellers.value)
            }

            // 1. Fetch traveller profiles (PostgREST RLS enforces that guides only see authorized travellers)
            val profilesResponse = client.postgrestApi.getTravellerProfiles()
            if (!profilesResponse.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Failed to load travellers: HTTP ${profilesResponse.code()} ${profilesResponse.message()}")
                )
            }

            val profileDtos = profilesResponse.body() ?: emptyList()
            // Filter strictly to accounts classified as TRAVELLER and remove any duplicates
            val travellerProfiles = profileDtos
                .filter { it.role.equals("traveller", ignoreCase = true) }
                .distinctBy { it.id }

            // 2. Fetch trip memberships to correlate with assigned trips
            val tripMembersMap = mutableMapOf<String, String>()
            try {
                val membersResp = client.postgrestApi.getTripMembers()
                if (membersResp.isSuccessful && membersResp.body() != null) {
                    membersResp.body()!!.forEach { tm ->
                        tripMembersMap[tm.userId] = tm.tripId
                    }
                }
            } catch (_: Exception) {
                // Table may be empty
            }

            // 3. Fetch demo sessions to get verified real-time demo status
            val demoSessionsMap = mutableMapOf<String, DemoSessionDto>()
            try {
                val sessionsResp = client.postgrestApi.getDemoSessions()
                if (sessionsResp.isSuccessful && sessionsResp.body() != null) {
                    sessionsResp.body()!!.forEach { ds ->
                        demoSessionsMap[ds.travellerId] = ds
                    }
                }
            } catch (_: Exception) {
                // Non-critical if offline
            }

            // 4. Map each registered traveller profile into a TravellerRecord
            val mappedTravellers = travellerProfiles.map { profile ->
                val assignedTripUuid = tripMembersMap[profile.id]
                val hasTrip = assignedTripUuid != null
                val assignedTrip = if (assignedTripUuid != null) {
                    _trips.value.find { it.id == assignedTripUuid || it.id == "trip_singapore_2026" }
                } else null

                val demo = demoSessionsMap[profile.id]
                val demoStatus = if (demo != null) {
                    try {
                        DemoStatus.valueOf(demo.status)
                    } catch (_: Exception) {
                        DemoStatus.ACTIVE
                    }
                } else {
                    DemoStatus.NOT_STARTED
                }

                // Strict verified monitoring rule:
                // Only mark location monitoring active if demo is ACTIVE or DEPARTED and locationAvailable is verified true.
                val isMonitoringActive = (demoStatus == DemoStatus.ACTIVE || demoStatus == DemoStatus.DEPARTED) &&
                        (demo?.locationAvailable == true)

                val overallStatus = when {
                    demoStatus == DemoStatus.COMPLETED -> "Completed"
                    demoStatus == DemoStatus.AWAITING_BELONGINGS_CHECK -> "Awaiting Belongings Check"
                    demoStatus == DemoStatus.RETURNED -> "Returned"
                    demoStatus == DemoStatus.DEPARTED -> if (demo?.locationAvailable == false) "Location Unavailable" else "Outside Tinkerspace"
                    demoStatus == DemoStatus.ACTIVE -> if (demo?.locationAvailable == false) "Location Unavailable" else "Active"
                    hasTrip -> "Trip Selected"
                    else -> "Registered"
                }

                val displayName = profile.fullName?.trim()?.ifBlank { null }
                    ?: profile.email?.substringBefore('@')?.ifBlank { null }
                    ?: "Registered Traveller"

                val assignedTripId = assignedTrip?.id ?: assignedTripUuid ?: if (hasTrip) "trip_singapore_2026" else ""
                val assignedTripName = assignedTrip?.title ?: if (hasTrip) "Singapore Explorer" else "No Trip Selected"

                TravellerRecord(
                    id = profile.id,
                    name = displayName,
                    assignedTripId = assignedTripId,
                    assignedTripName = assignedTripName,
                    preparationStatus = overallStatus,
                    packedItemsCount = 0,
                    totalItemsCount = 0,
                    visitedActivitiesCount = 0,
                    totalActivitiesCount = 0,
                    contactPhone = profile.phone ?: "",
                    email = profile.email,
                    overallStatus = overallStatus,
                    demoStatus = demoStatus,
                    isLocationMonitoringActive = isMonitoringActive,
                    lastRecordedEvent = demo?.lastEvent,
                    lastEventTimestamp = demo?.lastEventTime.parseIso8601ToEpoch(),
                    departureTimestamp = demo?.departureTime.parseIso8601ToEpoch(),
                    returnTimestamp = demo?.returnTime.parseIso8601ToEpoch(),
                    isBelongingsChecked = demo?.belongingsChecked ?: false,
                    isTripCompleted = demo?.isCompleted ?: false,
                    completedTimestamp = demo?.completedTime.parseIso8601ToEpoch()
                )
            }

            _travellers.value = mappedTravellers
            saveTravellers(mappedTravellers)
            Result.success(mappedTravellers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    private fun markDestinationCompleted(tripId: String, destId: String) {
        val updated = _trips.value.map { trip ->
            if (trip.id == tripId) {
                trip.copy(
                    days = trip.days.map { day ->
                        day.copy(
                            destinations = day.destinations.map { dest ->
                                if (dest.id == destId) dest.copy(isCompleted = true) else dest
                            }
                        )
                    }
                )
            } else trip
        }
        _trips.value = updated
        saveTrips(updated)
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

    private fun saveDemoStatuses(statuses: List<TravellerDemoStatus>) {
        prefs.edit().putString(KEY_DEMO_STATUSES, gson.toJson(statuses)).apply()
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun Long.toIso8601(): String = synchronized(isoDateFormat) {
        isoDateFormat.format(Date(this))
    }

    private fun String?.parseIso8601ToEpoch(): Long? {
        if (this.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(this).toEpochMilli()
        } catch (_: Exception) {
            try {
                synchronized(isoDateFormat) { isoDateFormat.parse(this)?.time }
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private const val KEY_ROLE = "key_role"
        private const val KEY_TRIPS = "key_trips"
        private const val KEY_CHECKLIST = "key_checklist"
        private const val KEY_CHAT = "key_chat"
        private const val KEY_TRAVELLERS = "key_travellers"
        private const val KEY_SELECTED_TRIP_IDS = "key_selected_trip_ids"
        private const val KEY_TINKERSPACE_WORKFLOW_STATE = "key_tinkerspace_workflow_state"
        private const val KEY_TINKERSPACE_IS_SIMULATED = "key_tinkerspace_is_simulated"
        private const val KEY_TINKERSPACE_BELONGINGS_TIME = "key_tinkerspace_belongings_time"
        private const val KEY_DEMO_STATUSES = "key_demo_statuses"
        private const val KEY_USER_ID = "key_user_id"

        @Volatile
        private var instance: RoamlyRepository? = null

        fun getInstance(context: Context): RoamlyRepository {
            return instance ?: synchronized(this) {
                instance ?: RoamlyRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
