package com.roamly.app.auth.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roamly.app.auth.data.AuthRepository
import com.roamly.app.auth.google.GoogleSignInHelper
import com.roamly.app.auth.google.GoogleSignInResult
import com.roamly.app.auth.model.AuthResult
import com.roamly.app.auth.model.AuthState
import com.roamly.app.auth.model.AuthUser
import com.roamly.app.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthFormErrors(
    val fullNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val generalError: String? = null
) {
    val hasErrors: Boolean
        get() = fullNameError != null || emailError != null ||
                passwordError != null || confirmPasswordError != null || generalError != null
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Initializing)

    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedPortal = MutableStateFlow<UserRole?>(null)
    val selectedPortal: StateFlow<UserRole?> = _selectedPortal.asStateFlow()

    private val _formErrors = MutableStateFlow(AuthFormErrors())
    val formErrors: StateFlow<AuthFormErrors> = _formErrors.asStateFlow()

    private val _passwordResetSent = MutableStateFlow(false)
    val passwordResetSent: StateFlow<Boolean> = _passwordResetSent.asStateFlow()

    private val _accountCreatedSuccess = MutableStateFlow<String?>(null)
    val accountCreatedSuccess: StateFlow<String?> = _accountCreatedSuccess.asStateFlow()

    private val _lastRegisteredEmail = MutableStateFlow<String?>(null)
    val lastRegisteredEmail: StateFlow<String?> = _lastRegisteredEmail.asStateFlow()

    private var isEmailSignInRunning = false
    private var isEmailSignUpRunning = false
    private var isPasswordResetRunning = false
    private var isSessionRestoreRunning = false
    private var isDemoSignInRunning = false
    private var isGoogleSignInRunning = false
    private var isSignOutRunning = false

    init {
        restoreSession()
    }

    fun selectPortal(role: UserRole) {
        _selectedPortal.value = role
        clearErrors()
    }

    fun clearPortal() {
        _selectedPortal.value = null
        _accountCreatedSuccess.value = null
        clearErrors()
    }

    fun clearAccountCreatedSuccess() {
        _accountCreatedSuccess.value = null
    }

    fun restoreSession() {
        if (isSessionRestoreRunning) return
        isSessionRestoreRunning = true
        viewModelScope.launch {
            try {
                authRepository.restoreSession()
            } finally {
                isSessionRestoreRunning = false
            }
        }
    }

    fun validateEmail(email: String): String? {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return when {
            email.isBlank() -> "Email address is required."
            !email.matches(emailRegex) -> "Please enter a valid email address (e.g. name@example.com)."
            else -> null
        }
    }

    fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "Password is required."
            password.length < 6 -> "Password must be at least 6 characters long."
            else -> null
        }
    }

    fun validateFullName(name: String): String? {
        return if (name.trim().isBlank()) "Full name is required." else null
    }

    fun signIn(email: String, password: String, role: UserRole) {
        _accountCreatedSuccess.value = null
        val emailErr = validateEmail(email)
        val passErr = validatePassword(password)

        if (emailErr != null || passErr != null) {
            _formErrors.value = AuthFormErrors(emailError = emailErr, passwordError = passErr)
            return
        }

        if (isEmailSignInRunning) return
        isEmailSignInRunning = true
        _formErrors.value = AuthFormErrors()

        viewModelScope.launch {
            try {
                val result = authRepository.signInWithEmail(email.trim(), password, role)
                if (result is AuthResult.Failure) {
                    _formErrors.value = AuthFormErrors(generalError = result.message)
                }
            } catch (e: Exception) {
                _formErrors.value = AuthFormErrors(generalError = e.localizedMessage ?: "Sign in failed. Please try again.")
            } finally {
                isEmailSignInRunning = false
            }
        }
    }

    fun signInAsDemo(role: UserRole) {
        if (isDemoSignInRunning) return
        isDemoSignInRunning = true
        _formErrors.value = AuthFormErrors()
        _selectedPortal.value = role
        viewModelScope.launch {
            try {
                authRepository.signInAsDemoUser(role)
            } finally {
                isDemoSignInRunning = false
            }
        }
    }

    fun signUp(
        fullName: String,
        email: String,
        password: String,
        confirmPassword: String,
        role: UserRole,
        onSuccess: (() -> Unit)? = null
    ) {
        clearErrors()
        _accountCreatedSuccess.value = null

        val nameErr = validateFullName(fullName)
        val emailErr = validateEmail(email)
        val passErr = validatePassword(password)
        val confirmErr = when {
            confirmPassword.isBlank() -> "Please confirm your password."
            confirmPassword != password -> "Passwords do not match."
            else -> null
        }

        if (nameErr != null || emailErr != null || passErr != null || confirmErr != null) {
            _formErrors.value = AuthFormErrors(
                fullNameError = nameErr,
                emailError = emailErr,
                passwordError = passErr,
                confirmPasswordError = confirmErr
            )
            return
        }

        if (isEmailSignUpRunning) return
        isEmailSignUpRunning = true
        _formErrors.value = AuthFormErrors()

        viewModelScope.launch {
            try {
                val result = authRepository.signUpWithEmail(fullName.trim(), email.trim(), password, role)
                when (result) {
                    is AuthResult.Success -> {
                        val roleLabel = if (role == UserRole.GUIDE) "Guide" else "Traveller"
                        _accountCreatedSuccess.value = "$roleLabel account created successfully! Please sign in with your credentials."
                        _lastRegisteredEmail.value = email.trim()
                        _selectedPortal.value = role
                        onSuccess?.invoke()
                    }
                    is AuthResult.Failure -> {
                        _formErrors.value = AuthFormErrors(generalError = result.message)
                    }
                }
            } catch (e: Exception) {
                _formErrors.value = AuthFormErrors(generalError = e.localizedMessage ?: "Registration failed. Please try again.")
            } finally {
                isEmailSignUpRunning = false
            }
        }
    }

    fun signInWithGoogle(context: Context, role: UserRole? = null) {
        if (isGoogleSignInRunning) return
        isGoogleSignInRunning = true
        _formErrors.value = AuthFormErrors()

        viewModelScope.launch {
            try {
                val helper = GoogleSignInHelper(context)
                when (val googleResult = helper.signInWithGoogle()) {
                    is GoogleSignInResult.Success -> {
                        val authResult = authRepository.signInWithGoogle(googleResult.idToken, role)
                        if (authResult is AuthResult.Failure) {
                            _formErrors.value = AuthFormErrors(generalError = authResult.message)
                        } else if (role != null) {
                            _selectedPortal.value = role
                        }
                    }
                    is GoogleSignInResult.Cancelled -> {
                        // User dismissed the bottom sheet, stop loading without loud error
                    }
                    is GoogleSignInResult.Error -> {
                        _formErrors.value = AuthFormErrors(generalError = googleResult.message)
                    }
                }
            } finally {
                isGoogleSignInRunning = false
            }
        }
    }

    fun assignUserRole(role: UserRole) {
        _selectedPortal.value = role
        authRepository.assignUserRole(role)
        clearErrors()
    }

    fun sendPasswordReset(email: String) {
        val emailErr = validateEmail(email)
        if (emailErr != null) {
            _formErrors.value = AuthFormErrors(emailError = emailErr)
            return
        }

        if (isPasswordResetRunning) return
        isPasswordResetRunning = true
        _formErrors.value = AuthFormErrors()
        _passwordResetSent.value = false

        viewModelScope.launch {
            try {
                authRepository.sendPasswordResetEmail(email.trim())
                // Always set success to prevent user enumeration
                _passwordResetSent.value = true
            } catch (e: Exception) {
                _passwordResetSent.value = true
            } finally {
                isPasswordResetRunning = false
            }
        }
    }

    fun resetPasswordState() {
        _passwordResetSent.value = false
        clearErrors()
    }

    fun signOut() {
        if (isSignOutRunning) return
        isSignOutRunning = true
        _accountCreatedSuccess.value = null
        _lastRegisteredEmail.value = null
        viewModelScope.launch {
            try {
                authRepository.signOut()
                _selectedPortal.value = null
                clearErrors()
            } finally {
                isSignOutRunning = false
            }
        }
    }

    fun clearErrors() {
        _formErrors.value = AuthFormErrors()
        authRepository.clearError()
    }
}
