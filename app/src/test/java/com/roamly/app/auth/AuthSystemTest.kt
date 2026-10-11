package com.roamly.app.auth

import com.roamly.app.auth.data.AuthRepository
import com.roamly.app.auth.model.ApprovalStatus
import com.roamly.app.auth.model.AuthResult
import com.roamly.app.auth.model.AuthState
import com.roamly.app.auth.model.AuthUser
import com.roamly.app.auth.ui.AuthViewModel
import com.roamly.app.data.SampleData
import com.roamly.app.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import com.roamly.app.auth.network.SupabaseNetworkClient

import com.roamly.app.auth.network.ProfileDto
import com.roamly.app.auth.network.UserDto
import com.roamly.app.auth.data.SupabaseAuthRepository

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    val registeredUsers = mutableMapOf<String, AuthUser>()

    var shouldFailSignIn = false
    var signInErrorMessage = "Invalid credentials"
    var isEmailConfirmed = true
    var signInCallCount = 0
    var signUpCallCount = 0
    var delayDurationMs = 0L
    var cachedSessionUser: AuthUser? = null

    fun adminApproveGuide(email: String) {
        val existing = registeredUsers[email]
        if (existing != null) {
            val updated = existing.copy(approvalStatus = ApprovalStatus.APPROVED)
            registeredUsers[email] = updated
            if (_currentUser.value?.email == email) {
                _currentUser.value = updated
                _authState.value = AuthState.Authenticated(updated)
            }
        }
    }

    override suspend fun restoreSession() {
        val cached = cachedSessionUser
        if (cached != null) {
            val persisted = registeredUsers[cached.email] ?: cached
            _currentUser.value = persisted
            _authState.value = if (persisted.role == null) {
                AuthState.AwaitingRoleSelection(persisted)
            } else {
                AuthState.Authenticated(persisted)
            }
        } else {
            _currentUser.value = null
            _authState.value = AuthState.SignedOut
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
        intendedRole: UserRole
    ): AuthResult<AuthUser> {
        signInCallCount++
        if (delayDurationMs > 0) kotlinx.coroutines.delay(delayDurationMs)
        if (shouldFailSignIn) {
            _authState.value = AuthState.Error(signInErrorMessage)
            return AuthResult.Failure(signInErrorMessage)
        }

        val persisted = registeredUsers[email]
        val effectiveRole: UserRole? = if (persisted != null) persisted.role else intendedRole
        val user = AuthUser(
            id = persisted?.id ?: "test-user-id",
            email = email,
            fullName = persisted?.fullName ?: "Test User",
            role = effectiveRole,
            approvalStatus = ApprovalStatus.APPROVED,
            isEmailConfirmed = isEmailConfirmed
        )

        cachedSessionUser = user
        _currentUser.value = user
        _authState.value = if (!isEmailConfirmed) {
            AuthState.AwaitingEmailVerification(email)
        } else if (effectiveRole == null) {
            AuthState.AwaitingRoleSelection(user)
        } else {
            AuthState.Authenticated(user)
        }

        return AuthResult.Success(user)
    }

    override suspend fun signUpWithEmail(
        fullName: String,
        email: String,
        password: String,
        role: UserRole
    ): AuthResult<AuthUser> {
        signUpCallCount++
        if (delayDurationMs > 0) kotlinx.coroutines.delay(delayDurationMs)
        val user = AuthUser(
            id = "new-user-id",
            email = email,
            fullName = fullName,
            role = role,
            approvalStatus = ApprovalStatus.APPROVED,
            isEmailConfirmed = isEmailConfirmed
        )
        registeredUsers[email] = user
        // Crucial: Signup creates account/profile, but does NOT log the user in
        // or set auth state to Authenticated or persist session!
        _currentUser.value = null
        _authState.value = AuthState.SignedOut
        return AuthResult.Success(user)
    }

    override suspend fun signInWithGoogle(idToken: String, intendedRole: UserRole?): AuthResult<AuthUser> {
        if (shouldFailSignIn) {
            _authState.value = AuthState.Error(signInErrorMessage)
            return AuthResult.Failure(signInErrorMessage)
        }
        val user = AuthUser(
            id = "google-user-id",
            email = "google@test.com",
            fullName = "Google User",
            role = intendedRole,
            approvalStatus = ApprovalStatus.APPROVED,
            photoUrl = "https://example.com/photo.jpg"
        )
        cachedSessionUser = user
        _currentUser.value = user
        _authState.value = when {
            intendedRole == null -> AuthState.AwaitingRoleSelection(user)
            else -> AuthState.Authenticated(user)
        }
        return AuthResult.Success(user)
    }

    override fun assignUserRole(role: UserRole) {
        val user = _currentUser.value
        val updated = user?.copy(role = role, approvalStatus = ApprovalStatus.APPROVED) ?: AuthUser(
            id = "test-id",
            email = "user@test.com",
            fullName = "User",
            role = role,
            approvalStatus = ApprovalStatus.APPROVED
        )
        cachedSessionUser = updated
        _currentUser.value = updated
        _authState.value = AuthState.Authenticated(updated)
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        return AuthResult.Success(Unit)
    }

    override suspend fun signInAsDemoUser(role: UserRole): AuthResult<AuthUser> {
        val user = AuthUser(
            id = "demo-id",
            email = "demo@roamly.app",
            fullName = "Demo User",
            role = role,
            approvalStatus = ApprovalStatus.APPROVED,
            isEmailConfirmed = true
        )
        cachedSessionUser = user
        _currentUser.value = user
        _authState.value = AuthState.Authenticated(user)
        return AuthResult.Success(user)
    }

    override suspend fun signOut() {
        cachedSessionUser = null
        _currentUser.value = null
        _authState.value = AuthState.SignedOut
    }

    override fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.SignedOut
        }
    }

    fun setExplicitAuthState(state: AuthState, user: AuthUser? = null) {
        _currentUser.value = user
        _authState.value = state
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeAuthRepository
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeAuthRepository()
        viewModel = AuthViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testFormValidationLogic() {
        // Email Validation
        assertNotNull(viewModel.validateEmail(""))
        assertNotNull(viewModel.validateEmail("not-an-email"))
        assertNotNull(viewModel.validateEmail("missing@domain"))
        assertNull(viewModel.validateEmail("traveller@example.com"))

        // Password Validation
        assertNotNull(viewModel.validatePassword(""))
        assertNotNull(viewModel.validatePassword("12345"))
        assertNull(viewModel.validatePassword("123456"))

        // Full Name Validation
        assertNotNull(viewModel.validateFullName("  "))
        assertNull(viewModel.validateFullName("Eleanor Vance"))
    }

    @Test
    fun testPasswordMismatchThrowsError() = runTest {
        viewModel.signUp(
            fullName = "John Doe",
            email = "john@example.com",
            password = "password123",
            confirmPassword = "differentPassword",
            role = UserRole.TRAVELLER
        )

        val errors = viewModel.formErrors.value
        assertTrue("Should have errors", errors.hasErrors)
        assertEquals("Passwords do not match.", errors.confirmPasswordError)
    }

    @Test
    fun testTravellerSignInSuccess() = runTest {
        viewModel.signIn("traveller@example.com", "secure123", UserRole.TRAVELLER)
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue("Should be Authenticated", state is AuthState.Authenticated)
        val user = (state as AuthState.Authenticated).user
        assertEquals(UserRole.TRAVELLER, user.role)
        assertEquals(ApprovalStatus.APPROVED, user.approvalStatus)
    }

    @Test
    fun testGuideSignUpCreatesAccountWithoutLoggingIn() = runTest {
        var navigatedToSignIn = false
        viewModel.signUp(
            fullName = "Marcus Chen",
            email = "marcus@guide.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.GUIDE,
            onSuccess = { navigatedToSignIn = true }
        )
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertEquals("Signup must NOT automatically authenticate the user", AuthState.SignedOut, state)
        assertTrue("Must trigger navigation to login screen", navigatedToSignIn)
        assertNotNull("Must show clear account created success message", viewModel.accountCreatedSuccess.value)
        assertEquals("marcus@guide.com", viewModel.lastRegisteredEmail.value)

        // Profile was registered with role GUIDE and approved status
        val registered = fakeRepository.registeredUsers["marcus@guide.com"]
        assertNotNull(registered)
        assertEquals(UserRole.GUIDE, registered?.role)
        assertEquals(ApprovalStatus.APPROVED, registered?.approvalStatus)

        // User must explicitly sign in to enter the Guide portal
        viewModel.signIn("marcus@guide.com", "password123", UserRole.GUIDE)
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.Authenticated)
        val user = (viewModel.authState.value as AuthState.Authenticated).user
        assertEquals(UserRole.GUIDE, user.role)
        assertEquals(ApprovalStatus.APPROVED, user.approvalStatus)
    }

    @Test
    fun testFailedAuthenticationDisplaysErrorMessage() = runTest {
        fakeRepository.shouldFailSignIn = true
        fakeRepository.signInErrorMessage = "Invalid email or password."

        viewModel.signIn("wrong@example.com", "wrongpass", UserRole.TRAVELLER)
        advanceUntilIdle()

        val errors = viewModel.formErrors.value
        assertEquals("Invalid email or password.", errors.generalError)
    }

    @Test
    fun testUnconfirmedEmailRoutesToVerificationNotice() = runTest {
        fakeRepository.isEmailConfirmed = false

        viewModel.signIn("unconfirmed@example.com", "password123", UserRole.TRAVELLER)
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue("Should await email verification", state is AuthState.AwaitingEmailVerification)
        assertEquals("unconfirmed@example.com", (state as AuthState.AwaitingEmailVerification).email)
    }

    @Test
    fun testSignOutClearsStateAndUser() = runTest {
        viewModel.signIn("user@example.com", "password123", UserRole.TRAVELLER)
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.Authenticated)

        viewModel.signOut()
        advanceUntilIdle()

        assertTrue(viewModel.authState.value is AuthState.SignedOut)
        assertNull(viewModel.currentUser.value)
    }

    @Test
    fun testPasswordRecoveryNeverLeakingExistence() = runTest {
        viewModel.sendPasswordReset("random@domain.com")
        advanceUntilIdle()

        assertTrue(viewModel.passwordResetSent.value)
    }

    @Test
    fun testGoogleSignInWithoutRolePromptsRoleSelection() = runTest {
        val result = fakeRepository.signInWithGoogle("valid-token", null)
        assertTrue(result is AuthResult.Success)

        val state = fakeRepository.authState.value
        assertTrue("Must prompt role selection if no role selected", state is AuthState.AwaitingRoleSelection)
        val user = (state as AuthState.AwaitingRoleSelection).user
        assertEquals("google@test.com", user.email)
        assertNull(user.role)
    }

    @Test
    fun testAssignRoleAfterGoogleSignInAuthenticatesUser() = runTest {
        fakeRepository.signInWithGoogle("valid-token", null)
        assertTrue(fakeRepository.authState.value is AuthState.AwaitingRoleSelection)

        viewModel.assignUserRole(UserRole.TRAVELLER)
        advanceUntilIdle()

        val state = fakeRepository.authState.value
        assertTrue("Must be Authenticated after role assigned", state is AuthState.Authenticated)
        val user = (state as AuthState.Authenticated).user
        assertEquals(UserRole.TRAVELLER, user.role)
        assertEquals(ApprovalStatus.APPROVED, user.approvalStatus)
    }

    @Test
    fun testGoogleSignInWithRoleAuthenticatesDirectly() = runTest {
        val result = fakeRepository.signInWithGoogle("valid-token", UserRole.TRAVELLER)
        assertTrue(result is AuthResult.Success)

        val state = fakeRepository.authState.value
        assertTrue("Direct Traveller auth", state is AuthState.Authenticated)
        assertEquals(UserRole.TRAVELLER, (state as AuthState.Authenticated).user.role)
    }

    @Test
    fun testPreservationOfSingaporeItinerary() {
        val trips = SampleData.sampleTrips
        assertEquals("Both Singapore and Tinkerspace demo trips should be available", 2, trips.size)
        val singapore = trips.first { it.id == "trip_singapore_2026" }
        assertEquals("Singapore", singapore.destination)
        assertEquals(5, singapore.days.size)
        val tinkerspace = trips.first { it.id == "trip_tinkerspace_demo" }
        assertEquals("Outside Tinkerspace", tinkerspace.title)
        assertEquals(1, tinkerspace.days.size)
    }

    @Test
    fun testInFlightGuardPreventsConcurrentSignInRequests() = runTest {
        fakeRepository.delayDurationMs = 100L

        // Trigger two rapid signIn calls
        viewModel.signIn("traveller@example.com", "password123", UserRole.TRAVELLER)
        viewModel.signIn("traveller@example.com", "password123", UserRole.TRAVELLER)

        advanceUntilIdle()

        // Guard must have ensured only 1 request was dispatched
        assertEquals("Only 1 sign-in request should be dispatched despite rapid taps", 1, fakeRepository.signInCallCount)
        assertTrue(viewModel.authState.value is AuthState.Authenticated)
    }

    @Test
    fun testInFlightGuardPreventsConcurrentSignUpRequests() = runTest {
        fakeRepository.delayDurationMs = 100L

        // Trigger two rapid signUp calls
        viewModel.signUp("Alex Chen", "alex@example.com", "password123", "password123", UserRole.TRAVELLER)
        viewModel.signUp("Alex Chen", "alex@example.com", "password123", "password123", UserRole.TRAVELLER)

        advanceUntilIdle()

        // Guard must have ensured only 1 request was dispatched
        assertEquals("Only 1 sign-up request should be dispatched despite rapid taps", 1, fakeRepository.signUpCallCount)
        assertEquals(AuthState.SignedOut, viewModel.authState.value)
        assertNotNull(viewModel.accountCreatedSuccess.value)
    }

    @Test
    fun testSupabaseNetworkClientRateLimitDetection() {
        val client = SupabaseNetworkClient(
            baseUrl = "https://example.supabase.co",
            anonKey = "anon-key",
            tokenProvider = { null }
        )

        // 1. General rate limit HTTP 429
        val jsonGeneral = "{\"code\": 429, \"msg\": \"Rate limit exceeded\"}"
        val resGeneral = Response.error<Unit>(
            429,
            jsonGeneral.toResponseBody("application/json".toMediaType())
        )
        val msgGeneral = client.parseErrorMessage(resGeneral)
        assertTrue(msgGeneral.contains("Too many requests", ignoreCase = true))

        // 2. Email delivery rate limit HTTP 429
        val jsonEmail = "{\"code\": 429, \"error_code\": \"over_email_send_rate_limit\", \"msg\": \"Email rate limit exceeded\"}"
        val resEmail = Response.error<Unit>(
            429,
            jsonEmail.toResponseBody("application/json".toMediaType())
        )
        val msgEmail = client.parseErrorMessage(resEmail)
        assertTrue(msgEmail.contains("Email delivery", ignoreCase = true))

        // 3. Rate limit with Retry-After header
        val headers = Headers.headersOf("Retry-After", "120")
        val rawResponse = okhttp3.Response.Builder()
            .code(429)
            .message("Too Many Requests")
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .request(okhttp3.Request.Builder().url("https://example.supabase.co/auth/v1/signup").build())
            .headers(headers)
            .build()
        val resRetry = Response.error<Unit>(
            jsonGeneral.toResponseBody("application/json".toMediaType()),
            rawResponse
        )
        val msgRetry = client.parseErrorMessage(resRetry)
        assertTrue(msgRetry.contains("wait 2 minutes", ignoreCase = true))
    }

    @Test
    fun testTravellerSignUpPersistsTravellerRole() = runTest {
        viewModel.signUp(
            fullName = "Alice Traveler",
            email = "alice@travel.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.TRAVELLER
        )
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertEquals("Traveller signup must stay SignedOut", AuthState.SignedOut, state)
        assertEquals(UserRole.TRAVELLER, fakeRepository.registeredUsers["alice@travel.com"]?.role)
        assertEquals(ApprovalStatus.APPROVED, fakeRepository.registeredUsers["alice@travel.com"]?.approvalStatus)
        assertNotNull(viewModel.accountCreatedSuccess.value)

        // Explicit login enters Traveller portal
        viewModel.signIn("alice@travel.com", "password123", UserRole.TRAVELLER)
        advanceUntilIdle()

        val loginState = viewModel.authState.value
        assertTrue("Explicit login must authenticate Traveller", loginState is AuthState.Authenticated)
        assertEquals(UserRole.TRAVELLER, (loginState as AuthState.Authenticated).user.role)
    }

    @Test
    fun testGuideSignUpPersistsGuideRole() = runTest {
        viewModel.signUp(
            fullName = "Bob Guide",
            email = "bob@guide.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.GUIDE
        )
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertEquals("Guide signup must stay SignedOut", AuthState.SignedOut, state)
        assertEquals(UserRole.GUIDE, fakeRepository.registeredUsers["bob@guide.com"]?.role)
        assertEquals(ApprovalStatus.APPROVED, fakeRepository.registeredUsers["bob@guide.com"]?.approvalStatus)
        assertNotNull(viewModel.accountCreatedSuccess.value)

        // Explicit login enters Guide portal
        viewModel.signIn("bob@guide.com", "password123", UserRole.GUIDE)
        advanceUntilIdle()

        val loginState = viewModel.authState.value
        assertTrue("Explicit login must authenticate Guide", loginState is AuthState.Authenticated)
        assertEquals(UserRole.GUIDE, (loginState as AuthState.Authenticated).user.role)
    }

    @Test
    fun testLoginRetrievesPersistedRoleRegardlessOfIntendedRole() = runTest {
        // Register Bob as GUIDE
        fakeRepository.signUpWithEmail("Bob Guide", "bob@guide.com", "secret", UserRole.GUIDE)

        // Bob attempts to sign in via Traveller login form (intendedRole = TRAVELLER)
        viewModel.signIn("bob@guide.com", "secret", UserRole.TRAVELLER)
        advanceUntilIdle()

        // Authoritative backend role must prevail: Bob is GUIDE, not TRAVELLER
        val state = viewModel.authState.value
        assertTrue("Backend role GUIDE must override client form intendedRole", state is AuthState.Authenticated)
        val user = (state as AuthState.Authenticated).user
        assertEquals(UserRole.GUIDE, user.role)
        assertEquals(ApprovalStatus.APPROVED, user.approvalStatus)
    }

    @Test
    fun testGuideAccountDirectlyOpensGuidePortal() = runTest {
        viewModel.signUp(
            fullName = "Dave Guide",
            email = "dave@guide.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.GUIDE
        )
        advanceUntilIdle()

        assertEquals("Guide signup must stay SignedOut", AuthState.SignedOut, viewModel.authState.value)

        // Explicit sign-in opens the Guide portal directly
        viewModel.signIn("dave@guide.com", "password123", UserRole.GUIDE)
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue("Guide account is directly Authenticated upon sign-in", state is AuthState.Authenticated)
        val user = (state as AuthState.Authenticated).user
        assertEquals(UserRole.GUIDE, user.role)
        assertEquals(ApprovalStatus.APPROVED, user.approvalStatus)
    }

    @Test
    fun testExistingGuideWithoutManualApprovalCanAccessPortal() = runTest {
        val existingGuidePending = ProfileDto(
            id = "g-legacy",
            fullName = "Legacy Guide",
            role = "guide",
            approvalStatus = "pending"
        )
        val mappedUser = SupabaseAuthRepository.mapDtoToAuthUser(null, existingGuidePending, null)
        assertEquals(UserRole.GUIDE, mappedUser.role)
        assertEquals("Existing pending guide must be recognized as APPROVED", ApprovalStatus.APPROVED, mappedUser.approvalStatus)
    }

    @Test
    fun testTravellerCannotEnterGuidePortalByModifyingUiState() = runTest {
        viewModel.signUp(
            fullName = "Tom Traveler",
            email = "tom@travel.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.TRAVELLER
        )
        advanceUntilIdle()
        assertEquals(AuthState.SignedOut, viewModel.authState.value)

        // Attempting to sign in with intendedRole = GUIDE when user is registered as TRAVELLER
        viewModel.signIn("tom@travel.com", "password123", UserRole.GUIDE)
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue("Must be authenticated", state is AuthState.Authenticated)
        val user = (state as AuthState.Authenticated).user
        assertEquals("Authoritative backend role TRAVELLER must prevail", UserRole.TRAVELLER, user.role)
    }

    @Test
    fun testTravellerSignUpDoesNotEnterTravellerPortal() = runTest {
        var navigated = false
        viewModel.signUp(
            fullName = "Sara Traveller",
            email = "sara@travel.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.TRAVELLER,
            onSuccess = { navigated = true }
        )
        advanceUntilIdle()

        assertFalse("Must not enter Authenticated state on signup", viewModel.authState.value is AuthState.Authenticated)
        assertEquals(AuthState.SignedOut, viewModel.authState.value)
        assertNull(viewModel.currentUser.value)
        assertTrue("Must navigate to sign-in screen", navigated)
        assertTrue(viewModel.accountCreatedSuccess.value?.contains("Traveller account created successfully", ignoreCase = true) == true)
        assertEquals("sara@travel.com", viewModel.lastRegisteredEmail.value)
    }

    @Test
    fun testGuideSignUpDoesNotEnterGuidePortal() = runTest {
        var navigated = false
        viewModel.signUp(
            fullName = "Liam Guide",
            email = "liam@guide.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.GUIDE,
            onSuccess = { navigated = true }
        )
        advanceUntilIdle()

        assertFalse("Must not enter Authenticated state on signup", viewModel.authState.value is AuthState.Authenticated)
        assertEquals(AuthState.SignedOut, viewModel.authState.value)
        assertNull(viewModel.currentUser.value)
        assertTrue("Must navigate to sign-in screen", navigated)
        assertTrue(viewModel.accountCreatedSuccess.value?.contains("Guide account created successfully", ignoreCase = true) == true)
        assertEquals("liam@guide.com", viewModel.lastRegisteredEmail.value)
    }

    @Test
    fun testExplicitTravellerSignInOpensTravellerPortal() = runTest {
        fakeRepository.signUpWithEmail("Traveller User", "traveller1@demo.com", "secret", UserRole.TRAVELLER)
        viewModel.signIn("traveller1@demo.com", "secret", UserRole.TRAVELLER)
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue("Explicit sign-in must open Traveller portal", state is AuthState.Authenticated)
        assertEquals(UserRole.TRAVELLER, (state as AuthState.Authenticated).user.role)
    }

    @Test
    fun testExplicitGuideSignInOpensGuidePortal() = runTest {
        fakeRepository.signUpWithEmail("Guide User", "guide1@demo.com", "secret", UserRole.GUIDE)
        viewModel.signIn("guide1@demo.com", "secret", UserRole.GUIDE)
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue("Explicit sign-in must open Guide portal directly", state is AuthState.Authenticated)
        assertEquals(UserRole.GUIDE, (state as AuthState.Authenticated).user.role)
        assertEquals(ApprovalStatus.APPROVED, (state as AuthState.Authenticated).user.approvalStatus)
    }

    @Test
    fun testExistingAuthenticatedSessionRestoresCorrectly() = runTest {
        // User explicitly signs in
        viewModel.signIn("user@roamly.com", "password", UserRole.TRAVELLER)
        advanceUntilIdle()
        assertTrue(viewModel.authState.value is AuthState.Authenticated)

        // Session restore on app launch
        viewModel.restoreSession()
        advanceUntilIdle()
        assertTrue("Existing authenticated session must restore correctly", viewModel.authState.value is AuthState.Authenticated)
        assertEquals("user@roamly.com", (viewModel.authState.value as AuthState.Authenticated).user.email)
    }

    @Test
    fun testSignUpCreatedSessionDoesNotRestoreOnAppLaunch() = runTest {
        // User signs up but has not signed in
        viewModel.signUp("New User", "newbie@roamly.com", "password", "password", UserRole.TRAVELLER)
        advanceUntilIdle()
        assertEquals(AuthState.SignedOut, viewModel.authState.value)

        // App launches and restoreSession() runs
        viewModel.restoreSession()
        advanceUntilIdle()

        assertEquals("Signup-only user must not be restored to Authenticated", AuthState.SignedOut, viewModel.authState.value)
        assertNull(viewModel.currentUser.value)
    }

    @Test
    fun testDuplicateEmailErrorSanitization() {
        val client = SupabaseNetworkClient(
            baseUrl = "https://example.supabase.co",
            anonKey = "anon-key",
            tokenProvider = { null }
        )

        val variations = listOf(
            "{\"code\": 422, \"msg\": \"User already registered\"}",
            "{\"code\": 400, \"msg\": \"already registered\"}",
            "{\"code\": 400, \"msg\": \"user_already_exists\"}",
            "{\"code\": 400, \"msg\": \"An account with this email already exists\"}",
            "{\"code\": 422, \"msg\": \"email address already in use\"}"
        )

        for (json in variations) {
            val response = Response.error<Unit>(
                422,
                json.toResponseBody("application/json".toMediaType())
            )
            val msg = client.parseErrorMessage(response)
            assertEquals(
                "An account with this email address already exists. Please sign in instead.",
                msg
            )
        }
    }

    @Test
    fun testMissingRoleDataDoesNotSilentlyBecomeTraveller() = runTest {
        // User with no role in database or metadata
        fakeRepository.registeredUsers["unknown@user.com"] = AuthUser(
            id = "unk-1",
            email = "unknown@user.com",
            fullName = "Unknown User",
            role = null,
            approvalStatus = ApprovalStatus.PENDING,
            isEmailConfirmed = true
        )

        // Login with intendedRole
        val result = fakeRepository.signInWithEmail("unknown@user.com", "pass", UserRole.TRAVELLER)
        assertTrue(result is AuthResult.Success)

        val state = fakeRepository.authState.value
        assertTrue("Missing role must route to AwaitingRoleSelection, never silently Authenticated as TRAVELLER",
            state is AuthState.AwaitingRoleSelection)
        assertFalse("Must not be authenticated as Traveller", state is AuthState.Authenticated)
    }

    @Test
    fun testSupabaseDtoMappingGuideApprovalAndRolePrecedence() {
        // 1. Profile with Guide role and Approved status
        val guideApprovedProfile = ProfileDto(
            id = "g-1",
            fullName = "Grace Guide",
            role = "guide",
            approvalStatus = "approved"
        )
        val user1 = SupabaseAuthRepository.mapDtoToAuthUser(null, guideApprovedProfile, null)
        assertEquals(UserRole.GUIDE, user1.role)
        assertEquals(ApprovalStatus.APPROVED, user1.approvalStatus)

        // 2. Profile with Guide role and Pending status: guides do not require approval, so resolved status is APPROVED
        val guidePendingProfile = ProfileDto(
            id = "g-2",
            fullName = "Pending Guide",
            role = "guide",
            approvalStatus = "pending"
        )
        val user2 = SupabaseAuthRepository.mapDtoToAuthUser(null, guidePendingProfile, null)
        assertEquals(UserRole.GUIDE, user2.role)
        assertEquals(ApprovalStatus.APPROVED, user2.approvalStatus)

        // 3. Profile with Guide role and Rejected status: rejected status is preserved
        val guideRejectedProfile = ProfileDto(
            id = "g-3",
            fullName = "Rejected Guide",
            role = "guide",
            approvalStatus = "rejected"
        )
        val user3 = SupabaseAuthRepository.mapDtoToAuthUser(null, guideRejectedProfile, null)
        assertEquals(UserRole.GUIDE, user3.role)
        assertEquals(ApprovalStatus.REJECTED, user3.approvalStatus)

        // 4. Profile with Traveller role
        val travellerProfile = ProfileDto(
            id = "t-1",
            fullName = "Tina Traveler",
            role = "traveller",
            approvalStatus = null
        )
        val user4 = SupabaseAuthRepository.mapDtoToAuthUser(null, travellerProfile, null)
        assertEquals(UserRole.TRAVELLER, user4.role)
        assertEquals(ApprovalStatus.APPROVED, user4.approvalStatus)

        // 5. Missing role in profile and metadata: must be null, NOT silently Traveller
        val userDtoNoRole = UserDto(id = "u-none", email = "norole@roamly.app")
        val user5 = SupabaseAuthRepository.mapDtoToAuthUser(userDtoNoRole, null, fallbackRole = null)
        assertNull("Missing role must remain null", user5.role)

        // 6. Database profile takes precedence over user metadata
        val profileGuide = ProfileDto(id = "g-4", role = "guide", approvalStatus = "approved")
        val userDtoTraveller = UserDto(
            id = "g-4",
            email = "g4@roamly.app",
            userMetadata = mapOf("role" to "traveller")
        )
        val user6 = SupabaseAuthRepository.mapDtoToAuthUser(userDtoTraveller, profileGuide, null)
        assertEquals("Database profile role must take precedence over user_metadata", UserRole.GUIDE, user6.role)
    }
}
