package com.roamly.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.roamly.app.auth.model.AuthState
import com.roamly.app.auth.model.AuthUser
import com.roamly.app.auth.ui.AuthViewModel
import com.roamly.app.data.RoamlyRepository
import com.roamly.app.model.UserRole

import com.roamly.app.ui.screens.AiCompanionScreen
import com.roamly.app.ui.screens.ChecklistsScreen
import com.roamly.app.ui.screens.GuideDashboardScreen
import com.roamly.app.ui.screens.GuideManageTripScreen
import com.roamly.app.ui.screens.GuideTravellersScreen
import com.roamly.app.ui.screens.HomeScreen
import com.roamly.app.ui.screens.MyTripsScreen
import com.roamly.app.ui.screens.ProfileScreen
import com.roamly.app.ui.screens.RoleSelectionScreen

import com.roamly.app.ui.screens.auth.EmailVerificationNoticeScreen
import com.roamly.app.ui.screens.auth.GuideSignInScreen
import com.roamly.app.ui.screens.auth.GuideSignUpScreen
import com.roamly.app.ui.screens.auth.TravellerForgotPasswordScreen
import com.roamly.app.ui.screens.auth.TravellerSignInScreen
import com.roamly.app.ui.screens.auth.TravellerSignUpScreen

import com.roamly.app.ui.theme.RoamlyAccent
import com.roamly.app.ui.theme.RoamlyAccentContainer
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlySurfaceVariant
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary


enum class TravellerTab(
    val title: String,
    val icon: ImageVector
) {
    HOME("Trips", Icons.Default.Home),
    TRIPS("My Trips", Icons.Default.Map),
    HELP("Travel Help", Icons.Default.AutoAwesome),
    CHECKLIST("Checklist", Icons.Default.Checklist)
}


enum class GuideTab(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    TRIPS("My Trips", Icons.Default.Map),
    TRAVELLERS("Travellers", Icons.Default.Group),
    PROFILE("Profile", Icons.Default.Person)
}


private enum class AuthScreenStep {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD
}


