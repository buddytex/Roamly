package com.railguard.mobile.tripmate.ui.tourist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.railguard.mobile.tripmate.ai.TripChatMessage
import com.railguard.mobile.tripmate.ai.TripMateAiAssistant
import com.railguard.mobile.tripmate.models.Trip

@Composable
fun AiAssistantScreen(
    trip: Trip?,
    aiAssistant: TripMateAiAssistant
) {
    val currentDest = trip?.destinations?.getOrNull(trip.currentDestinationIndex)
    var userInput by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            aiAssistant.generateWelcomeMessage(trip, currentDest)
        )
    }

    val quickChips = listOf(
        "What should I look for here?",
        "Explain the history of this place",
        "What precautions should I take?",
        "What is my next destination?",
        "Have I completed my packing checklist?"
    )

    fun handleSend(query: String) {
        if (query.isBlank()) return
        val userMsg = TripChatMessage(
            sender = "You",
            text = query.trim(),
            isFromAi = false
        )
        messages.add(userMsg)
        userInput = ""

        val aiResponse = aiAssistant.answerQuery(query, trip, currentDest)
        messages.add(aiResponse)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // AI Header Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2563EB).copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF2563EB),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("TripMate AI Travel Guide", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                    Text(
                        text = "Context: ${currentDest?.name ?: trip?.title ?: "TripMate"}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = false
        ) {
            items(messages) { msg ->
                val isUser = !msg.isFromAi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isUser) Color(0xFF2563EB) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 14.dp
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (!isUser) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = msg.sender,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFF16A34A).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Verified Trip Data",
                                            fontSize = 9.sp,
                                            color = Color(0xFF16A34A),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = msg.timestamp,
                                fontSize = 10.sp,
                                color = if (isUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        // Quick Suggestion Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickChips) { chip ->
                SuggestionChip(
                    onClick = { handleSend(chip) },
                    label = { Text(chip, fontSize = 11.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }

        // Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                placeholder = { Text("Ask about history, sights, stops...", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { handleSend(userInput) },
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFF2563EB), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}
