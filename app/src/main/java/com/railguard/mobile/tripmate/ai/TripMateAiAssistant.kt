package com.railguard.mobile.tripmate.ai

import com.railguard.mobile.tripmate.models.*
import java.text.SimpleDateFormat
import java.util.*

data class TripChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: String = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
    val isFromAi: Boolean,
    val isDeterministic: Boolean = true
)

class TripMateAiAssistant {

    fun generateWelcomeMessage(trip: Trip?, currentDest: Destination?): TripChatMessage {
        val destName = currentDest?.name ?: "your upcoming tour"
        val text = if (trip != null) {
            "Hello! I am your TripMate Travel Companion for \"${trip.title}\".\n" +
                    "Currently, you are at ${destName}. You can ask me about planned activities, safety precautions, historical highlights, or your next stop!"
        } else {
            "Welcome to TripMate! I can answer questions about your itinerary, packing lists, and destinations once a trip is active."
        }
        return TripChatMessage(
            sender = "TripMate AI",
            text = text,
            isFromAi = true,
            isDeterministic = true
        )
    }

    fun answerQuery(query: String, trip: Trip?, currentDest: Destination?): TripChatMessage {
        val q = query.trim().lowercase(Locale.ROOT)

        if (trip == null) {
            return TripChatMessage(
                sender = "TripMate AI",
                text = "There is currently no active trip loaded. Please select or create a trip to explore details.",
                isFromAi = true,
                isDeterministic = true
            )
        }

        val dest = currentDest ?: trip.destinations.getOrNull(trip.currentDestinationIndex)

        val responseText: String = when {
            // Next destination query
            q.contains("next") && (q.contains("destination") || q.contains("stop") || q.contains("where")) -> {
                val nextIdx = trip.currentDestinationIndex + 1
                if (nextIdx < trip.destinations.size) {
                    val next = trip.destinations[nextIdx]
                    "Your next stop is **${next.name}**.\n" +
                            "• Planned Time: ${next.plannedTime}\n" +
                            "• Meeting Point: ${next.meetingPoint}\n" +
                            "• Highlights: ${next.shortDescription}"
                } else {
                    "You are currently at the final stop of this itinerary (**${dest?.name ?: "Current"}**). Upon completion, the expedition concludes!"
                }
            }

            // Planned activities
            q.contains("activit") || q.contains("schedule") || q.contains("plan") || q.contains("what to do") -> {
                if (dest != null && dest.activities.isNotEmpty()) {
                    val acts = dest.activities.joinToString("\n") { act ->
                        val check = if (act.isCompleted) "✓ [Done]" else "○ [Pending]"
                        "$check **${act.time}** - ${act.title}: ${act.description}"
                    }
                    "Planned activities at **${dest.name}**:\n$acts"
                } else {
                    "No specific sub-activities are scheduled for this stop. You are free to explore following guide instructions."
                }
            }

            // Precautions and safety
            q.contains("precaut") || q.contains("safe") || q.contains("danger") || q.contains("warning") || q.contains("careful") -> {
                if (dest != null && dest.precautions.isNotEmpty()) {
                    val precs = dest.precautions.joinToString("\n") { "• $it" }
                    "Important precautions for **${dest.name}**:\n$precs"
                } else {
                    "Standard travel safety applies: stay hydrated, keep belongings secured, and remain near guide checkpoints."
                }
            }

            // What to see / look for / top sights
            q.contains("look for") || q.contains("see") || q.contains("sight") || q.contains("attraction") || q.contains("highlight") -> {
                if (dest != null && dest.topSights.isNotEmpty()) {
                    val sights = dest.topSights.joinToString("\n") { "★ $it" }
                    val order = if (dest.suggestedExploration.isNotEmpty()) {
                        "\n\nSuggested exploration order:\n" + dest.suggestedExploration.joinToString("\n")
                    } else ""
                    "Top sights to look for at **${dest.name}**:\n$sights$order"
                } else {
                    "Explore the immediate surrounding area and enjoy the panoramic viewpoints."
                }
            }

            // History / significance / why visit
            q.contains("histor") || q.contains("why") || q.contains("signific") || q.contains("story") || q.contains("tell me about") -> {
                if (dest != null) {
                    "**${dest.name} - Cultural & Historical Context:**\n\n" +
                            "${dest.fullOverview}\n\n" +
                            "**Why this destination matters:** ${dest.whyVisit}"
                } else {
                    "Historical details are available once a specific destination is selected."
                }
            }

            // Packing status
            q.contains("pack") || q.contains("checklist") -> {
                val total = trip.packingList.size
                val packed = trip.packingList.count { it.isPacked }
                val remaining = trip.packingList.filterNot { it.isPacked }
                if (remaining.isEmpty()) {
                    "Great news! Your packing checklist is **100% complete** ($packed of $total items packed)."
                } else {
                    val remainingItems = remaining.take(4).joinToString(", ") { it.title }
                    val extra = if (remaining.size > 4) " and ${remaining.size - 4} more" else ""
                    "You have packed $packed of $total items (${(packed * 100 / total.coerceAtLeast(1))}%%).\n" +
                            "Still pending: $remainingItems$extra."
                }
            }

            // Belongings check
            q.contains("belonging") || q.contains("wallet") || q.contains("phone") || q.contains("lost") || q.contains("left behind") -> {
                val unverified = trip.belongingsList.filterNot { it.isVerified }
                if (unverified.isEmpty()) {
                    "All your tracked belongings (${trip.belongingsList.size} items) are marked verified for this segment."
                } else {
                    "Reminder: Before transitioning between stops, always check your belongings: " +
                            trip.belongingsList.joinToString(", ") { it.name } +
                            ". Tap Departure Check to acknowledge."
                }
            }

            // Guide contact
            q.contains("guide") || q.contains("contact") || q.contains("call") || q.contains("phone number") -> {
                "Your lead tour guide is **${trip.guideName}** (${trip.agencyName}).\n" +
                        "Emergency & Coordination Phone: **${trip.guideContact}**.\n" +
                        "Meeting Point: **${trip.meetingPoint}**."
            }

            // Meeting point
            q.contains("meeting") || q.contains("meet") || q.contains("assemble") -> {
                if (dest != null) {
                    "The assembly point for **${dest.name}** is:\n📍 **${dest.meetingPoint}**\nPlanned Departure / Window: ${dest.plannedTime}"
                } else {
                    "General trip meeting point: ${trip.meetingPoint}"
                }
            }

            // Fallback: structured contextual summary
            else -> {
                "Here is what I know about your current stop (**${dest?.name ?: "Current Stop"}**):\n\n" +
                        "• **Overview**: ${dest?.shortDescription ?: "In progress"}\n" +
                        "• **Next Activity**: ${dest?.activities?.firstOrNull { !it.isCompleted }?.title ?: "Free exploration"}\n" +
                        "• **Meeting Point**: ${dest?.meetingPoint ?: trip.meetingPoint}\n\n" +
                        "You can ask me specifically: *\"What should I look for here?\"*, *\"Explain the history\"*, *\"What precautions?\"*, or *\"What is my next destination?\"*"
            }
        }

        return TripChatMessage(
            sender = "TripMate AI",
            text = responseText,
            isFromAi = true,
            isDeterministic = true
        )
    }
}
