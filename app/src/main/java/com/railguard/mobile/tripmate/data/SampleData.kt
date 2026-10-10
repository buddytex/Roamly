package com.railguard.mobile.tripmate.data

import com.railguard.mobile.tripmate.models.*
import java.util.UUID

object SampleData {
    fun createSampleDubaiTrip(): Trip {
        val dest1 = Destination(
            id = "dest-burj-khalifa",
            name = "Burj Khalifa & Downtown Promenade",
            shortDescription = "World's tallest skyscraper standing at 828 meters with 360° views.",
            fullOverview = "Soaring 828 meters above Downtown Dubai, the Burj Khalifa is a marvel of modern engineering and architecture. The tour encompasses the observation decks on levels 124 and 125, the landscaped gardens of Burj Park, and the lakeside promenade facing the Dubai Fountain.",
            whyVisit = "Unrivaled panoramic vistas across the Arabian Gulf, futuristic architecture, and the centerpiece of contemporary Dubai.",
            plannedTime = "09:00 AM - 11:30 AM",
            meetingPoint = "At The Top Ticket Concourse (LG Floor, Dubai Mall)",
            latitude = 25.1972,
            longitude = 55.2744,
            status = DestinationStatus.CURRENT,
            activities = listOf(
                ActivityItem(
                    id = "act-1-1",
                    title = "At The Top Observation Deck Entry",
                    time = "09:15 AM",
                    description = "Take the 10 m/s double-deck elevator to level 124 for open-air terrace viewing.",
                    isCompleted = true
                ),
                ActivityItem(
                    id = "act-1-2",
                    title = "Architectural Presentation",
                    time = "10:15 AM",
                    description = "Briefing on the Y-shaped buttressed core design inspired by the Hymenocallis desert flower.",
                    isCompleted = false
                ),
                ActivityItem(
                    id = "act-1-3",
                    title = "Promenade Photo Walk & Fountain Lake",
                    time = "11:00 AM",
                    description = "Group photography session from the waterfront boardwalk.",
                    isCompleted = false
                )
            ),
            instructions = listOf(
                "Display digital admission QR codes directly from your mobile screen.",
                "Meeting point strictly at 11:30 AM at Souk Al Bahar bridge entrance.",
                "Audio guide headsets are provided at the concourse."
            ),
            precautions = listOf(
                "Security screening is strict: no luggage or large backpacks permitted on upper decks.",
                "Observation terrace can be breezy; secure loose hats and accessories.",
                "Stay mindful of escalator safety in high-traffic mall corridors."
            ),
            topSights = listOf(
                "Level 124 Outdoor Observation Terrace",
                "Falcon's Eye View Interactive Telescope",
                "The Dubai Fountain Boardwalk",
                "Burj Park Art Sculptures"
            ),
            suggestedExploration = listOf(
                "1. Check in at ticket concourse (LG Floor)",
                "2. Ride elevator to 124th floor",
                "3. Walk the 360° perimeter deck",
                "4. Exit to Burj Park for base-to-spire photography"
            )
        )

        val dest2 = Destination(
            id = "dest-al-fahidi",
            name = "Al Fahidi Historical Neighborhood & Souks",
            shortDescription = "19th-century wind-tower district, heritage museums, and abra creek crossing.",
            fullOverview = "Established in the late 1890s, Al Fahidi (formerly Al Bastakiya) preserves Dubai's authentic maritime and trading roots. Wander narrow sandstone alleys flanked by barajeel (traditional cooling wind towers), then cross Dubai Creek on a wooden abra to the sensory Spice and Gold Souks.",
            whyVisit = "Authentic contrast to modern skyscrapers, offering living insight into Emirati culture, tea traditions, and historical sea trade.",
            plannedTime = "12:15 PM - 02:45 PM",
            meetingPoint = "Al Seef Heritage Dhow Pier #3",
            latitude = 25.2631,
            longitude = 55.2972,
            status = DestinationStatus.UPCOMING,
            activities = listOf(
                ActivityItem(
                    id = "act-2-1",
                    title = "Al Fahidi Heritage Walking Tour",
                    time = "12:30 PM",
                    description = "Guided exploration of coral-stone buildings, wind towers, and courtyard galleries.",
                    isCompleted = false
                ),
                ActivityItem(
                    id = "act-2-2",
                    title = "Emirati Tea & Dates Tasting",
                    time = "01:15 PM",
                    description = "Cultural hospitality session featuring gahwa (cardamom coffee) and fresh dates.",
                    isCompleted = false
                ),
                ActivityItem(
                    id = "act-2-3",
                    title = "Traditional Wooden Abra Creek Crossing",
                    time = "02:00 PM",
                    description = "Scenic boat transit across Dubai Creek to the Deira Spice and Gold Souks.",
                    isCompleted = false
                )
            ),
            instructions = listOf(
                "Shoulders and knees should be covered when entering cultural centers and mosques.",
                "Small dirham cash is ideal for traditional abra boat fare (AED 1-2).",
                "Rejoin guide at Deira Old Souk Marine Transport Station at 02:45 PM."
            ),
            precautions = listOf(
                "Narrow cobblestone alleyways require comfortable, closed-toe walking shoes.",
                "Stay together during the souk crossing as streets become densely crowded.",
                "Keep personal bags zippered and in front while browsing spice stalls."
            ),
            topSights = listOf(
                "Barajeel Wind Tower Architecture",
                "Sheikh Mohammed Cultural Understanding Center",
                "Dubai Coffee Museum",
                "Traditional Abra Creek Waterway",
                "Deira Saffron & Frankincense Souk"
            ),
            suggestedExploration = listOf(
                "1. Al Fahidi Courtyards & Wind Towers",
                "2. Arabian Tea House cultural refreshment",
                "3. Bur Dubai Abra Station boarding",
                "4. Deira Spice Souk aromatic spice tasting"
            )
        )

        val dest3 = Destination(
            id = "dest-desert-safari",
            name = "Lahbab Red Dunes Desert Safari",
            shortDescription = "Thrilling dune exploration, sandboarding, Bedouin camp, and starry dinner.",
            fullOverview = "Journey deep into the Lahbab Desert known for its distinctive high-silica crimson sand dunes. Experience 4x4 dune navigation, sunset photography from cresting dunes, camel caravan riding, and an authentic Bedouin camp dinner under the desert sky.",
            whyVisit = "Immersion in the majestic Arabian desert landscape, adrenaline-filled dune trekking, and heritage culinary arts.",
            plannedTime = "04:00 PM - 08:30 PM",
            meetingPoint = "Desert Safari Convoy Staging Area (E44 Highway Hub)",
            latitude = 24.9780,
            longitude = 55.6120,
            status = DestinationStatus.UPCOMING,
            activities = listOf(
                ActivityItem(
                    id = "act-3-1",
                    title = "4x4 Desert Dune Driving",
                    time = "04:30 PM",
                    description = "Professional convoy dune navigation across the Lahbab red sands.",
                    isCompleted = false
                ),
                ActivityItem(
                    id = "act-3-2",
                    title = "Red Dune Sunset Photography & Sandboarding",
                    time = "05:45 PM",
                    description = "Glide down the Big Red dune and photograph the golden hour sunset.",
                    isCompleted = false
                ),
                ActivityItem(
                    id = "act-3-3",
                    title = "Bedouin Camp Barbecue & Folk Dance",
                    time = "07:00 PM",
                    description = "Traditional campfire feast, Tanoura spinning dance, and stargazing.",
                    isCompleted = false
                )
            ),
            instructions = listOf(
                "Wear light, loose clothing; bring a light cardigan or windbreaker for evening desert breeze.",
                "Remain seated with seatbelts securely fastened in 4x4 vehicles at all times.",
                "Meeting point for return bus pickup is the Camp Main Gate at 08:30 PM."
            ),
            precautions = listOf(
                "Fine desert sand can damage camera lenses and charge ports; use protective pouches.",
                "Take motion sickness precautions if sensitive to dune driving.",
                "Always remain within visible sight of guide markers in the open desert."
            ),
            topSights = listOf(
                "Big Red Lahbab Sand Dune",
                "Desert Horizon Sunset Crest",
                "Traditional Bedouin Majlis Camp",
                "Night Sky Constellations Over the Dunes"
            ),
            suggestedExploration = listOf(
                "1. Convoy tire deflation & staging",
                "2. Red dune ride to sunset ridge",
                "3. Camel caravan station ride",
                "4. Evening Bedouin feast & Tanoura celebration"
            )
        )

        val travelers = listOf(
            Traveler(
                id = "trav-1",
                name = "Elena Rostova",
                phone = "+1 415 555 0192",
                currentStop = "Burj Khalifa Observation Deck",
                lastCheckInTime = "09:18 AM",
                isCheckInOverdue = false,
                hasPendingSos = false,
                volunteeredLat = 25.1974,
                volunteeredLng = 55.2742
            ),
            Traveler(
                id = "trav-2",
                name = "Marcus Vance",
                phone = "+44 20 7946 0912",
                currentStop = "Burj Khalifa Concourse",
                lastCheckInTime = "09:12 AM",
                isCheckInOverdue = false,
                hasPendingSos = false,
                volunteeredLat = 25.1969,
                volunteeredLng = 55.2748
            ),
            Traveler(
                id = "trav-3",
                name = "Kenji Takahashi",
                phone = "+81 90 1234 5678",
                currentStop = "Downtown Cafe Area",
                lastCheckInTime = "08:15 AM (55m ago)",
                isCheckInOverdue = true, // Highlights group coordination exception!
                hasPendingSos = false,
                volunteeredLat = 25.1950,
                volunteeredLng = 55.2760
            ),
            Traveler(
                id = "trav-4",
                name = "Sarah Jenkins",
                phone = "+1 312 555 0147",
                currentStop = "Level 125 Gift Shop",
                lastCheckInTime = "09:20 AM",
                isCheckInOverdue = false,
                hasPendingSos = false,
                volunteeredLat = 25.1973,
                volunteeredLng = 55.2743
            ),
            Traveler(
                id = "trav-5",
                name = "Aisha Patel",
                phone = "+91 98200 12345",
                currentStop = "Burj Khalifa Terrace",
                lastCheckInTime = "09:22 AM",
                isCheckInOverdue = false,
                hasPendingSos = false,
                volunteeredLat = 25.1971,
                volunteeredLng = 55.2745
            )
        )

        val packing = listOf(
            PackingItem(title = "Passport & UAE Tourist Visa Copy", category = "Documents", isPacked = true, isRequiredByGuide = true),
            PackingItem(title = "Travel Health Insurance Card", category = "Documents", isPacked = true, isRequiredByGuide = true),
            PackingItem(title = "Digital Tour Voucher QR", category = "Documents", isPacked = true, isRequiredByGuide = true),
            PackingItem(title = "Universal Plug Adapter (Type G / UK)", category = "Electronics", isPacked = true),
            PackingItem(title = "High-Capacity Power Bank & Cables", category = "Electronics", isPacked = true, isRequiredByGuide = true),
            PackingItem(title = "Compact Camera & Extra SD Card", category = "Electronics", isPacked = false),
            PackingItem(title = "Light Breathable Cotton Attire", category = "Clothing", isPacked = true),
            PackingItem(title = "Modest Attire for Heritage Areas (Cover Knees/Shoulders)", category = "Clothing", isPacked = true, isRequiredByGuide = true),
            PackingItem(title = "Comfortable Walking Shoes for Souks", category = "Clothing", isPacked = true),
            PackingItem(title = "Evening Desert Windbreaker / Shawl", category = "Clothing", isPacked = false),
            PackingItem(title = "Broad-Spectrum Sunscreen & Sunglasses", category = "Health & Essentials", isPacked = true),
            PackingItem(title = "Reusable Insulated Water Bottle", category = "Health & Essentials", isPacked = true),
            PackingItem(title = "Personal Prescriptions & Electrolytes", category = "Health & Essentials", isPacked = true),
            PackingItem(title = "Motion Sickness Tablets (For Dune Drive)", category = "Health & Essentials", isPacked = false)
        )

        val belongings = listOf(
            BelongingItem(name = "Smartphone & Charger Cable", isVerified = true),
            BelongingItem(name = "Wallet, Bank Cards & Currency", isVerified = true),
            BelongingItem(name = "Daypack / Crossbody Bag", isVerified = true),
            BelongingItem(name = "Passport & Identity Folder", isVerified = true),
            BelongingItem(name = "Sunglasses & Sun Protective Hat", isVerified = true),
            BelongingItem(name = "Digital Camera & Memory Pouches", isVerified = false),
            BelongingItem(name = "Insulated Water Bottle", isVerified = true),
            BelongingItem(name = "Hotel Room Key Card", isVerified = true)
        )

        val announcements = listOf(
            TripAnnouncement(
                title = "Welcome to Dubai Grand Expedition!",
                content = "Please ensure your phone is charged and meet Guide Sari at the Dubai Mall Ground Atrium by 08:30 AM.",
                timestamp = "08:00 AM",
                isUrgent = false
            ),
            TripAnnouncement(
                title = "Reminder: Al Fahidi Dress Etiquette",
                content = "For our midday heritage walking tour, please ensure shoulders and knees are covered for cultural center access.",
                timestamp = "09:00 AM",
                isUrgent = false
            )
        )

        return Trip(
            id = "trip-dubai-2026",
            title = "Dubai Grand Cultural & Modern Wonders Expedition",
            description = "A curated journey connecting Downtown architectural marvels, historic wind-tower alleys, traditional creek abras, and pristine red sand dunes.",
            agencyName = "Voyage Horizon Tours & Safaris",
            guideName = "Sari Al-Mansoor",
            guideContact = "+971 50 123 4567",
            startDate = "Today, 08:30 AM - 09:00 PM",
            meetingPoint = "Dubai Mall Ground Atrium (Near Souk Al Bahar Bridge)",
            status = TripStatus.ACTIVE,
            destinations = listOf(dest1, dest2, dest3),
            travelers = travelers,
            packingList = packing,
            belongingsList = belongings,
            announcements = announcements,
            currentDestinationIndex = 0
        )
    }
}
