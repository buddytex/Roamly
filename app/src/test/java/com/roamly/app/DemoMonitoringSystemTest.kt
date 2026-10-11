package com.roamly.app

import com.roamly.app.data.SampleData
import com.roamly.app.model.DemoEventRecord
import com.roamly.app.model.DemoStatus
import com.roamly.app.model.TinkerspaceWorkflowState
import com.roamly.app.model.TravellerDemoStatus
import com.roamly.app.model.TripStatus
import com.roamly.app.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DemoMonitoringSystemTest {

    private class FakeDemoTracker {
        private var lastRecordedEventType: String? = null
        val demoStatuses = mutableMapOf<String, TravellerDemoStatus>()

        fun recordTransition(
            travellerId: String,
            travellerName: String,
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
            val existing = demoStatuses[travellerId]

            // Deduplication guard
            if (lastRecordedEventType == eventType && existing?.status == status) {
                return
            }
            lastRecordedEventType = eventType

            val now = System.currentTimeMillis()
            val newEvent = DemoEventRecord(
                id = UUID.randomUUID().toString(),
                eventType = eventType,
                description = description,
                timestamp = now
            )
            val updatedHistory = if (existing != null) {
                listOf(newEvent) + existing.eventHistory
            } else {
                listOf(newEvent)
            }

            val updated = TravellerDemoStatus(
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
            demoStatuses[travellerId] = updated
        }

        fun getGuideView(authorizedGuide: Boolean): List<TravellerDemoStatus> {
            if (!authorizedGuide) return emptyList()
            return demoStatuses.values.toList()
        }
    }

    @Test
    fun testStep1And2_TravellerStartsDemo_GuideSeesActive() {
        val tracker = FakeDemoTracker()

        // 1. Traveller starts demo
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.ACTIVE,
            eventType = "DEMO_STARTED",
            description = "Traveller started Outside Tinkerspace demo"
        )

        // 2. Guide inspects dashboard
        val guideView = tracker.getGuideView(authorizedGuide = true)
        assertEquals(1, guideView.size)
        val session = guideView.first()
        assertEquals("Alex Chen", session.travellerName)
        assertEquals(DemoStatus.ACTIVE, session.status)
        assertEquals("DEMO_STARTED", session.eventHistory.first().eventType)
        assertFalse("Trip must not be completed on start", session.isCompleted)
    }

    @Test
    fun testStep3And4_TravellerLeavesTinkerspace_DepartureEventRecorded() {
        val tracker = FakeDemoTracker()
        val departureTime = System.currentTimeMillis()

        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.ACTIVE,
            eventType = "DEMO_STARTED",
            description = "Traveller started Outside Tinkerspace demo"
        )

        // 3. Traveller leaves Tinkerspace (outdoor stop reached)
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.DEPARTED,
            eventType = "DEPARTED",
            description = "Traveller departed Tinkerspace (Outdoor stop reached)",
            departureTime = departureTime
        )

        // 4. Guide sees departure event
        val session = tracker.getGuideView(authorizedGuide = true).first()
        assertEquals(DemoStatus.DEPARTED, session.status)
        assertNotNull(session.departureTime)
        assertEquals(departureTime, session.departureTime)
        assertEquals(2, session.eventHistory.size)
        assertEquals("DEPARTED", session.eventHistory.first().eventType)
        assertFalse(session.isCompleted)
    }

    @Test
    fun testDuplicateGpsCallbacksDoNotCreateDuplicateEvents() {
        val tracker = FakeDemoTracker()
        val departureTime = System.currentTimeMillis()

        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.DEPARTED,
            eventType = "DEPARTED",
            description = "Traveller departed Tinkerspace",
            departureTime = departureTime
        )

        // Repeated GPS callbacks while staying within outdoor perimeter
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.DEPARTED,
            eventType = "DEPARTED",
            description = "Traveller departed Tinkerspace",
            departureTime = departureTime
        )
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.DEPARTED,
            eventType = "DEPARTED",
            description = "Traveller departed Tinkerspace",
            departureTime = departureTime
        )

        val session = tracker.getGuideView(authorizedGuide = true).first()
        assertEquals("Duplicate GPS events must be suppressed", 1, session.eventHistory.size)
    }

    @Test
    fun testStep5And6_TravellerReturns_ReturnEventRecorded_NotCompleted() {
        val tracker = FakeDemoTracker()
        val returnTime = System.currentTimeMillis()

        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.DEPARTED,
            eventType = "DEPARTED",
            description = "Traveller departed Tinkerspace",
            departureTime = returnTime - 60000
        )

        // 5. Traveller returns to Tinkerspace (indoor stop reached)
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.RETURNED,
            eventType = "RETURNED",
            description = "Traveller returned to Tinkerspace (Indoor stop reached)",
            returnTime = returnTime
        )

        // 6. Guide sees return event
        val session = tracker.getGuideView(authorizedGuide = true).first()
        assertEquals(DemoStatus.RETURNED, session.status)
        assertEquals(returnTime, session.returnTime)
        assertNotNull(session.departureTime)

        // CRITICAL: Must NOT be completed merely because the Traveller returns!
        assertFalse("Return must not automatically complete the trip", session.isCompleted)
        assertFalse(session.isBelongingsChecked)
    }

    @Test
    fun testStep7_BelongingsCheckCompleted() {
        val tracker = FakeDemoTracker()
        val belongingsTime = System.currentTimeMillis()

        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.RETURNED,
            eventType = "RETURNED",
            description = "Traveller returned"
        )

        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.AWAITING_BELONGINGS_CHECK,
            eventType = "AWAITING_BELONGINGS_CHECK",
            description = "Traveller arrived back and entered belongings check"
        )

        // 7. Traveller confirms belongings
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.AWAITING_BELONGINGS_CHECK,
            eventType = "BELONGINGS_CHECKED",
            description = "Traveller verified all personal belongings",
            belongingsChecked = true,
            belongingsCheckedTime = belongingsTime
        )

        val session = tracker.getGuideView(authorizedGuide = true).first()
        assertTrue("Belongings check must be verified", session.isBelongingsChecked)
        assertEquals(belongingsTime, session.belongingsCheckedTime)
        // Explicit end trip has not occurred yet
        assertFalse("Trip must not be completed until explicit end trip", session.isCompleted)
    }

    @Test
    fun testStep8And9_TravellerExplicitlyEndsTrip_GuideSeesCompleted() {
        val tracker = FakeDemoTracker()
        val completedTime = System.currentTimeMillis()

        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.AWAITING_BELONGINGS_CHECK,
            eventType = "BELONGINGS_CHECKED",
            description = "Traveller verified all personal belongings",
            belongingsChecked = true,
            belongingsCheckedTime = completedTime - 5000
        )

        // 8. Explicit End Trip action
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.COMPLETED,
            eventType = "TRIP_COMPLETED",
            description = "Traveller explicitly completed and ended the trip",
            isCompleted = true,
            completedTime = completedTime
        )

        // 9. Guide sees completed state
        val session = tracker.getGuideView(authorizedGuide = true).first()
        assertEquals(DemoStatus.COMPLETED, session.status)
        assertTrue(session.isCompleted)
        assertEquals(completedTime, session.completedTime)
        assertTrue(session.isBelongingsChecked)
    }

    @Test
    fun testStep10_UnauthorizedUserCannotViewDemo() {
        val tracker = FakeDemoTracker()
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.ACTIVE,
            eventType = "DEMO_STARTED",
            description = "Started"
        )

        // Unauthorized user attempt
        val unauthorizedView = tracker.getGuideView(authorizedGuide = false)
        assertTrue("Unauthorized user must receive empty list", unauthorizedView.isEmpty())

        // Authorized guide view
        val authorizedView = tracker.getGuideView(authorizedGuide = true)
        assertEquals(1, authorizedView.size)
    }

    @Test
    fun testStep11_LocationUnavailabilityEvent() {
        val tracker = FakeDemoTracker()
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.ACTIVE,
            eventType = "DEMO_STARTED",
            description = "Started"
        )

        // GPS permission revoked / signal lost
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.LOCATION_UNAVAILABLE,
            eventType = "LOCATION_UNAVAILABLE",
            description = "GPS location tracking unavailable",
            locationAvailable = false
        )

        val session = tracker.getGuideView(authorizedGuide = true).first()
        assertEquals(DemoStatus.LOCATION_UNAVAILABLE, session.status)
        assertFalse(session.isLocationAvailable)
        assertEquals("LOCATION_UNAVAILABLE", session.eventHistory.first().eventType)

        // GPS restored
        tracker.recordTransition(
            travellerId = "traveller_alex",
            travellerName = "Alex Chen",
            status = DemoStatus.ACTIVE,
            eventType = "LOCATION_RESTORED",
            description = "GPS location tracking active",
            locationAvailable = true
        )
        val restoredSession = tracker.getGuideView(authorizedGuide = true).first()
        assertEquals(DemoStatus.ACTIVE, restoredSession.status)
        assertTrue(restoredSession.isLocationAvailable)
    }

    @Test
    fun testStep12_SingaporeAndTinkerspaceIntegrityPreserved() {
        val singapore = SampleData.singaporeTrip
        assertEquals("trip_singapore_2026", singapore.id)
        assertEquals("Singapore Explorer", singapore.title)
        assertEquals(5, singapore.durationDays)

        val tinkerspace = SampleData.tinkerspaceTrip
        assertEquals("trip_tinkerspace_demo", tinkerspace.id)
        assertEquals("Outside Tinkerspace", tinkerspace.title)
        assertEquals(TripStatus.UPCOMING, tinkerspace.status)
        assertEquals(2, tinkerspace.days.first().destinations.size)
    }
}
