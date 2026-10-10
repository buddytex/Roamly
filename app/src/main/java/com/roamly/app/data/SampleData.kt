package com.roamly.app.data

import com.roamly.app.model.ActivityItem
import com.roamly.app.model.ChecklistCategory
import com.roamly.app.model.ChecklistItem
import com.roamly.app.model.DestinationItem
import com.roamly.app.model.DocumentGuide
import com.roamly.app.model.Trip
import com.roamly.app.model.TripDay
import com.roamly.app.model.TripStatus
import com.roamly.app.model.TravellerRecord

object SampleData {

    val sampleTrips: List<Trip> = listOf(
        Trip(
            id = "trip_singapore_2026",
            title = "Singapore Explorer",
            destination = "Singapore",
            country = "Singapore",
            dates = "Oct 12 – 16, 2026",
            durationDays = 5,
            travelerCount = 4,
            travelers = listOf("Alex Chen", "Maya Lin", "David Tan", "Sarah Jenkins"),
            status = TripStatus.UPCOMING,
            summary = "A serene 5-day journey blending futuristic architecture, lush garden canopies, vibrant cultural enclaves, and world-renowned hawker cuisine.",
            meetingPoint = "Changi Airport T3 Arrival Hall / Pan Pacific Lobby",
            announcements = listOf(
                "Welcome to Singapore! Meet at Pan Pacific Lobby at 09:30 AM tomorrow for morning briefing.",
                "Remember to wear lightweight modest attire covering shoulders & knees for the Chinatown temple stop."
            ),
            maxGroupSize = 8,
            isGuideManaged = true,
            days = listOf(
                TripDay(
                    dayNumber = 1,
                    title = "Iconic Bay & Futuristic Gardens",
                    date = "Monday, Oct 12",
                    destinations = listOf(
                        DestinationItem(
                            id = "dest_mbs",
                            name = "Marina Bay Sands SkyPark",
                            category = "Landmark",
                            timeSlot = "10:00 AM",
                            duration = "1.5 hrs",
                            shortDescription = "Panoramic 57-storey observation deck overlooking the Singapore Strait.",
                            fullDescription = "Perched 200 meters atop three hotel towers, the SkyPark offers breathtaking 360-degree vistas of the city skyline, bustling shipping channels, and Gardens by the Bay.",
                            practicalTips = listOf(
                                "Book timed-entry slot online to bypass the ground ticket queue.",
                                "Morning visits typically offer the clearest skies before midday haze.",
                                "Keep your camera strap secured when approaching glass barriers."
                            ),
                            precautions = listOf(
                                "High wind conditions on open observation deck; hold onto hats and light accessories.",
                                "Weather closure: Deck access may pause briefly during sudden electrical thunderstorms."
                            ),
                            activities = listOf(
                                ActivityItem("act_mbs_1", "Take panoramic photo overlooking the Singapore Strait", true),
                                ActivityItem("act_mbs_2", "Spot the Marina Barrage and Helix Bridge from above", false),
                                ActivityItem("act_mbs_3", "Enjoy refreshment at the rooftop deck café", false)
                            ),
                            isCompleted = false
                        ),
                        DestinationItem(
                            id = "dest_gardens",
                            name = "Gardens by the Bay & Cloud Forest",
                            category = "Nature & Flora",
                            timeSlot = "02:30 PM",
                            duration = "3.0 hrs",
                            shortDescription = "World's largest climate-controlled glass greenhouses with indoor mountain waterfalls.",
                            fullDescription = "Home to the 35-meter-tall Cloud Mountain veiled in mist, exotic orchids, pitcher plants, and the soaring Supertrees connected by the OCBC Skyway.",
                            practicalTips = listOf(
                                "Bring a light cardigan or wrap — the conservatories are cooled to a crisp 23°C.",
                                "Stay until 7:45 PM for the free Garden Rhapsody music and light choreography."
                            ),
                            precautions = listOf(
                                "Do not touch or disturb sensitive cloud forest orchid exhibits.",
                                "Walking surfaces can be slightly damp near the mist dispensers."
                            ),
                            activities = listOf(
                                ActivityItem("act_gbb_1", "Walk the aerial catwalk around the indoor waterfall", true),
                                ActivityItem("act_gbb_2", "Experience the Flower Dome Mediterranean garden", false),
                                ActivityItem("act_gbb_3", "Watch the evening Garden Rhapsody Supertree light show", false)
                            ),
                            isCompleted = false
                        ),
                        DestinationItem(
                            id = "dest_laupasat",
                            name = "Lau Pa Sat Hawker Market",
                            category = "Hawker Dining",
                            timeSlot = "07:30 PM",
                            duration = "1.5 hrs",
                            shortDescription = "Victorian cast-iron landmark with the famous evening open-air Satay Street.",
                            fullDescription = "Built in 1894, Lau Pa Sat seamlessly marries colonial architectural elegance with Singapore's vibrant UNESCO-recognized street food culture.",
                            practicalTips = listOf(
                                "Satay Street (Boon Tat Street) closes to vehicles from 7:00 PM nightly.",
                                "Look out for 'Choping' — tissue packs placed on tables mean they are reserved.",
                                "Carry cash or local mobile pay for traditional beverage stalls."
                            ),
                            precautions = listOf(
                                "Inform stalls in advance if you have peanut or shellfish allergies (satay sauce contains peanuts).",
                                "Brisk crowds during peak dinner hours; stay aware of personal bags."
                            ),
                            activities = listOf(
                                ActivityItem("act_lps_1", "Order mixed chicken and beef satay skewers from Stall 7 & 8", false),
                                ActivityItem("act_lps_2", "Sip fresh sugarcane juice with kalamansi lime", false),
                                ActivityItem("act_lps_3", "Sample Hainanese chicken rice from the heritage aisle", false)
                            ),
                            isCompleted = false
                        )
                    )
                ),
                TripDay(
                    dayNumber = 2,
                    title = "Heritage Quarters & Cultural Flavors",
                    date = "Tuesday, Oct 13",
                    destinations = listOf(
                        DestinationItem(
                            id = "dest_chinatown",
                            name = "Buddha Tooth Relic Temple & Chinatown",
                            category = "Heritage & Culture",
                            timeSlot = "09:30 AM",
                            duration = "2.0 hrs",
                            shortDescription = "Tang Dynasty-styled temple housing sacred relic, surrounded by historic shophouses.",
                            fullDescription = "Immerse in the Buddhist cultural museum, ornate golden prayer wheels, and stroll the preserved shophouse arcades of Pagoda and Smith Streets.",
                            practicalTips = listOf(
                                "Complimentary sarongs are provided at the entrance if knees or shoulders are exposed.",
                                "Visit the quiet rooftop Orchid Garden and prayer wheel pavilion for tranquility."
                            ),
                            precautions = listOf(
                                "Remove hats and silence mobile phones before entering main shrine hall.",
                                "Respect worshippers during ongoing morning chanting ceremonies."
                            ),
                            activities = listOf(
                                ActivityItem("act_ct_1", "Spin the giant prayer wheel on the rooftop terrace", false),
                                ActivityItem("act_ct_2", "Browse traditional herbal medicine apothecaries", false),
                                ActivityItem("act_ct_3", "Taste warm egg tarts from Tong Heng bakery", false)
                            ),
                            isCompleted = false
                        ),
                        DestinationItem(
                            id = "dest_kampongglam",
                            name = "Kampong Glam & Haji Lane",
                            category = "Art & Culture",
                            timeSlot = "02:00 PM",
                            duration = "2.5 hrs",
                            shortDescription = "Historic Malay-Arab enclave with golden Sultan Mosque and vibrant indie boutiques.",
                            fullDescription = "Discover colorful street murals along Haji Lane, fragrant artisanal perfume houses on Arab Street, and the grand golden dome of Sultan Mosque.",
                            practicalTips = listOf(
                                "Best photography lighting along Haji Lane murals is early afternoon.",
                                "Try traditional Teh Tarik (pulled milk tea) at Bhai Sarbat."
                            ),
                            precautions = listOf(
                                "Sultan Mosque interior is closed to tourists during Friday prayer sessions.",
                                "Narrow alleys can become crowded; be courteous when taking street photos."
                            ),
                            activities = listOf(
                                ActivityItem("act_kg_1", "Photograph Sultan Mosque framed by palm-lined Muscat Street", false),
                                ActivityItem("act_kg_2", "Explore indie fashion boutiques on Haji Lane", false),
                                ActivityItem("act_kg_3", "Sample authentic Nasi Padang or Murtabak", false)
                            ),
                            isCompleted = false
                        )
                    )
                ),
                TripDay(
                    dayNumber = 3,
                    title = "Civic District & Riverfront Strolls",
                    date = "Wednesday, Oct 14",
                    destinations = listOf(
                        DestinationItem(
                            id = "dest_nationalgallery",
                            name = "National Gallery Singapore",
                            category = "Museum & Art",
                            timeSlot = "10:30 AM",
                            duration = "2.5 hrs",
                            shortDescription = "Former City Hall and Supreme Court transformed into premier visual arts museum.",
                            fullDescription = "Spanning two national monuments connected by bridges of filigree metal and glass, presenting the largest public collection of modern art in Southeast Asia.",
                            practicalTips = listOf(
                                "Download the gallery's companion audio tour app for free curated guides.",
                                "Visit the rooftop garden terrace for views of the Padang and Marina Bay."
                            ),
                            precautions = listOf(
                                "No flash photography in specialized historical galleries.",
                                "Large backpacks must be checked at the complimentary coatroom."
                            ),
                            activities = listOf(
                                ActivityItem("act_ng_1", "Walk across the glass link-bridges between heritage courtrooms", false),
                                ActivityItem("act_ng_2", "Admire Georgette Chen and Raden Saleh masterpieces", false)
                            ),
                            isCompleted = false
                        ),
                        DestinationItem(
                            id = "dest_clarkequay",
                            name = "Clarke Quay River Bumboat Cruise",
                            category = "Leisure & River",
                            timeSlot = "06:30 PM",
                            duration = "1.5 hrs",
                            shortDescription = "Electric wooden bumboats gliding along the tranquil Singapore River at twilight.",
                            fullDescription = "Take in the historic bridges, Fullerton Hotel, the Merlion statue, and the dazzling evening reflections of Marina Bay Sands from the water.",
                            practicalTips = listOf(
                                "Depart roughly 20 minutes before sunset for the golden-hour skyline transition.",
                                "Boats depart every 15 minutes from the Clarke Quay Jetty."
                            ),
                            precautions = listOf(
                                "Keep life-vest instructions in mind and remain seated while boat is in transit."
                            ),
                            activities = listOf(
                                ActivityItem("act_cq_1", "Spot the historic Anderson and Cavenagh suspension bridges", false),
                                ActivityItem("act_cq_2", "Capture sunset illumination of the Merlion fountain", false)
                            ),
                            isCompleted = false
                        )
                    )
                ),
                TripDay(
                    dayNumber = 4,
                    title = "Sentosa Coastline & Island Escapes",
                    date = "Thursday, Oct 15",
                    destinations = listOf(
                        DestinationItem(
                            id = "dest_seaaquarium",
                            name = "S.E.A. Aquarium Sentosa",
                            category = "Marine Life",
                            timeSlot = "10:00 AM",
                            duration = "2.5 hrs",
                            shortDescription = "Colossal aquarium featuring hammerhead sharks, manta rays, and coral habitats.",
                            fullDescription = "One of the world's largest marine facilities, housing more than 100,000 marine animals across 40 diverse oceanic habitats.",
                            practicalTips = listOf(
                                "Take the Sentosa Express monorail from VivoCity directly to Resorts World.",
                                "The Open Ocean Habitat viewing panel offers serene seated relaxation."
                            ),
                            precautions = listOf(
                                "Flash photography is strictly prohibited to protect aquatic vision.",
                                "Strollers and walkers: elevator access is available at every zone."
                            ),
                            activities = listOf(
                                ActivityItem("act_sea_1", "Watch the majestic manta ray feeding session", false),
                                ActivityItem("act_sea_2", "Walk through the 360-degree acrylic shark underwater tunnel", false)
                            ),
                            isCompleted = false
                        ),
                        DestinationItem(
                            id = "dest_palawan",
                            name = "Palawan Beach & Suspension Bridge",
                            category = "Coastal Leisure",
                            timeSlot = "03:30 PM",
                            duration = "2.0 hrs",
                            shortDescription = "Cross the swaying wooden bridge to the Southernmost Point of Continental Asia.",
                            fullDescription = "White sand beach lined with tropical palms, calm swimming lagoons, and two timber viewing towers facing the open Singapore Strait.",
                            practicalTips = listOf(
                                "Complimentary Sentosa beach shuttle runs every 10 minutes.",
                                "Climb the timber towers for a refreshing coastal breeze and cargo ship views."
                            ),
                            precautions = listOf(
                                "Apply sunscreen (SPF 50) and stay well-hydrated under tropical humidity.",
                                "Observe lifeguard beach warning flags if entering the swimming lagoon."
                            ),
                            activities = listOf(
                                ActivityItem("act_pb_1", "Cross the timber suspension bridge to the islet", false),
                                ActivityItem("act_pb_2", "Relax under coconut palms with fresh coconut water", false)
                            ),
                            isCompleted = false
                        )
                    )
                ),
                TripDay(
                    dayNumber = 5,
                    title = "Jewel Changi Marvel & Farewell",
                    date = "Friday, Oct 16",
                    destinations = listOf(
                        DestinationItem(
                            id = "dest_botanicgardens",
                            name = "Singapore Botanic Gardens & Orchid Garden",
                            category = "UNESCO Heritage",
                            timeSlot = "09:00 AM",
                            duration = "2.0 hrs",
                            shortDescription = "165-year-old tropical botanical garden and Singapore's first UNESCO World Heritage Site.",
                            fullDescription = "Stroll along shaded palm valleys, the serene Swan Lake, and marvel at the National Orchid Garden's vibrant collection of over 1,000 species and 2,000 hybrids.",
                            practicalTips = listOf(
                                "Entry to the main Botanic Gardens is free; Orchid Garden has a nominal admission.",
                                "Early morning is ideal to beat the midday tropical heat."
                            ),
                            precautions = listOf(
                                "Stick to paved trails and avoid feeding local monitor lizards or waterfowl.",
                                "Mosquito repellent is recommended for wooded garden sections."
                            ),
                            activities = listOf(
                                ActivityItem("act_sbg_1", "Admire VIP celebrity orchids named after global figures", false),
                                ActivityItem("act_sbg_2", "Relax by Symphony Lake and the Shaw Foundation stage", false)
                            ),
                            isCompleted = false
                        ),
                        DestinationItem(
                            id = "dest_jewel",
                            name = "Jewel Changi & HSBC Rain Vortex",
                            category = "Modern Wonder",
                            timeSlot = "03:00 PM",
                            duration = "2.5 hrs",
                            shortDescription = "World's tallest indoor waterfall cascading 40 meters through a lush terraced forest.",
                            fullDescription = "Directly connected to airport terminals, Jewel seamlessly integrates nature and commerce under a toroidal glass-and-steel dome with Canopy Park mazes.",
                            practicalTips = listOf(
                                "Early baggage check-in is available right in Jewel for major international flights.",
                                "The best viewing deck is on Level 2 near the Shake Shack terrace."
                            ),
                            precautions = listOf(
                                "Keep carry-on bags zipped and trolley wheels locked near escalators."
                            ),
                            activities = listOf(
                                ActivityItem("act_jwl_1", "Photograph the 40m Rain Vortex from the forest trail", false),
                                ActivityItem("act_jwl_2", "Pick up souvenir Kaya coconut jam and Pandan chiffon cake", false),
                                ActivityItem("act_jwl_3", "Walk the Canopy Bridge with glass bottom panels", false)
                            ),
                            isCompleted = false
                        )
                    )
                )
            )
        ),
        Trip(
            id = "trip_penang_2026",
            title = "Penang Culinary & Shophouses",
            destination = "Penang",
            country = "Malaysia",
            dates = "Nov 5 – 8, 2026",
            durationDays = 4,
            travelerCount = 3,
            travelers = listOf("Alex Chen", "Maya Lin", "Kenji Sato"),
            status = TripStatus.PLANNING,
            summary = "Street food trails in historic George Town, clan jetties, and spice garden explorations.",
            meetingPoint = "Eastern & Oriental Hotel Lobby, George Town",
            announcements = listOf("Draft itinerary prepared; passport validity check pending for travellers."),
            maxGroupSize = 6,
            isGuideManaged = true,
            days = emptyList()
        ),
        Trip(
            id = "trip_kyoto_past",
            title = "Kyoto Zen & Ancient Shrines",
            destination = "Kyoto",
            country = "Japan",
            dates = "April 4 – 9, 2026",
            durationDays = 6,
            travelerCount = 2,
            travelers = listOf("Alex Chen", "Maya Lin"),
            status = TripStatus.COMPLETED,
            summary = "Tranquil bamboo groves, vermillion torii corridors, and traditional matcha ceremonies in historic Kyoto.",
            meetingPoint = "Kyoto Station Central Gate",
            announcements = listOf("Trip successfully completed. Thank you for travelling with us!"),
            maxGroupSize = 6,
            isGuideManaged = false,
            days = emptyList()
        )
    )

