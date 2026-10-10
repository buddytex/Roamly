package com.roamly.app.auth.data

import com.roamly.app.auth.model.AuthResult
import com.roamly.app.auth.model.AuthState
import com.roamly.app.auth.model.AuthUser
import com.roamly.app.model.UserRole
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentUser: StateFlow<AuthUser?>

    suspend fun restoreSession()
    suspend fun signInWithEmail(email: String, password: String, intendedRole: UserRole): AuthResult<AuthUser>
    suspend fun signUpWithEmail(fullName: String, email: String, password: String, role: UserRole): AuthResult<AuthUser>
    suspend fun signInWithGoogle(idToken: String, intendedRole: UserRole): AuthResult<AuthUser>
    suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit>
    suspend fun signOut()
    fun clearError()
}
