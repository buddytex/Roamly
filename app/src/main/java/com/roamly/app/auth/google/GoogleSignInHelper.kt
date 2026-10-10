package com.roamly.app.auth.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.roamly.app.BuildConfig

sealed class GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult()
    data class Cancelled(val message: String = "Google Sign-In was cancelled.") : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
}

class GoogleSignInHelper(private val context: Context) {

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    suspend fun signInWithGoogle(): GoogleSignInResult {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

        if (serverClientId.isBlank()) {
            return GoogleSignInResult.Error(
                "Google Sign-In is not configured yet. Please configure GOOGLE_WEB_CLIENT_ID in gradle.properties."
            )
        }

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(idToken = googleIdTokenCredential.idToken)
            } else {
                GoogleSignInResult.Error("Unexpected credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled()
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Error("No Google account found on device. Please sign in to a Google account.")
        } catch (e: Exception) {
            GoogleSignInResult.Error(e.localizedMessage ?: "Google Sign-In failed.")
        }
    }
}
