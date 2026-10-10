package com.roamly.app

import com.roamly.app.ai.RoamlyAiCompanion
import com.roamly.app.data.SampleData
import com.roamly.app.model.ChatSender
import com.roamly.app.model.ChecklistCategory
import com.roamly.app.model.TripStatus
import com.roamly.app.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoamlyDataModelTest {

    @Test
    fun testSampleTripsIntegrity() {
        val trips = SampleData.sampleTrips
        assertEquals("Singapore must be the single available trip", 1, trips.size)
        val singaporeTrip = trips.first()
        assertEquals("trip_singapore_2026", singaporeTrip.id)
        assertEquals("Singapore", singaporeTrip.destination)
        assertEquals("Singapore Explorer", singaporeTrip.title)
        assertEquals(5, singaporeTrip.durationDays)
        assertEquals(5, singaporeTrip.days.size)

        // Verify Day 1 to Day 5 destinations
        singaporeTrip.days.forEachIndexed { index, day ->
            assertEquals(index + 1, day.dayNumber)
            assertTrue("Day ${day.dayNumber} must have destinations", day.destinations.isNotEmpty())
            day.destinations.forEach { dest ->
                assertNotNull(dest.name)
                assertNotNull(dest.shortDescription)
                assertNotNull(dest.fullDescription)
            }
        }
    }

    @Test
    fun testSampleChecklistIntegrity() {
        val checklist = SampleData.sampleChecklist
        assertTrue("Checklist must not be empty", checklist.isNotEmpty())

        // Ensure key categories are represented
        val categories = checklist.map { it.category }.toSet()
        assertTrue(categories.contains(ChecklistCategory.DOCUMENTS))
        assertTrue(categories.contains(ChecklistCategory.CLOTHING))
        assertTrue(categories.contains(ChecklistCategory.ESSENTIALS))
        assertTrue(categories.contains(ChecklistCategory.ELECTRONICS))
        assertTrue(categories.contains(ChecklistCategory.HEALTH))

        // Non-destructive: verify initial state
        val totalCount = checklist.size
        val packedCount = checklist.count { it.isPacked }
        assertTrue("Total count should be greater than 0", totalCount > 0)
        assertTrue("Initial packed count should be reasonable", packedCount >= 0)
    }

    @Test
    fun testTravellersIntegrity() {
        val travellers = SampleData.sampleTravellers
        assertTrue("Sample travellers must not be empty", travellers.isNotEmpty())

        travellers.forEach { traveller ->
            assertNotNull(traveller.id)
            assertNotNull(traveller.name)
            assertNotNull(traveller.assignedTripId)
            assertTrue(
                listOf("Ready", "Preparation Incomplete", "Documents Pending")
                    .contains(traveller.preparationStatus)
            )
        }
    }

    @Test
    fun testRolesAndStatus() {
        assertEquals("TRAVELLER", UserRole.TRAVELLER.name)
        assertEquals("GUIDE", UserRole.GUIDE.name)

        assertEquals("Planning", TripStatus.PLANNING.displayName)
        assertEquals("Upcoming", TripStatus.UPCOMING.displayName)
        assertEquals("In Progress", TripStatus.IN_PROGRESS.displayName)
        assertEquals("Completed", TripStatus.COMPLETED.displayName)
    }

    @Test
    fun testOfflineAiCompanion() {
        val singaporeTrip = SampleData.sampleTrips.first()

        val packResponse = RoamlyAiCompanion.generateResponse("What should I pack?", singaporeTrip)
        assertEquals(ChatSender.ROAMLY_AI, packResponse.sender)
        assertTrue(packResponse.text.contains("Packing") || packResponse.text.contains("pack"))

        val marinaResponse = RoamlyAiCompanion.generateResponse("Marina Bay", singaporeTrip)
        assertTrue(marinaResponse.text.contains("Marina Bay") || marinaResponse.text.contains("SkyPark"))

        val unknownResponse = RoamlyAiCompanion.generateResponse("Random unrelated query xyz", singaporeTrip)
        assertNotNull(unknownResponse.text)
    }
}