    val sampleChecklist: List<ChecklistItem> = listOf(
        // Documents
        ChecklistItem("chk_1", "Passports (valid 6+ months)", ChecklistCategory.DOCUMENTS, isPacked = true, isBelongingItem = true),
        ChecklistItem("chk_2", "SG Arrival Card confirmation (electronic)", ChecklistCategory.DOCUMENTS, isPacked = true, isBelongingItem = false),
        ChecklistItem("chk_3", "Return flight tickets & accommodation vouchers", ChecklistCategory.DOCUMENTS, isPacked = true, isBelongingItem = false),
        ChecklistItem("chk_4", "Travel insurance policy card & emergency hotline", ChecklistCategory.DOCUMENTS, isPacked = true, isBelongingItem = true),
        
        // Essentials & Belongings
        ChecklistItem("chk_5", "Primary wallet & contactless travel credit cards", ChecklistCategory.ESSENTIALS, isPacked = true, isBelongingItem = true),
        ChecklistItem("chk_6", "Singapore Dollar cash (small notes for hawkers)", ChecklistCategory.ESSENTIALS, isPacked = false, isBelongingItem = true),
        ChecklistItem("chk_7", "House keys & luggage TSA lock keys", ChecklistCategory.ESSENTIALS, isPacked = true, isBelongingItem = true),
        ChecklistItem("chk_8", "Reusable insulated water bottle", ChecklistCategory.ESSENTIALS, isPacked = false, isBelongingItem = true),
        ChecklistItem("chk_9", "UV polarized sunglasses", ChecklistCategory.ESSENTIALS, isPacked = true, isBelongingItem = true),

        // Clothing
        ChecklistItem("chk_10", "Light breathable linen / cotton shirts (5 days)", ChecklistCategory.CLOTHING, isPacked = false, isBelongingItem = false),
        ChecklistItem("chk_11", "Comfortable walking shoes / cushioned sneakers", ChecklistCategory.CLOTHING, isPacked = true, isBelongingItem = false),
        ChecklistItem("chk_12", "Modest temple outfit (covering shoulders & knees)", ChecklistCategory.CLOTHING, isPacked = true, isBelongingItem = false),
        ChecklistItem("chk_13", "Light cardigan / wrap (for chilly AC & Cloud Forest)", ChecklistCategory.CLOTHING, isPacked = false, isBelongingItem = false),
        ChecklistItem("chk_14", "Compact folding rain umbrella / poncho", ChecklistCategory.CLOTHING, isPacked = true, isBelongingItem = true),

        // Electronics
        ChecklistItem("chk_15", "Smartphones with local eSIM / roaming active", ChecklistCategory.ELECTRONICS, isPacked = true, isBelongingItem = true),
        ChecklistItem("chk_16", "Universal UK / Type-G 3-pin plug adapter", ChecklistCategory.ELECTRONICS, isPacked = true, isBelongingItem = true),
        ChecklistItem("chk_17", "High-capacity power bank (under 100Wh for flights)", ChecklistCategory.ELECTRONICS, isPacked = false, isBelongingItem = true),
        ChecklistItem("chk_18", "USB-C fast charging cables", ChecklistCategory.ELECTRONICS, isPacked = true, isBelongingItem = true),
        ChecklistItem("chk_19", "Camera, memory cards & backup batteries", ChecklistCategory.ELECTRONICS, isPacked = false, isBelongingItem = true),

        // Health
        ChecklistItem("chk_20", "Personal prescription medications & basic first aid", ChecklistCategory.HEALTH, isPacked = true, isBelongingItem = true),
        ChecklistItem("chk_21", "Broad-spectrum sunscreen (SPF 50+)", ChecklistCategory.HEALTH, isPacked = false, isBelongingItem = false),
        ChecklistItem("chk_22", "Tropical insect repellent (DEET or Picaridin)", ChecklistCategory.HEALTH, isPacked = true, isBelongingItem = false),
        ChecklistItem("chk_23", "Electrolyte hydration packs", ChecklistCategory.HEALTH, isPacked = false, isBelongingItem = false)
    )

