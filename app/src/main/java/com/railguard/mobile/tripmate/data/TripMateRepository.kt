package com.railguard.mobile.tripmate.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railguard.mobile.tripmate.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class TripMateRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tripmate_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _activeRole = MutableStateFlow(UserRole.TOURIST)
    val activeRole: StateFlow<UserRole> = _activeRole.asStateFlow()

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _selectedTripId = MutableStateFlow<String?>(null)
    val selectedTripId: StateFlow<String?> = _selectedTripId.asStateFlow()

    private val _sosIncidents = MutableStateFlow<List<SOSIncident>>(emptyList())
    val sosIncidents: StateFlow<List<SOSIncident>> = _sosIncidents.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val roleStr = prefs.getString("user_role", UserRole.TOURIST.name) ?: UserRole.TOURIST.name
        _activeRole.value = try {
            UserRole.valueOf(roleStr)
        } catch (e: Exception) {
            UserRole.TOURIST
        }

        val tripsJson = prefs.getString("trips_data", null)
        if (!tripsJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<Trip>>() {}.type
                val loadedTrips: List<Trip> = gson.fromJson(tripsJson, type)
                if (loadedTrips.isNotEmpty()) {
                    _trips.value = loadedTrips
                    _selectedTripId.value = prefs.getString("selected_trip_id", loadedTrips[0].id)
                } else {
                    initDefaultData()
                }
            } catch (e: Exception) {
                initDefaultData()
            }
        } else {
            initDefaultData()
        }

        val sosJson = prefs.getString("sos_incidents", null)
        if (!sosJson.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<SOSIncident>>() {}.type
                _sosIncidents.value = gson.fromJson(sosJson, type)
            } catch (e: Exception) {
                _sosIncidents.value = emptyList()
            }
        }
    }

    private fun initDefaultData() {
        val defaultTrip = SampleData.createSampleDubaiTrip()
        _trips.value = listOf(defaultTrip)
        _selectedTripId.value = defaultTrip.id
        saveTrips()
    }

    private fun saveTrips() {
        prefs.edit()
            .putString("trips_data", gson.toJson(_trips.value))
            .putString("selected_trip_id", _selectedTripId.value)
            .apply()
    }

    private fun saveSos() {
        prefs.edit()
            .putString("sos_incidents", gson.toJson(_sosIncidents.value))
            .apply()
    }

    fun setRole(role: UserRole) {
        _activeRole.value = role
        prefs.edit().putString("user_role", role.name).apply()
    }

    fun selectTrip(tripId: String) {
        _selectedTripId.value = tripId
        prefs.edit().putString("selected_trip_id", tripId).apply()
    }

    fun getActiveTrip(): Trip? {
        val id = _selectedTripId.value ?: return _trips.value.firstOrNull()
        return _trips.value.find { it.id == id } ?: _trips.value.firstOrNull()
    }

    // Trip Management
    fun createTrip(newTrip: Trip) {
        val updated = _trips.value.toMutableList().apply { add(0, newTrip) }
        _trips.value = updated
        _selectedTripId.value = newTrip.id
        saveTrips()
    }

    fun updateTrip(updatedTrip: Trip) {
        val updated = _trips.value.map { if (it.id == updatedTrip.id) updatedTrip else it }
        _trips.value = updated
        saveTrips()
    }

    fun resetToDemoData() {
        initDefaultData()
        _sosIncidents.value = emptyList()
        saveSos()
    }

    // Packing Checklist
    fun togglePackingItem(itemId: String) {
        val trip = getActiveTrip() ?: return
        val updatedList = trip.packingList.map {
            if (it.id == itemId) it.copy(isPacked = !it.isPacked) else it
        }
        updateTrip(trip.copy(packingList = updatedList))
    }

    fun addCustomPackingItem(title: String, category: String = "Personal Essentials") {
        val trip = getActiveTrip() ?: return
        val newItem = PackingItem(
            title = title,
            category = category,
            isPacked = false,
            isCustom = true
        )
        val updatedList = trip.packingList + newItem
        updateTrip(trip.copy(packingList = updatedList))
    }

    fun removePackingItem(itemId: String) {
        val trip = getActiveTrip() ?: return
        val updatedList = trip.packingList.filterNot { it.id == itemId }
        updateTrip(trip.copy(packingList = updatedList))
    }

    // Belongings Verification (CRITICAL: Never reset or wipe on departure!)
    fun toggleBelongingItem(itemId: String) {
        val trip = getActiveTrip() ?: return
        val updatedList = trip.belongingsList.map {
            if (it.id == itemId) it.copy(isVerified = !it.isVerified) else it
        }
        updateTrip(trip.copy(belongingsList = updatedList))
    }

    fun addBelongingItem(name: String) {
        val trip = getActiveTrip() ?: return
        val newItem = BelongingItem(name = name, isVerified = true)
        val updatedList = trip.belongingsList + newItem
        updateTrip(trip.copy(belongingsList = updatedList))
    }

    fun reportBelongingMissing(itemId: String, location: String) {
        val trip = getActiveTrip() ?: return
        val updatedList = trip.belongingsList.map {
            if (it.id == itemId) it.copy(isReportedMissing = true, lastSeenLocation = location) else it
        }
        updateTrip(trip.copy(belongingsList = updatedList))

        // Also automatically create an assistance record for the guide!
        val item = trip.belongingsList.find { it.id == itemId }
        if (item != null) {
            submitSOS(
                travelerName = "Elena Rostova (You)",
                travelerPhone = "+1 415 555 0192",
                destinationName = location,
                message = "Missing Item Reported: '${item.name}' last remembered at $location. Please assist."
            )
        }
    }

    // Destination Progression & Transitions
    fun acknowledgeDepartureReminder(destinationId: String) {
        val trip = getActiveTrip() ?: return
        val updatedDestinations = trip.destinations.map { dest ->
            if (dest.id == destinationId) dest.copy(departureReminderAcknowledged = true) else dest
        }
        updateTrip(trip.copy(destinations = updatedDestinations))
    }

    fun completeCurrentDestinationAndProceed() {
        val trip = getActiveTrip() ?: return
        val currIdx = trip.currentDestinationIndex
        if (currIdx < trip.destinations.size) {
            val updatedDestinations = trip.destinations.mapIndexed { idx, dest ->
                when (idx) {
                    currIdx -> dest.copy(status = DestinationStatus.COMPLETED)
                    currIdx + 1 -> dest.copy(status = DestinationStatus.CURRENT)
                    else -> dest
                }
            }
            val nextIdx = (currIdx + 1).coerceAtMost(trip.destinations.size)
            val updatedStatus = if (nextIdx >= trip.destinations.size) TripStatus.COMPLETED else trip.status
            updateTrip(trip.copy(
                destinations = updatedDestinations,
                currentDestinationIndex = nextIdx,
                status = updatedStatus
            ))
        }
    }

    fun toggleActivityCompleted(destinationId: String, activityId: String) {
        val trip = getActiveTrip() ?: return
        val updatedDestinations = trip.destinations.map { dest ->
            if (dest.id == destinationId) {
                val updatedActs = dest.activities.map { act ->
                    if (act.id == activityId) act.copy(isCompleted = !act.isCompleted) else act
                }
                dest.copy(activities = updatedActs)
            } else dest
        }
        updateTrip(trip.copy(destinations = updatedDestinations))
    }

    // SOS & Assistance
    fun submitSOS(
        travelerName: String,
        travelerPhone: String,
        destinationName: String,
        message: String,
        lat: Double? = null,
        lng: Double? = null
    ) {
        val trip = getActiveTrip()
        val incident = SOSIncident(
            tripId = trip?.id ?: "general",
            travelerName = travelerName,
            travelerPhone = travelerPhone,
            destinationName = destinationName,
            message = message,
            latitude = lat,
            longitude = lng,
            status = SOSStatus.REPORTED_PENDING_ACK,
            isOfflineStored = true
        )
        _sosIncidents.value = listOf(incident) + _sosIncidents.value
        saveSos()
    }

    fun acknowledgeSOS(incidentId: String) {
        _sosIncidents.value = _sosIncidents.value.map {
            if (it.id == incidentId) it.copy(status = SOSStatus.ACKNOWLEDGED) else it
        }
        saveSos()
    }

    fun resolveSOS(incidentId: String) {
        _sosIncidents.value = _sosIncidents.value.map {
            if (it.id == incidentId) it.copy(status = SOSStatus.RESOLVED) else it
        }
        saveSos()
    }

    companion object {
        @Volatile
        private var instance: TripMateRepository? = null

        fun getInstance(context: Context): TripMateRepository {
            return instance ?: synchronized(this) {
                instance ?: TripMateRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
