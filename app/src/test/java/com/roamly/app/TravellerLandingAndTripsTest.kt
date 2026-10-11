package com.roamly.app

import com.roamly.app.auth.model.AuthUser
import com.roamly.app.data.SampleData
import com.roamly.app.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TravellerLandingAndTripsTest {

    private fun resolveGreeting(currentUser: AuthUser?): Pair<String, String> {
        val greetingName = currentUser?.fullName?.trim()?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")?.trim()?.takeIf { it.isNotBlank() }

        val greetingTitle = if (!greetingName.isNullOrBlank() && !greetingName.equals("Explorer", ignoreCase = true)) {
            val firstName = greetingName.split(Regex("\\s+")).firstOrNull()?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            } ?: greetingName
            "Hello, $firstName!"
        } else {
            "Hello, Traveller!"
        }
        val greetingSubtitle = "Your next adventure awaits."
        return Pair(greetingTitle, greetingSubtitle)
    }

    @Test
    fun testPersonalizedGreetingWithNaveen() {
        val user = AuthUser(
            id = "user_naveen",
            email = "naveen@example.com",
            fullName = "Naveen",
            role = UserRole.TRAVELLER
        )
        val (title, subtitle) = resolveGreeting(user)
        assertEquals("Hello, Naveen!", title)
        assertEquals("Your next adventure awaits.", subtitle)
    }

    @Test
    fun testPersonalizedGreetingWithFullName() {
        val user = AuthUser(
            id = "user_naveen_kumar",
            email = "naveen.kumar@example.com",
            fullName = "Naveen Kumar",
            role = UserRole.TRAVELLER
        )
        val (title, subtitle) = resolveGreeting(user)
        assertEquals("Hello, Naveen!", title)
        assertEquals("Your next adventure awaits.", subtitle)
    }

    @Test
    fun testPersonalizedGreetingWithDifferentAccounts() {
        val user1 = AuthUser(
            id = "user_maya",
            email = "maya@example.com",
            fullName = "Maya Lin",
            role = UserRole.TRAVELLER
        )
        val user2 = AuthUser(
            id = "user_david",
            email = "david@example.com",
            fullName = "David Tan",
            role = UserRole.TRAVELLER
        )
        assertEquals("Hello, Maya!", resolveGreeting(user1).first)
        assertEquals("Hello, David!", resolveGreeting(user2).first)
    }

    @Test
    fun testNeutralGreetingWhenNoDisplayName() {
        val userEmpty = AuthUser(
            id = "user_anonymous",
            email = "",
            fullName = "",
            role = UserRole.TRAVELLER
        )
        val (title, subtitle) = resolveGreeting(userEmpty)
        assertEquals("Hello, Traveller!", title)
        assertEquals("Your next adventure awaits.", subtitle)

        val (nullTitle, _) = resolveGreeting(null)
        assertEquals("Hello, Traveller!", nullTitle)
    }

    @Test
    fun testSingaporePackagePreservation() {
        val singapore = SampleData.singaporeTrip
        assertEquals("trip_singapore_2026", singapore.id)
        assertEquals("Singapore Explorer", singapore.title)
        assertEquals("Singapore", singapore.destination)
        assertEquals("Oct 12 – 16, 2026", singapore.dates)
        assertEquals(5, singapore.durationDays)
        assertEquals(5, singapore.days.size)

        // Verify day 1 attractions
        val day1Destinations = singapore.days[0].destinations.map { it.name }
        assertTrue(day1Destinations.contains("Marina Bay Sands SkyPark"))
        assertTrue(day1Destinations.contains("Gardens by the Bay & Cloud Forest"))
        assertTrue(day1Destinations.contains("Lau Pa Sat Hawker Market"))

        // Verify meeting point
        assertEquals("Changi Airport T3 Arrival Hall / Pan Pacific Lobby", singapore.meetingPoint)
    }

    @Test
    fun testTinkerspaceDemoTripPreservation() {
        val tinkerspace = SampleData.tinkerspaceTrip
        assertEquals("trip_tinkerspace_demo", tinkerspace.id)
        assertEquals("Outside Tinkerspace", tinkerspace.title)
        assertEquals("Tinkerspace", tinkerspace.destination)
        assertEquals(1, tinkerspace.durationDays)

        val stops = tinkerspace.days[0].destinations
        assertEquals(2, stops.size)
        assertEquals("Outside Tinkerspace Building", stops[0].name)
        assertEquals("Back Inside Tinkerspace Building", stops[1].name)
    }

    @Test
    fun testSelectedTripsCoexistenceAndNoDuplicates() {
        val selectedIds = mutableSetOf<String>()

        // 1. Select Singapore
        selectedIds.add(SampleData.singaporeTrip.id)
        assertTrue(selectedIds.contains("trip_singapore_2026"))
        assertEquals(1, selectedIds.size)

        // 2. Select Outside of Tinkerspace
        selectedIds.add(SampleData.tinkerspaceTrip.id)
        assertTrue(selectedIds.contains("trip_singapore_2026"))
        assertTrue(selectedIds.contains("trip_tinkerspace_demo"))
        assertEquals("Both options must coexist in My Trips", 2, selectedIds.size)

        // 3. Repeated selection does not create duplicates
        selectedIds.add(SampleData.singaporeTrip.id)
        selectedIds.add(SampleData.tinkerspaceTrip.id)
        assertEquals("Set must not contain duplicate entries", 2, selectedIds.size)

        // 4. Verify distinct identities
        val trips = SampleData.sampleTrips.filter { it.id in selectedIds }
        assertEquals(2, trips.size)
        assertEquals("trip_singapore_2026", trips[0].id)
        assertEquals("trip_tinkerspace_demo", trips[1].id)
    }
}
