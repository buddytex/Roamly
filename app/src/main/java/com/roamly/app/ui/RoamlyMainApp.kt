package com.roamly.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.ui.screens.AiCompanionScreen
import com.roamly.app.ui.screens.ChecklistsScreen
import com.roamly.app.ui.screens.HomeScreen
import com.roamly.app.ui.screens.MyTripsScreen
import com.roamly.app.ui.screens.ProfileScreen
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary

enum class RoamlyTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    TRIPS("My Trips", Icons.Default.Map),
    AI("AI Companion", Icons.Default.AutoAwesome),
    CHECKLISTS("Checklists", Icons.Default.Checklist),
    PROFILE("Profile", Icons.Default.Person)
}

@Composable
fun RoamlyMainApp(
    repository: RoamlyRepository
) {
    var selectedTab by remember { mutableStateOf(RoamlyTab.HOME) }
    var aiInitialPrompt by remember { mutableStateOf<String?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = RoamlySurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .shadow(12.dp)
                    .border(1.dp, RoamlyBorder)
            ) {
                RoamlyTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTab = tab
                            if (tab != RoamlyTab.AI) {
                                aiInitialPrompt = null
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RoamlyPrimary,
                            selectedTextColor = RoamlyPrimary,
                            unselectedIconColor = RoamlyTextSecondary,
                            unselectedTextColor = RoamlyTextSecondary,
                            indicatorColor = RoamlyPrimaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RoamlyBackground)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                RoamlyTab.HOME -> HomeScreen(
                    repository = repository,
                    onNavigateToTrips = { selectedTab = RoamlyTab.TRIPS },
                    onNavigateToAi = { prompt ->
                        aiInitialPrompt = prompt
                        selectedTab = RoamlyTab.AI
                    },
                    onNavigateToChecklists = { selectedTab = RoamlyTab.CHECKLISTS }
                )
                RoamlyTab.TRIPS -> MyTripsScreen(
                    repository = repository
                )
                RoamlyTab.AI -> AiCompanionScreen(
                    repository = repository,
                    initialPrompt = aiInitialPrompt
                )
                RoamlyTab.CHECKLISTS -> ChecklistsScreen(
                    repository = repository
                )
                RoamlyTab.PROFILE -> ProfileScreen(
                    repository = repository
                )
            }
        }
    }
}
