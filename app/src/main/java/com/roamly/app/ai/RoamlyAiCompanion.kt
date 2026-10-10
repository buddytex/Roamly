package com.roamly.app.ai

import com.roamly.app.model.ChatMessage
import com.roamly.app.model.ChatSender
import com.roamly.app.model.Trip
import java.util.UUID

object RoamlyAiCompanion {

    fun generateResponse(userQuery: String, trip: Trip?): ChatMessage {
        val q = userQuery.lowercase().trim()
        val text: String
        val promptChips: MutableList<String> = mutableListOf()

        when {
            q.contains("marina bay") || q.contains("near marina") -> {
                text = """
                    **Exploring Near Marina Bay:**
                    Here is a curated itinerary around the Bay:
                    
                    • **Gardens by the Bay & Cloud Forest:** Just a 7-minute stroll across the Lions Bridge from Marina Bay Sands. Walk inside the 35m misty waterfall mountain and the Flower Dome.
                    • **Helix Bridge:** Beautiful DNA-inspired pedestrian link leading directly toward the ArtScience Museum.
                    • **Lau Pa Sat & Satay Street:** 12 minutes walk into the Financial District. Open-air charcoal grilled satay skewers start at 7:00 PM nightly.
                    • **Merlion Park:** Direct view across the water basin — best visited at twilight when the evening breeze picks up.
                    
                    💡 *Practical Tip:* The free Garden Rhapsody light show at the Supertree Grove runs daily at 7:45 PM and 8:45 PM.
                """.trimIndent()
                promptChips.addAll(listOf("What are the precautions for Marina Bay?", "What can I do with three hours free?"))
            }

            q.contains("three hours") || q.contains("3 hours") || q.contains("free time") -> {
                text = """
                    **Curated 3-Hour Singapore Experiences:**
                    Depending on where you are:
                    
                    1. **River & Culture (Civic District):**
                       • Start at National Gallery Singapore (1 hr).
                       • Stroll through historic Anderson Bridge to Clarke Quay (30 mins).
                       • Take a 40-minute Singapore River Bumboat cruise at sunset.
                    
                    2. **Green Serenity (Botanic Gardens):**
                       • Explore the National Orchid Garden VIP pavilions (1 hr).
                       • Enjoy iced lemongrass tea at The Halia café (45 mins).
                       • Relax around Swan Lake and the ancient heritage trees.
                    
                    3. **Architectural Wonder (Jewel Changi):**
                       • Walk the lush Shiseido Forest Valley.
                       • Catch the 40m Rain Vortex cascade from Level 2.
                       • Sample warm pandan chiffon cake and kaya toast.
                """.trimIndent()
                promptChips.addAll(listOf("What should I explore near Marina Bay?", "What should I pack for Singapore?"))
            }

            q.contains("history") || q.contains("historical") || q.contains("simple language") -> {
                text = """
                    **The Story of Singapore in Simple Words:**
                    
                    • **From Fishing Settlement to Lion City:** Originally called *Temasek* ('Sea Town'), legend says Prince Sang Nila Utama spotted what he thought was a lion and named the island *Singapura* ('Lion City').
                    • **Trading Port (1819):** Sir Stamford Raffles established Singapore as a free trade port due to its strategic position at the crossroads of the Malacca Strait.
                    • **Melting Pot of Cultures:** Chinese, Malay, Indian, and Eurasian merchants settled in distinct quarters — today’s Chinatown, Kampong Glam, and Little India.
                    • **Modern Transformation:** Since independence in 1965 under Lee Kuan Yew, Singapore developed into a global green financial hub, consciously designing itself as a "City in Nature."
                """.trimIndent()
                promptChips.addAll(listOf("What should I explore near Marina Bay?", "Explain Kampong Glam history"))
            }

            q.contains("planned for today") || q.contains("today's plan") || q.contains("itinerary today") -> {
                val day = trip?.days?.firstOrNull()
                if (day != null) {
                    val destList = day.destinations.joinToString("\n") { "• **${it.name}** (${it.timeSlot}) — ${it.shortDescription}" }
                    text = """
                        **Today's Itinerary — Day ${day.dayNumber}: ${day.title}**
                        
                        $destList
                        
                        💡 *Status:* You have ${day.destinations.count { it.isCompleted }} of ${day.destinations.size} destinations completed.
                    """.trimIndent()
                } else {
                    text = "You currently have 5 days planned for Singapore, starting with Marina Bay Sands and Gardens by the Bay!"
                }
                promptChips.addAll(listOf("What precautions should I know?", "What should I pack for my trip?"))
            }

            q.contains("pack") || q.contains("packing") || q.contains("what to bring") -> {
                text = """
                    **Packing Advice for Singapore's Tropical Climate:**
                    
                    • **Clothing:** Lightweight breathable linen or cotton. Daily temperatures hover between 28°C–32°C with high humidity.
                    • **Layering:** Malls, MRT trains, and attractions like Cloud Forest are heavily air-conditioned (~22°C); bring a light cardigan or wrap.
                    • **Rain Gear:** Short, heavy afternoon rain showers are common; carry a compact umbrella.
                    • **Footwear:** Cushioned walking sneakers — Singapore is exceptionally walkable.
                    • **Power & Plugs:** Type-G UK 3-pin plug (230V, 50Hz).
                    • **Etiquette:** Modest attire covering shoulders and knees for temple visits.
                """.trimIndent()
                promptChips.addAll(listOf("Check my packing list progress", "What are the rules and precautions?"))
            }

            q.contains("precaution") || q.contains("rule") || q.contains("law") || q.contains("fine") -> {
                text = """
                    **Key Precautions & Local Guidelines in Singapore:**
                    
                    • **MRT & Public Transit:** Eating, drinking (even plain water), and chewing gum are strictly prohibited inside train stations and cabins.
                    • **Temple Visits:** Remove footwear before entering Hindu and Buddhist shrines, and dress modestly (shoulders & knees covered).
                    • **Smoking:** Strictly restricted to designated yellow smoking boxes; prohibited in all public parks and indoor venues.
                    • **Tipping Culture:** Tipping is generally not expected as restaurants automatically add a 10% service charge and GST.
                    • **Littering:** Heavy fines apply for littering or jaywalking.
                """.trimIndent()
                promptChips.addAll(listOf("What should I pack for Singapore?", "What can I do with three hours free?"))
            }

            q.contains("reorganise") || q.contains("reorganize") || q.contains("change plan") || q.contains("rain") -> {
                text = """
                    **Smart Itinerary Adjustment Suggestions:**
                    
                    • If it rains in the afternoon: Swap outdoor walks (Supertree Grove / Haji Lane) for indoor marvels like **Cloud Forest & Flower Dome**, **National Gallery**, or **Jewel Changi**.
                    • If you finish early: Add a relaxing twilight stroll along **Marina Barrage** or visit **Clarke Quay** for dinner by the river.
                    • All destinations in your Roamly itinerary can be visited in any order. Tap any stop to mark it complete!
                """.trimIndent()
                promptChips.addAll(listOf("What activities are planned for today?", "What should I explore near Marina Bay?"))
            }

            q.contains("chinatown") || q.contains("food") || q.contains("hawker") -> {
                text = """
                    **Food & Hawker Culture Guide:**
                    
                    • **Lau Pa Sat:** Famous for evening Satay Street (Stalls 7 & 8) with peanut sauce and cucumber.
                    • **Chinatown Complex:** Home to iconic Liao Fan Hawker Chan chicken rice and traditional dessert stalls.
                    • **Maxwell Food Centre:** Try Tian Tian Hainanese Chicken Rice or Zhen Zhen Porridge.
                    • **Local Culture:** Look for packet tissues on tables ('Choping') — this is how locals reserve spots while ordering!
                """.trimIndent()
                promptChips.addAll(listOf("What should I explore near Marina Bay?", "What precautions should I know?"))
            }

            else -> {
                text = """
                    I have noted your question regarding "$userQuery".
                    
                    As your Singapore travel companion, I can help you with:
                    • Itinerary recommendations for Marina Bay, Chinatown, Sentosa, and Botanic Gardens.
                    • 3-hour free time schedules.
                    • Packing tips for Singapore's tropical climate.
                    • Local customs, temple dress codes, and transit etiquette.
                    • Reminders for essential belongings before you leave any stop.
                    
                    Feel free to tap one of the suggested questions below!
                """.trimIndent()
                promptChips.addAll(
                    listOf(
                        "What should I explore near Marina Bay?",
                        "What can I do with three hours free?",
                        "What should I pack for Singapore?",
                        "What precautions should I know about this activity?"
                    )
                )
            }
        }

        return ChatMessage(
            id = "ai_" + UUID.randomUUID().toString().take(8),
            sender = ChatSender.ROAMLY_AI,
            text = text,
            timestamp = System.currentTimeMillis(),
            promptChips = promptChips
        )
    }
}