@Composable
fun RoamlyMainApp(
    repository: RoamlyRepository,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current

    val authState by authViewModel.authState.collectAsState()
    val selectedPortal by authViewModel.selectedPortal.collectAsState()
    val formErrors by authViewModel.formErrors.collectAsState()

    var travellerAuthStep by remember {
        mutableStateOf(AuthScreenStep.SIGN_IN)
    }

    var guideAuthStep by remember {
        mutableStateOf(AuthScreenStep.SIGN_IN)
    }

    when (val state = authState) {

        // ---------------------------------------------------------
        // INITIALIZATION
        // ---------------------------------------------------------

        is AuthState.Initializing -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RoamlyBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = RoamlyPrimary,
                    strokeWidth = 3.dp
                )
            }
        }


        // ---------------------------------------------------------
        // ROLE SELECTION
        // ---------------------------------------------------------

        is AuthState.AwaitingRoleSelection -> {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    authViewModel.assignUserRole(role)
                }
            )
        }


        // ---------------------------------------------------------
        // EMAIL VERIFICATION
        // ---------------------------------------------------------

        is AuthState.AwaitingEmailVerification -> {
            EmailVerificationNoticeScreen(
                email = state.email,
                onBackToSignIn = {
                    authViewModel.signOut()

                    travellerAuthStep = AuthScreenStep.SIGN_IN
                    guideAuthStep = AuthScreenStep.SIGN_IN
                }
            )
        }


        // ---------------------------------------------------------
        // AUTHENTICATED USER ROUTING
        // ---------------------------------------------------------

        is AuthState.Authenticated -> {
            val user = state.user

            when (user.role) {

                // Guide accounts open GuideNavigationHost directly
                UserRole.GUIDE -> {
                    GuideNavigationHost(
                        repository = repository,
                        currentUser = user,
                        onSignOut = {
                            authViewModel.signOut()
                        }
                    )
                }

                // Traveller accounts open TravellerNavigationHost directly
                UserRole.TRAVELLER -> {
                    TravellerNavigationHost(
                        repository = repository,
                        currentUser = user,
                        onSignOut = {
                            authViewModel.signOut()
                        }
                    )
                }

                // Never silently route an unknown account to Traveller.
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(RoamlyBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Account role not recognised",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoamlyTextPrimary
                            )

                            Text(
                                text = "Roamly could not determine which portal belongs to your account. Sign out and sign in again. If the problem continues, your account profile may need to be corrected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RoamlyTextSecondary
                            )

                            Button(
                                onClick = {
                                    authViewModel.signOut()
                                }
                            ) {
                                Text("Return to Sign In")
                            }
                        }
                    }
                }
            }
        }


        // ---------------------------------------------------------
        // SIGNED OUT, AUTHENTICATION IN PROGRESS, OR ERROR
        // ---------------------------------------------------------

        is AuthState.SignedOut,
        is AuthState.SigningIn,
        is AuthState.SigningUp,
        is AuthState.SigningOut,
        is AuthState.Error -> {

            when (selectedPortal) {

                // -------------------------------------------------
                // SELECT A PORTAL
                // -------------------------------------------------

                null -> {
                    RoleSelectionScreen(
                        onRoleSelected = { role ->
                            authViewModel.selectPortal(role)

                            travellerAuthStep = AuthScreenStep.SIGN_IN
                            guideAuthStep = AuthScreenStep.SIGN_IN
                        },
                        onGoogleSignIn = {
                            authViewModel.signInWithGoogle(
                                context,
                                null
                            )
                        },
                        isLoading = authState is AuthState.SigningIn,
                        errorMessage = formErrors.generalError
                            ?: (authState as? AuthState.Error)?.message
                    )
                }


                // -------------------------------------------------
                // TRAVELLER AUTHENTICATION
                // -------------------------------------------------

                UserRole.TRAVELLER -> {

                    when (travellerAuthStep) {

                        AuthScreenStep.SIGN_IN -> {
                            TravellerSignInScreen(
                                authViewModel = authViewModel,
                                onNavigateToSignUp = {
                                    authViewModel.clearErrors()
                                    travellerAuthStep = AuthScreenStep.SIGN_UP
                                },
                                onNavigateToForgotPassword = {
                                    authViewModel.clearErrors()
                                    travellerAuthStep =
                                        AuthScreenStep.FORGOT_PASSWORD
                                },
                                onBackToRoleSelection = {
                                    authViewModel.clearPortal()
                                }
                            )
                        }


                        AuthScreenStep.SIGN_UP -> {
                            TravellerSignUpScreen(
                                authViewModel = authViewModel,
                                onNavigateToSignIn = {
                                    authViewModel.clearErrors()
                                    travellerAuthStep =
                                        AuthScreenStep.SIGN_IN
                                },
                                onBackToRoleSelection = {
                                    authViewModel.clearPortal()
                                }
                            )
                        }


                        AuthScreenStep.FORGOT_PASSWORD -> {
                            TravellerForgotPasswordScreen(
                                authViewModel = authViewModel,
                                onBackToSignIn = {
                                    authViewModel.resetPasswordState()
                                    travellerAuthStep =
                                        AuthScreenStep.SIGN_IN
                                }
                            )
                        }
                    }
                }


                // -------------------------------------------------
                // GUIDE AUTHENTICATION
                // -------------------------------------------------

                UserRole.GUIDE -> {

                    when (guideAuthStep) {

                        AuthScreenStep.SIGN_IN -> {
                            GuideSignInScreen(
                                authViewModel = authViewModel,
                                onNavigateToSignUp = {
                                    authViewModel.clearErrors()
                                    guideAuthStep = AuthScreenStep.SIGN_UP
                                },
                                onNavigateToForgotPassword = {
                                    authViewModel.clearErrors()
                                    guideAuthStep =
                                        AuthScreenStep.FORGOT_PASSWORD
                                },
                                onBackToRoleSelection = {
                                    authViewModel.clearPortal()
                                }
                            )
                        }


                        AuthScreenStep.SIGN_UP -> {
                            GuideSignUpScreen(
                                authViewModel = authViewModel,
                                onNavigateToSignIn = {
                                    authViewModel.clearErrors()
                                    guideAuthStep =
                                        AuthScreenStep.SIGN_IN
                                },
                                onBackToRoleSelection = {
                                    authViewModel.clearPortal()
                                }
                            )
                        }


                        AuthScreenStep.FORGOT_PASSWORD -> {
                            TravellerForgotPasswordScreen(
                                authViewModel = authViewModel,
                                onBackToSignIn = {
                                    authViewModel.resetPasswordState()
                                    guideAuthStep =
                                        AuthScreenStep.SIGN_IN
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


// =====================================================================
// TRAVELLER NAVIGATION
// =====================================================================

@Composable
private fun TravellerNavigationHost(
    repository: RoamlyRepository,
    currentUser: AuthUser? = null,
    onSignOut: () -> Unit
) {
    var selectedTab by remember {
        mutableStateOf(TravellerTab.HOME)
    }

    var showingProfile by remember {
        mutableStateOf(false)
    }

    var aiInitialPrompt by remember {
        mutableStateOf<String?>(null)
    }

    var openItineraryDirectly by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBarHeader(
                roleLabel = "TRAVELLER",
                onSwitchRole = onSignOut,
                onProfileClick = {
                    showingProfile = !showingProfile
                },
                isProfileActive = showingProfile
            )
        },

        bottomBar = {
            NavigationBar(
                containerColor = RoamlySurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .shadow(12.dp)
                    .border(1.dp, RoamlyBorder)
            ) {
                TravellerTab.values().forEach { tab ->

                    val isSelected =
                        !showingProfile && selectedTab == tab

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            showingProfile = false
                            selectedTab = tab
                            openItineraryDirectly = false

                            if (tab != TravellerTab.HELP) {
                                aiInitialPrompt = null
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                                fontSize = 12.sp
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

            if (showingProfile) {

                ProfileScreen(
                    repository = repository,
                    currentUser = currentUser,
                    onSwitchRoleRequested = onSignOut,
                    onSignOutRequested = onSignOut
                )

            } else {

                when (selectedTab) {

                    TravellerTab.HOME -> {
                        HomeScreen(
                            repository = repository,
                            currentUser = currentUser,
                            onViewTravelPlan = {
                                openItineraryDirectly = true
                                selectedTab = TravellerTab.TRIPS
                            },
                            onNavigateToTrips = {
                                openItineraryDirectly = false
                                selectedTab = TravellerTab.TRIPS
                            },
                            onNavigateToAi = { prompt ->
                                aiInitialPrompt = prompt
                                selectedTab = TravellerTab.HELP
                            },
                            onNavigateToChecklists = {
                                selectedTab = TravellerTab.CHECKLIST
                            }
                        )
                    }


                    TravellerTab.TRIPS -> {
                        MyTripsScreen(
                            repository = repository,
                            openItineraryDirectly = openItineraryDirectly,
                            onNavigateToTripsLanding = {
                                selectedTab = TravellerTab.HOME
                            }
                        )
                    }


                    TravellerTab.HELP -> {
                        AiCompanionScreen(
                            repository = repository,
                            initialPrompt = aiInitialPrompt
                        )
                    }


                    TravellerTab.CHECKLIST -> {
                        ChecklistsScreen(
                            repository = repository
                        )
                    }
                }
            }
        }
    }
}


// =====================================================================
// GUIDE NAVIGATION
// =====================================================================

@Composable
private fun GuideNavigationHost(
    repository: RoamlyRepository,
    currentUser: AuthUser? = null,
    onSignOut: () -> Unit
) {
    var selectedTab by remember {
        mutableStateOf(GuideTab.DASHBOARD)
    }

    var managingTripId by remember {
        mutableStateOf<String?>(null)
    }

    // Open the selected trip management screen.
    if (managingTripId != null) {

        GuideManageTripScreen(
            tripId = managingTripId!!,
            repository = repository,
            onBack = {
                managingTripId = null
            }
        )

        return
    }

    Scaffold(
        topBar = {
            TopAppBarHeader(
                roleLabel = "GUIDE",
                onSwitchRole = onSignOut,
                onProfileClick = {
                    selectedTab = GuideTab.PROFILE
                },
                isProfileActive = selectedTab == GuideTab.PROFILE
            )
        },

        bottomBar = {
            NavigationBar(
                containerColor = RoamlySurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .shadow(12.dp)
                    .border(1.dp, RoamlyBorder)
            ) {
                GuideTab.values().forEach { tab ->

                    val isSelected = selectedTab == tab

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            selectedTab = tab
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                },
                                fontSize = 12.sp
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

                GuideTab.DASHBOARD -> {
                    GuideDashboardScreen(
                        repository = repository,
                        onManageTrip = { trip ->
                            managingTripId = trip.id
                        },
                        onSwitchRole = onSignOut
                    )
                }


                GuideTab.TRIPS -> {
                    MyTripsScreen(
                        repository = repository
                    )
                }


                GuideTab.TRAVELLERS -> {
                    GuideTravellersScreen(
                        repository = repository
                    )
                }


                GuideTab.PROFILE -> {
                    ProfileScreen(
                        repository = repository,
                        currentUser = currentUser,
                        onSwitchRoleRequested = onSignOut,
                        onSignOutRequested = onSignOut
                    )
                }
            }
        }
    }
}


// =====================================================================
// SHARED TOP APP BAR
// =====================================================================

@Composable
private fun TopAppBarHeader(
    roleLabel: String,
    onSwitchRole: () -> Unit,
    onProfileClick: () -> Unit,
    isProfileActive: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RoamlySurface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 12.dp
                )
                .border(
                    width = 0.5.dp,
                    color = RoamlyBorder
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

        // App title and role badge.
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RoamlyPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = RoamlySurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Text(
                text = "Roamly",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RoamlyPrimary,
                letterSpacing = 0.5.sp
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (roleLabel == "GUIDE") {
                            RoamlyAccentContainer
                        } else {
                            RoamlyPrimaryContainer
                        }
                    )
                    .padding(
                        horizontal = 8.dp,
                        vertical = 3.dp
                    )
            ) {
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (roleLabel == "GUIDE") {
                        RoamlyAccent
                    } else {
                        RoamlyPrimary
                    },
                    fontSize = 10.sp
                )
            }
        }


        // Switch-role and profile actions.
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(RoamlySurfaceVariant)
                    .border(
                        width = 1.dp,
                        color = RoamlyBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = onSwitchRole)
                    .padding(
                        horizontal = 8.dp,
                        vertical = 6.dp
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Switch Role",
                        tint = RoamlyPrimary,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(
                        modifier = Modifier.width(4.dp)
                    )

                    Text(
                        text = "Switch Role",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = RoamlyPrimary
                    )
                }
            }

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(38.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isProfileActive) {
                                RoamlyPrimary
                            } else {
                                RoamlySurfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        tint = if (isProfileActive) {
                            RoamlySurface
                        } else {
                            RoamlyTextPrimary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
}