    val documentGuides: List<DocumentGuide> = listOf(
        DocumentGuide(
            title = "Passport Validity Requirement",
            description = "Singapore requires international visitors to hold a passport valid for at least six (6) months beyond the planned date of departure.",
            officialSource = "Singapore Immigration & Checkpoints Authority (ICA)",
            officialUrl = "https://www.ica.gov.sg/enter-transit-depart/entering-singapore"
        ),
        DocumentGuide(
            title = "SG Arrival Card with Electronic Health Declaration",
            description = "All travellers must submit the electronic SG Arrival Card (SGAC) within three (3) days prior to arriving in Singapore. The service is free of charge on the official ICA portal.",
            officialSource = "Official ICA SG Arrival Card Portal",
            officialUrl = "https://eservices.ica.gov.sg/sgarrivalcard"
        ),
        DocumentGuide(
            title = "Visa & Entry Guidance",
            description = "Many nationalities enjoy visa-free entry for tourism up to 30 or 90 days. Always confirm specific visa requirements directly on the official ICA website. Roamly provides travel preparation tools but does not issue or guarantee visas.",
            officialSource = "Ministry of Foreign Affairs Singapore (MFA)",
            officialUrl = "https://www.mfa.gov.sg"
        )
    )

    val belongingDepartureItems: List<String> = listOf(
        "Passport & Travel Documents",
        "Wallet & Credit Cards",
        "Smartphone & Charger",
        "Camera & Accessories",
        "House / Hotel Room Keys",
        "Prescription Glasses / Sunglasses",
        "Insulated Water Bottle"
    )

