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
        assertEquals("Both Singapore and Tinkerspace trips must be available", 2, trips.size)
        val singaporeTrip = trips.first { it.id == "trip_singapore_2026" }
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

        // Verify Tinkerspace demo trip
        val tinkerspaceTrip = trips.first { it.id == "trip_tinkerspace_demo" }
        assertEquals("Outside Tinkerspace", tinkerspaceTrip.title)
        assertEquals("Tinkerspace", tinkerspaceTrip.destination)
        assertEquals(1, tinkerspaceTrip.days.size)
        val stops = tinkerspaceTrip.days.first().destinations
        assertEquals(2, stops.size)
        assertEquals("dest_tinker_outside", stops[0].id)
        assertEquals("Outside Tinkerspace Building", stops[0].name)
        assertEquals("dest_tinker_inside", stops[1].id)
        assertEquals("Back Inside Tinkerspace Building", stops[1].name)
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

    @Test
    fun testTinkerspaceWorkflowStateMachine() {
        val states = com.roamly.app.model.TinkerspaceWorkflowState.values()
        assertEquals(7, states.size)
        assertEquals(com.roamly.app.model.TinkerspaceWorkflowState.UPCOMING, states[0])
        assertEquals(com.roamly.app.model.TinkerspaceWorkflowState.IN_PROGRESS, states[1])
        assertEquals(com.roamly.app.model.TinkerspaceWorkflowState.OUTDOOR_STOP_REACHED, states[2])
        assertEquals(com.roamly.app.model.TinkerspaceWorkflowState.INDOOR_STOP_REACHED, states[3])
        assertEquals(com.roamly.app.model.TinkerspaceWorkflowState.AWAITING_BELONGINGS_CHECK, states[4])
        assertEquals(com.roamly.app.model.TinkerspaceWorkflowState.AWAITING_FINAL_CONFIRMATION, states[5])
        assertEquals(com.roamly.app.model.TinkerspaceWorkflowState.COMPLETED, states[6])
    }
}
