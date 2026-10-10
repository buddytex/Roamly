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

class FakeAuthRepository : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    var shouldFailSignIn = false
    var signInErrorMessage = "Invalid credentials"
    var isEmailConfirmed = true

    override suspend fun restoreSession() {
        // default no-op for fake
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
        intendedRole: UserRole
    ): AuthResult<AuthUser> {
        if (shouldFailSignIn) {
            _authState.value = AuthState.Error(signInErrorMessage)
            return AuthResult.Failure(signInErrorMessage)
        }

        val approval = if (intendedRole == UserRole.GUIDE) ApprovalStatus.PENDING else ApprovalStatus.APPROVED
        val user = AuthUser(
            id = "test-user-id",
            email = email,
            fullName = "Test User",
            role = intendedRole,
            approvalStatus = approval,
            isEmailConfirmed = isEmailConfirmed
        )

        _currentUser.value = user
        _authState.value = if (!isEmailConfirmed) {
            AuthState.AwaitingEmailVerification(email)
        } else if (intendedRole == UserRole.GUIDE && approval == ApprovalStatus.PENDING) {
            AuthState.AwaitingGuideApproval(user)
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
        val approval = if (role == UserRole.GUIDE) ApprovalStatus.PENDING else ApprovalStatus.APPROVED
        val user = AuthUser(
            id = "new-user-id",
            email = email,
            fullName = fullName,
            role = role,
            approvalStatus = approval,
            isEmailConfirmed = isEmailConfirmed
        )
        _currentUser.value = user
        _authState.value = if (!isEmailConfirmed) {
            AuthState.AwaitingEmailVerification(email)
        } else if (role == UserRole.GUIDE) {
            AuthState.AwaitingGuideApproval(user)
        } else {
            AuthState.Authenticated(user)
        }
        return AuthResult.Success(user)
    }

    override suspend fun signInWithGoogle(idToken: String, intendedRole: UserRole): AuthResult<AuthUser> {
        return signInWithEmail("google@test.com", "", intendedRole)
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> {
        return AuthResult.Success(Unit)
    }

    override suspend fun signOut() {
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
    fun testGuideSignUpStartsAsPendingApproval() = runTest {
        viewModel.signUp(
            fullName = "Marcus Chen",
            email = "marcus@guide.com",
            password = "password123",
            confirmPassword = "password123",
            role = UserRole.GUIDE
        )
        advanceUntilIdle()

        val state = viewModel.authState.value
        assertTrue("Guide account must start as AwaitingGuideApproval", state is AuthState.AwaitingGuideApproval)
        val user = (state as AuthState.AwaitingGuideApproval).user
        assertEquals(UserRole.GUIDE, user.role)
        assertEquals(ApprovalStatus.PENDING, user.approvalStatus)
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
    fun testPreservationOfSingaporeItinerary() {
        val trips = SampleData.sampleTrips
        assertEquals("Singapore must be the single available trip", 1, trips.size)
        assertEquals("trip_singapore_2026", trips.first().id)
        assertEquals(5, trips.first().days.size)
    }
}
