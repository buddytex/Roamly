package com.roamly.app.auth.model

import com.roamly.app.model.UserRole

enum class ApprovalStatus(val displayName: String) {
    PENDING("Pending Approval"),
    APPROVED("Approved"),
    REJECTED("Access Denied")
}

data class AuthUser(
    val id: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val approvalStatus: ApprovalStatus = ApprovalStatus.APPROVED,
    val phone: String? = null,
    val isEmailConfirmed: Boolean = true
)

sealed class AuthState {
    object Initializing : AuthState()
    object SignedOut : AuthState()
    object SigningIn : AuthState()
    object SigningUp : AuthState()
    data class AwaitingEmailVerification(val email: String) : AuthState()
    data class AwaitingGuideApproval(val user: AuthUser) : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    object SigningOut : AuthState()
    data class Error(val message: String, val isRecoverable: Boolean = true) : AuthState()
}

sealed class AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>()
    data class Failure(val message: String, val isRecoverable: Boolean = true) : AuthResult<Nothing>()
}
