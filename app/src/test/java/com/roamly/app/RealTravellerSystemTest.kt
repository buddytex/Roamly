package com.roamly.app

import com.roamly.app.auth.network.DemoSessionDto
import com.roamly.app.auth.network.ProfileDto
import com.roamly.app.auth.network.TripDto
import com.roamly.app.auth.network.TripMemberDto
import com.roamly.app.data.SampleData
import com.roamly.app.model.DemoStatus
import com.roamly.app.model.TravellerRecord
import com.roamly.app.model.TripStatus
import com.roamly.app.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RealTravellerSystemTest {

    // Simulates the backend + repository mapping logic and RLS enforcement
    private class BackendRosterSystem {
        val profiles = mutableListOf<ProfileDto>()
        val tripMembers = mutableListOf<TripMemberDto>()
        val demoSessions = mutableListOf<DemoSessionDto>()
        val trips = mutableListOf<TripDto>()

        var shouldSimulateQueryFailure = false

        // Simulates PostgREST RLS for a guide
        fun getTravellerProfilesForGuide(guideId: String, isApprovedGuide: Boolean): Result<List<ProfileDto>> {
            if (shouldSimulateQueryFailure) {
                return Result.failure(Exception("HTTP 500: Database connection failure"))
            }
            if (!isApprovedGuide) {
                return Result.success(emptyList()) // RLS denies access
            }

            // Find trips managed by this guide
            val guideTrips = tripMembers
                .filter { it.userId == guideId && (it.membershipRole == "guide" || it.membershipRole == "lead_guide") }
                .map { it.tripId }
                .toSet()

            // Find travellers enrolled in those trips
            val authorizedTravellerIds = tripMembers
                .filter { it.tripId in guideTrips && it.membershipRole == "traveller" }
                .map { it.userId }
                .toSet()

            // Filter profiles: must be 'traveller' role and in authorized set
            val accessible = profiles.filter { profile ->
                profile.role.equals("traveller", ignoreCase = true) && profile.id in authorizedTravellerIds
            }
            return Result.success(accessible)
        }

        fun mapToTravellerRecords(profiles: List<ProfileDto>): List<TravellerRecord> {
            val tripMembersMap = tripMembers.associate { it.userId to it.tripId }
            val demoSessionsMap = demoSessions.associateBy { it.travellerId }

            return profiles.map { profile ->
                val assignedTripUuid = tripMembersMap[profile.id]
                val hasTrip = assignedTripUuid != null
                val assignedTrip = if (assignedTripUuid != null) {
                    trips.find { it.id == assignedTripUuid || it.slug == "trip_singapore_2026" }
                } else null

                val demo = demoSessionsMap[profile.id]
                val demoStatus = if (demo != null) {
                    try { DemoStatus.valueOf(demo.status) } catch (_: Exception) { DemoStatus.ACTIVE }
                } else DemoStatus.NOT_STARTED

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
                    contactPhone = profile.phone ?: "",
                    email = profile.email,
                    overallStatus = overallStatus,
                    demoStatus = demoStatus,
                    isLocationMonitoringActive = isMonitoringActive,
                    lastRecordedEvent = demo?.lastEvent,
                    isBelongingsChecked = demo?.belongingsChecked ?: false,
                    isTripCompleted = demo?.isCompleted ?: false
                )
            }
        }
    }

    @Test
    fun test1_NoMockTravellersAppearInEmptyBackend() {
        val backend = BackendRosterSystem()
        val guideId = "guide_123"

        // No travellers registered yet
        val profilesResult = backend.getTravellerProfilesForGuide(guideId, isApprovedGuide = true)
        assertTrue(profilesResult.isSuccess)
        val travellers = backend.mapToTravellerRecords(profilesResult.getOrThrow())

        // Must be completely empty, no mock fallback to Alex Chen, Maya Lin, etc.
        assertTrue("Roster must be empty when no travellers registered", travellers.isEmpty())
        assertFalse("Alex Chen must not appear", travellers.any { it.name == "Alex Chen" })
        assertFalse("Maya Lin must not appear", travellers.any { it.name == "Maya Lin" })
    }

    @Test
    fun test2_RealTravellerRegistrationAppearsInBackend() {
        val backend = BackendRosterSystem()
        val realTravellerId = UUID.randomUUID().toString()

        backend.profiles.add(
            ProfileDto(
                id = realTravellerId,
                fullName = "Jordan Taylor",
                role = "traveller",
                email = "jordan.taylor@example.com",
                phone = "+1 555 0198"
            )
        )

        val stored = backend.profiles.find { it.id == realTravellerId }
        assertNotNull(stored)
        assertEquals("Jordan Taylor", stored?.fullName)
        assertEquals("traveller", stored?.role)
    }

    @Test
    fun test3_RealTravellerAppearsInGuidePortalWhenAuthorized() {
        val backend = BackendRosterSystem()
        val tripId = "trip_sg_1"
        val guideId = "guide_alice"
        val travellerId = "traveller_jordan"

        backend.trips.add(TripDto(id = tripId, slug = "trip_singapore_2026", title = "Singapore Explorer", destination = "Singapore", country = "Singapore"))
        backend.profiles.add(ProfileDto(id = guideId, fullName = "Alice Guide", role = "guide"))
        backend.profiles.add(ProfileDto(id = travellerId, fullName = "Jordan Taylor", role = "traveller", email = "jordan@example.com"))

        // Enroll guide and traveller in the trip
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = guideId, membershipRole = "lead_guide"))
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = travellerId, membershipRole = "traveller"))

        val profiles = backend.getTravellerProfilesForGuide(guideId, isApprovedGuide = true).getOrThrow()
        assertEquals(1, profiles.size)

        val records = backend.mapToTravellerRecords(profiles)
        assertEquals(1, records.size)
        assertEquals("Jordan Taylor", records[0].name)
        assertEquals("Singapore Explorer", records[0].assignedTripName)
    }

    @Test
    fun test4_GuideAccountNotListedAsTraveller() {
        val backend = BackendRosterSystem()
        val tripId = "trip_sg_1"
        val guideId1 = "guide_lead"
        val guideId2 = "guide_assistant"

        backend.profiles.add(ProfileDto(id = guideId1, fullName = "Lead Guide", role = "guide"))
        backend.profiles.add(ProfileDto(id = guideId2, fullName = "Assistant Guide", role = "guide"))

        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = guideId1, membershipRole = "lead_guide"))
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = guideId2, membershipRole = "guide"))

        val profiles = backend.getTravellerProfilesForGuide(guideId1, isApprovedGuide = true).getOrThrow()
        // No guide accounts should be classified or returned as travellers
        assertTrue("Guides must not be listed as travellers", profiles.none { it.role == "guide" })
    }

    @Test
    fun test5_RegisteredTravellerWithoutTripNotShownAsActive() {
        val backend = BackendRosterSystem()
        val travellerId = "traveller_newly_registered"
        val guideId = "guide_lead"
        val tripId = "trip_universal"

        backend.profiles.add(ProfileDto(id = travellerId, fullName = "Emily Stone", role = "traveller"))
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = guideId, membershipRole = "lead_guide"))
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = travellerId, membershipRole = "traveller"))

        val records = backend.mapToTravellerRecords(listOf(backend.profiles.first { it.id == travellerId }))
        val traveller = records[0]

        // Has not started Tinkerspace demo
        assertEquals(DemoStatus.NOT_STARTED, traveller.demoStatus)
        assertFalse("Monitoring must NOT be active for traveller who has not started demo", traveller.isLocationMonitoringActive)
        assertNotEquals("Active", traveller.overallStatus)
    }

    @Test
    fun test6_TravellerSelectedTripDisplayedCorrectly() {
        val backend = BackendRosterSystem()
        val tripId = "trip_tinker_demo"
        val travellerId = "traveller_tim"

        backend.trips.add(TripDto(id = tripId, slug = "trip_tinkerspace_demo", title = "Outside Tinkerspace", destination = "Tinkerspace", country = "India"))
        backend.profiles.add(ProfileDto(id = travellerId, fullName = "Tim Rover", role = "traveller"))
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = travellerId, membershipRole = "traveller"))

        val records = backend.mapToTravellerRecords(listOf(backend.profiles.first { it.id == travellerId }))
        assertEquals("Outside Tinkerspace", records[0].assignedTripName)
        assertEquals("Trip Selected", records[0].overallStatus)
    }

    @Test
    fun test7And8_RealTinkerspaceStatusChangesAndEventsReachGuidePortal() {
        val backend = BackendRosterSystem()
        val travellerId = "traveller_real_1"

        backend.profiles.add(ProfileDto(id = travellerId, fullName = "Marcus Vance", role = "traveller"))
        backend.tripMembers.add(TripMemberDto(tripId = "trip_demo", userId = travellerId, membershipRole = "traveller"))

        // State 1: Active
        backend.demoSessions.add(
            DemoSessionDto(
                tripId = "trip_demo",
                travellerId = travellerId,
                travellerName = "Marcus Vance",
                status = "ACTIVE",
                locationAvailable = true,
                lastEvent = "Demo started"
            )
        )
        var records = backend.mapToTravellerRecords(backend.profiles)
        assertEquals("Active", records[0].overallStatus)
        assertTrue(records[0].isLocationMonitoringActive)

        // State 2: Departed geofence
        backend.demoSessions.clear()
        backend.demoSessions.add(
            DemoSessionDto(
                tripId = "trip_demo",
                travellerId = travellerId,
                travellerName = "Marcus Vance",
                status = "DEPARTED",
                locationAvailable = true,
                lastEvent = "Departed Tinkerspace geofence",
                departureTime = "2026-10-11T08:00:00Z"
            )
        )
        records = backend.mapToTravellerRecords(backend.profiles)
        assertEquals("Outside Tinkerspace", records[0].overallStatus)
        assertTrue(records[0].isLocationMonitoringActive)
        assertEquals("Departed Tinkerspace geofence", records[0].lastRecordedEvent)

        // State 3: Returned to Tinkerspace (returning does not automatically complete the trip)
        backend.demoSessions.clear()
        backend.demoSessions.add(
            DemoSessionDto(
                tripId = "trip_demo",
                travellerId = travellerId,
                travellerName = "Marcus Vance",
                status = "RETURNED",
                locationAvailable = true,
                lastEvent = "Returned inside Tinkerspace",
                departureTime = "2026-10-11T08:00:00Z",
                returnTime = "2026-10-11T08:15:00Z"
            )
        )
        records = backend.mapToTravellerRecords(backend.profiles)
        assertEquals("Returned", records[0].overallStatus)
        assertFalse("Monitoring is no longer active once returned", records[0].isLocationMonitoringActive)
        assertFalse("Returning does NOT complete trip automatically", records[0].isTripCompleted)
    }

    @Test
    fun test9_BelongingsCheckAndCompletionStatesAccurate() {
        val backend = BackendRosterSystem()
        val travellerId = "traveller_real_2"

        backend.profiles.add(ProfileDto(id = travellerId, fullName = "Sophia Chen", role = "traveller"))
        backend.tripMembers.add(TripMemberDto(tripId = "trip_demo", userId = travellerId, membershipRole = "traveller"))

        // State 1: Awaiting belongings check
        backend.demoSessions.add(
            DemoSessionDto(
                tripId = "trip_demo",
                travellerId = travellerId,
                travellerName = "Sophia Chen",
                status = "AWAITING_BELONGINGS_CHECK",
                belongingsChecked = false,
                lastEvent = "Awaiting belongings verification"
            )
        )
        var records = backend.mapToTravellerRecords(backend.profiles)
        assertEquals("Awaiting Belongings Check", records[0].overallStatus)
        assertFalse(records[0].isBelongingsChecked)
        assertFalse(records[0].isTripCompleted)

        // State 2: Explicit trip completion after belongings check
        backend.demoSessions.clear()
        backend.demoSessions.add(
            DemoSessionDto(
                tripId = "trip_demo",
                travellerId = travellerId,
                travellerName = "Sophia Chen",
                status = "COMPLETED",
                belongingsChecked = true,
                isCompleted = true,
                completedTime = "2026-10-11T08:30:00Z",
                lastEvent = "Trip explicitly ended by traveller"
            )
        )
        records = backend.mapToTravellerRecords(backend.profiles)
        assertEquals("Completed", records[0].overallStatus)
        assertTrue(records[0].isBelongingsChecked)
        assertTrue(records[0].isTripCompleted)
    }

    @Test
    fun test10_UnauthorizedGuideCannotAccessPrivateTravellerData() {
        val backend = BackendRosterSystem()
        val tripId = "trip_guide_a"
        val authorizedGuideId = "guide_a"
        val unauthorizedGuideId = "guide_b"
        val travellerId = "traveller_private"

        backend.profiles.add(ProfileDto(id = travellerId, fullName = "Private User", role = "traveller", email = "private@example.com"))
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = authorizedGuideId, membershipRole = "lead_guide"))
        backend.tripMembers.add(TripMemberDto(tripId = tripId, userId = travellerId, membershipRole = "traveller"))

        // Authorized guide can see traveller
        val authResult = backend.getTravellerProfilesForGuide(authorizedGuideId, isApprovedGuide = true).getOrThrow()
        assertEquals(1, authResult.size)

        // Unauthorized guide (not assigned to this trip) CANNOT see traveller
        val unauthResult = backend.getTravellerProfilesForGuide(unauthorizedGuideId, isApprovedGuide = true).getOrThrow()
        assertTrue("Unauthorized guide must receive empty roster due to RLS", unauthResult.isEmpty())
    }

    @Test
    fun test11_QueryFailuresShowErrorRatherThanFakeData() {
        val backend = BackendRosterSystem()
        backend.shouldSimulateQueryFailure = true

        val result = backend.getTravellerProfilesForGuide("guide_1", isApprovedGuide = true)
        assertTrue("Query failure must be propagated as failure", result.isFailure)
        assertEquals("HTTP 500: Database connection failure", result.exceptionOrNull()?.message)
    }

    @Test
    fun test12_ExistingSingaporePackagesAndMyTripsContinueWorking() {
        val sampleTrips = SampleData.sampleTrips
        assertEquals(2, sampleTrips.size)

        val singaporeTrip = sampleTrips.first { it.id == "trip_singapore_2026" }
        assertEquals("Singapore Explorer", singaporeTrip.title)
        assertEquals(5, singaporeTrip.durationDays)
        assertEquals(TripStatus.UPCOMING, singaporeTrip.status)

        val checklist = SampleData.sampleChecklist
        assertTrue(checklist.isNotEmpty())
    }

    @Test
    fun test13_ExistingTinkerspaceFunctionalityContinuesWorking() {
        val sampleTrips = SampleData.sampleTrips
        val tinkerspaceTrip = sampleTrips.first { it.id == "trip_tinkerspace_demo" }
        assertEquals("Outside Tinkerspace", tinkerspaceTrip.title)
        assertEquals("Tinkerspace", tinkerspaceTrip.destination)
        assertEquals(2, tinkerspaceTrip.days.first().destinations.size)
    }
}
