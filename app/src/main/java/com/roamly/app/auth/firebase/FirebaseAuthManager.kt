package com.roamly.app.auth.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {

    /**
     * Checks whether Firebase has been properly initialized in the application process.
     * Firebase initializes automatically when a valid google-services.json is present.
     */
    fun isConfigured(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context.applicationContext).isNotEmpty()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Retrieves the current authenticated Firebase user if Firebase is initialized.
     */
    fun getCurrentUser(context: Context): FirebaseUser? {
        return if (isConfigured(context)) {
            try {
                FirebaseAuth.getInstance().currentUser
            } catch (e: Throwable) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Verifies the Google ID token with Firebase Authentication and establishes
     * an authenticated Firebase user session.
     */
    suspend fun verifyGoogleIdToken(context: Context, idToken: String): Result<FirebaseUser> {
        if (!isConfigured(context)) {
            return Result.failure(
                IllegalStateException("Google sign-in is not configured yet. Please add google-services.json to the app module.")
            )
        }

        if (idToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Google ID token is missing. Please try signing in again.")
            )
        }

        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            val user = authResult.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Authentication succeeded but no user session was returned."))
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Google authentication credentials were not accepted. Please try again."))
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("This Google account is disabled or blocked."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(Exception("Please check your internet connection and try again."))
        } catch (e: Exception) {
            val friendlyMsg = when {
                e.message?.contains("network", ignoreCase = true) == true ->
                    "Please check your internet connection and try again."
                e.message?.contains("configuration", ignoreCase = true) == true ->
                    "Google sign-in is not configured yet."
                else -> "Authentication failed. Please try again."
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    /**
     * Signs out the active Firebase user session.
     */
    fun signOut(context: Context) {
        if (isConfigured(context)) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Throwable) {
                // Ignore errors during sign-out
            }
        }
    }
}
