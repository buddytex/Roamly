package com.roamly.app.auth.google

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.roamly.app.BuildConfig

sealed class GoogleSignInResult {
    data class Success(
        val idToken: String,
        val email: String? = null,
        val displayName: String? = null,
        val photoUrl: String? = null
    ) : GoogleSignInResult()

    data class Cancelled(val message: String = "Sign-in was cancelled. You can try again.") : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
}

class GoogleSignInHelper(private val context: Context) {

    private val credentialManager: CredentialManager by lazy {
        CredentialManager.create(context)
    }

    /**
     * Resolves the Web Client ID either from Google Services generated string resources
     * or from BuildConfig / gradle.properties.
     */
    fun resolveServerClientId(): String {
        try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val clientId = context.getString(resId)
                if (clientId.isNotBlank()) return clientId
            }
        } catch (e: Exception) {
            // Ignore resource lookup errors
        }
        return BuildConfig.GOOGLE_WEB_CLIENT_ID
    }

    suspend fun signInWithGoogle(): GoogleSignInResult {
        val serverClientId = resolveServerClientId()

        if (serverClientId.isBlank()) {
            return GoogleSignInResult.Error(
                "Google sign-in is not configured yet. Please configure Firebase with google-services.json."
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
                GoogleSignInResult.Success(
                    idToken = googleIdTokenCredential.idToken,
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName,
                    photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
                )
            } else {
                GoogleSignInResult.Error("Google sign-in could not be completed. Please try again.")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled()
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Error("No suitable Google account is available on this device.")
        } catch (e: GetCredentialInterruptedException) {
            GoogleSignInResult.Cancelled("Sign-in was interrupted. Please try again.")
        } catch (e: GetCredentialProviderConfigurationException) {
            GoogleSignInResult.Error("Google sign-in is not configured yet.")
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: ""
            val userFriendly = when {
                msg.contains("network", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) ->
                    "Please check your internet connection and try again."
                msg.contains("cancelled", ignoreCase = true) || msg.contains("canceled", ignoreCase = true) ->
                    "Sign-in was cancelled. You can try again."
                msg.contains("configuration", ignoreCase = true) || msg.contains("not configured", ignoreCase = true) ->
                    "Google sign-in is not configured yet."
                else ->
                    "Google sign-in could not be completed. Please try again."
            }
            GoogleSignInResult.Error(userFriendly)
        }
    }
}