    val sampleTravellers: List<TravellerRecord> = listOf(
        TravellerRecord(
            id = "trav_alex",
            name = "Alex Chen",
            assignedTripId = "trip_singapore_2026",
            assignedTripName = "Singapore Explorer",
            preparationStatus = "Ready",
            packedItemsCount = 14,
            totalItemsCount = 23,
            visitedActivitiesCount = 2,
            totalActivitiesCount = 3,
            contactPhone = "+65 9123 4567"
        ),
        TravellerRecord(
            id = "trav_maya",
            name = "Maya Lin",
            assignedTripId = "trip_singapore_2026",
            assignedTripName = "Singapore Explorer",
            preparationStatus = "Ready",
            packedItemsCount = 14,
            totalItemsCount = 23,
            visitedActivitiesCount = 2,
            totalActivitiesCount = 3,
            contactPhone = "+65 9234 5678"
        ),
        TravellerRecord(
            id = "trav_david",
            name = "David Tan",
            assignedTripId = "trip_singapore_2026",
            assignedTripName = "Singapore Explorer",
            preparationStatus = "Preparation Incomplete",
            packedItemsCount = 6,
            totalItemsCount = 23,
            visitedActivitiesCount = 0,
            totalActivitiesCount = 3,
            contactPhone = "+65 9345 6789"
        ),
        TravellerRecord(
            id = "trav_sarah",
            name = "Sarah Jenkins",
            assignedTripId = "trip_singapore_2026",
            assignedTripName = "Singapore Explorer",
            preparationStatus = "Documents Pending",
            packedItemsCount = 8,
            totalItemsCount = 23,
            visitedActivitiesCount = 0,
            totalActivitiesCount = 3,
            contactPhone = "+1 415 555 0192"
        ),
        TravellerRecord(
            id = "trav_kenji",
            name = "Kenji Sato",
            assignedTripId = "trip_penang_2026",
            assignedTripName = "Penang Culinary & Shophouses",
            preparationStatus = "Ready",
            packedItemsCount = 18,
            totalItemsCount = 20,
            visitedActivitiesCount = 0,
            totalActivitiesCount = 0,
            contactPhone = "+81 90 1234 5678"
        )
    )
}

