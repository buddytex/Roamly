package com.roamly.app.auth.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.roamly.app.BuildConfig
import com.roamly.app.auth.model.ApprovalStatus
import com.roamly.app.auth.model.AuthResult
import com.roamly.app.auth.model.AuthState
import com.roamly.app.auth.model.AuthUser
import com.roamly.app.auth.network.AuthResponse
import com.roamly.app.auth.network.IdTokenLoginRequest
import com.roamly.app.auth.network.PasswordLoginRequest
import com.roamly.app.auth.network.ProfileDto
import com.roamly.app.auth.network.RecoverRequest
import com.roamly.app.auth.network.RefreshTokenRequest
import com.roamly.app.auth.network.SignUpRequest
import com.roamly.app.auth.network.SupabaseNetworkClient
import com.roamly.app.model.UserRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SupabaseAuthRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    customBaseUrl: String? = null,
    customAnonKey: String? = null
) : AuthRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("roamly_auth_session", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val supabaseUrl: String = customBaseUrl ?: BuildConfig.SUPABASE_URL
    private val supabaseAnonKey: String = customAnonKey ?: BuildConfig.SUPABASE_ANON_KEY

    private val isBackendConfigured: Boolean =
        supabaseUrl.isNotBlank() &&
        !supabaseUrl.contains("your-project.supabase.co") &&
        supabaseAnonKey.isNotBlank() &&
        !supabaseAnonKey.contains("your-anon-key")

    private val networkClient by lazy {
        SupabaseNetworkClient(
            baseUrl = if (supabaseUrl.isBlank() || !supabaseUrl.startsWith("http")) "https://placeholder.supabase.co" else supabaseUrl,
            anonKey = supabaseAnonKey,
            tokenProvider = { prefs.getString(KEY_ACCESS_TOKEN, null) }
        )
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    override suspend fun restoreSession() = withContext(ioDispatcher) {
        val cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val cachedUserJson = prefs.getString(KEY_CACHED_USER, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (cachedToken.isNullOrBlank() || cachedUserJson.isNullOrBlank()) {
            _currentUser.value = null
            _authState.value = AuthState.SignedOut
            return@withContext
        }

        val cachedUser = try {
            gson.fromJson(cachedUserJson, AuthUser::class.java)
        } catch (e: Exception) {
            null
        }

        if (cachedUser == null) {
            clearSession()
            _authState.value = AuthState.SignedOut
            return@withContext
        }

        // Check if token has expired and backend is configured
        val now = System.currentTimeMillis()
        if (now >= expiresAt && isBackendConfigured) {
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            if (!refreshToken.isNullOrBlank()) {
                try {
                    val refreshResponse = networkClient.authApi.refreshToken(
                        RefreshTokenRequest(refreshToken)
                    )
                    if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                        saveSession(refreshResponse.body()!!, cachedUser)
                    } else {
                        clearSession()
                        _authState.value = AuthState.SignedOut
                        return@withContext
                    }
                } catch (e: Exception) {
                    // Network issue, preserve cached user if not critically expired
                }
            }
        }

        // Apply state based on role and approval status
        _currentUser.value = cachedUser
        when {
            cachedUser.role == UserRole.GUIDE && cachedUser.approvalStatus == ApprovalStatus.PENDING -> {
                _authState.value = AuthState.AwaitingGuideApproval(cachedUser)
            }
            cachedUser.role == UserRole.GUIDE && cachedUser.approvalStatus == ApprovalStatus.REJECTED -> {
                clearSession()
                _authState.value = AuthState.Error(
                    "Your Guide application was declined. Please contact the administrator.",
                    isRecoverable = false
                )
            }
            !cachedUser.isEmailConfirmed -> {
                _authState.value = AuthState.AwaitingEmailVerification(cachedUser.email)
            }
            else -> {
                _authState.value = AuthState.Authenticated(cachedUser)
            }
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
        intendedRole: UserRole
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        if (!isBackendConfigured) {
            return@withContext AuthResult.Failure(
                "Supabase backend is not configured yet. Please configure SUPABASE_URL and SUPABASE_ANON_KEY in gradle.properties.",
                isRecoverable = false
            )
        }

        _authState.value = AuthState.SigningIn

        try {
            val response = networkClient.authApi.signInWithPassword(
                PasswordLoginRequest(email.trim(), password)
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = networkClient.parseErrorMessage(response)
                _authState.value = AuthState.Error(errorMsg)
                return@withContext AuthResult.Failure(errorMsg)
            }

            val authBody = response.body()!!
            val userDto = authBody.user
            val userId = userDto?.id ?: ""

            // Fetch Profile from PostgreSQL to determine true role and approval status
            val profile = fetchProfileSafely(userId)
            val authUser = mapDtoToAuthUser(userDto, profile, intendedRole)

            saveSession(authBody, authUser)
            _currentUser.value = authUser

            // Route based on role & approval
            when {
                authUser.role == UserRole.GUIDE && authUser.approvalStatus == ApprovalStatus.PENDING -> {
                    _authState.value = AuthState.AwaitingGuideApproval(authUser)
                }
                authUser.role == UserRole.GUIDE && authUser.approvalStatus == ApprovalStatus.REJECTED -> {
                    clearSession()
                    val msg = "Your Guide access request was not approved."
                    _authState.value = AuthState.Error(msg, isRecoverable = false)
                    return@withContext AuthResult.Failure(msg)
                }
                !authUser.isEmailConfirmed -> {
                    _authState.value = AuthState.AwaitingEmailVerification(authUser.email)
                }
                else -> {
                    _authState.value = AuthState.Authenticated(authUser)
                }
            }

            AuthResult.Success(authUser)
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Failed to connect to authentication server."
            _authState.value = AuthState.Error(message)
            AuthResult.Failure(message)
        }
    }

    override suspend fun signUpWithEmail(
        fullName: String,
        email: String,
        password: String,
        role: UserRole
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        if (!isBackendConfigured) {
            return@withContext AuthResult.Failure(
                "Supabase backend is not configured yet. Please configure SUPABASE_URL and SUPABASE_ANON_KEY in gradle.properties.",
                isRecoverable = false
            )
        }

        _authState.value = AuthState.SigningUp

        try {
            val metadata = mapOf(
                "full_name" to fullName.trim(),
                "role" to if (role == UserRole.GUIDE) "guide" else "traveller"
            )

            val response = networkClient.authApi.signUp(
                SignUpRequest(email.trim(), password, metadata)
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = networkClient.parseErrorMessage(response)
                _authState.value = AuthState.Error(errorMsg)
                return@withContext AuthResult.Failure(errorMsg)
            }

            val authBody = response.body()!!
            val userDto = authBody.user
            val isEmailConfirmed = userDto?.emailConfirmedAt != null

            val initialApproval = if (role == UserRole.GUIDE) ApprovalStatus.PENDING else ApprovalStatus.APPROVED
            val authUser = AuthUser(
                id = userDto?.id ?: "",
                email = email.trim(),
                fullName = fullName.trim(),
                role = role,
                approvalStatus = initialApproval,
                isEmailConfirmed = isEmailConfirmed
            )

            if (authBody.accessToken != null) {
                saveSession(authBody, authUser)
            }
            _currentUser.value = authUser

            when {
                !isEmailConfirmed -> {
                    _authState.value = AuthState.AwaitingEmailVerification(email.trim())
                }
                role == UserRole.GUIDE -> {
                    _authState.value = AuthState.AwaitingGuideApproval(authUser)
                }
                else -> {
                    _authState.value = AuthState.Authenticated(authUser)
                }
            }

            AuthResult.Success(authUser)
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Registration failed due to a network error."
            _authState.value = AuthState.Error(message)
            AuthResult.Failure(message)
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        intendedRole: UserRole
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        if (!isBackendConfigured) {
            return@withContext AuthResult.Failure(
                "Supabase backend is not configured yet. Please configure SUPABASE_URL and SUPABASE_ANON_KEY in gradle.properties.",
                isRecoverable = false
            )
        }

        _authState.value = AuthState.SigningIn

        try {
            val response = networkClient.authApi.signInWithIdToken(
                IdTokenLoginRequest(
                    provider = "google",
                    idToken = idToken,
                    clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { null }
                )
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = networkClient.parseErrorMessage(response)
                _authState.value = AuthState.Error(errorMsg)
                return@withContext AuthResult.Failure(errorMsg)
            }

            val authBody = response.body()!!
            val userDto = authBody.user
            val userId = userDto?.id ?: ""

            val profile = fetchProfileSafely(userId)
            val authUser = mapDtoToAuthUser(userDto, profile, intendedRole)

            saveSession(authBody, authUser)
            _currentUser.value = authUser

            when {
                authUser.role == UserRole.GUIDE && authUser.approvalStatus == ApprovalStatus.PENDING -> {
                    _authState.value = AuthState.AwaitingGuideApproval(authUser)
                }
                authUser.role == UserRole.GUIDE && authUser.approvalStatus == ApprovalStatus.REJECTED -> {
                    clearSession()
                    val msg = "Your Guide access request was not approved."
                    _authState.value = AuthState.Error(msg, isRecoverable = false)
                    return@withContext AuthResult.Failure(msg)
                }
                else -> {
                    _authState.value = AuthState.Authenticated(authUser)
                }
            }

            AuthResult.Success(authUser)
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Google sign-in failed. Please try again."
            _authState.value = AuthState.Error(message)
            AuthResult.Failure(message)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthResult<Unit> = withContext(ioDispatcher) {
        if (!isBackendConfigured) {
            return@withContext AuthResult.Failure(
                "Supabase backend is not configured yet.",
                isRecoverable = false
            )
        }

        try {
            val response = networkClient.authApi.recoverPassword(RecoverRequest(email.trim()))
            // Regardless of whether account exists, return success to prevent email enumeration
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            // Return failure only on raw connection drop
            AuthResult.Failure("Network error. Please verify your connection.")
        }
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        _authState.value = AuthState.SigningOut
        try {
            if (isBackendConfigured) {
                networkClient.authApi.logout()
            }
        } catch (e: Exception) {
            // Ignore network errors during sign-out
        } finally {
            clearSession()
            _currentUser.value = null
            _authState.value = AuthState.SignedOut
        }
    }

    override fun clearError() {
        if (_authState.value is AuthState.Error) {
            val user = _currentUser.value
            _authState.value = if (user != null) AuthState.Authenticated(user) else AuthState.SignedOut
        }
    }

    private suspend fun fetchProfileSafely(userId: String): ProfileDto? {
        if (userId.isBlank()) return null
        return try {
            val profileRes = networkClient.postgrestApi.getProfile("eq.$userId")
            if (profileRes.isSuccessful && !profileRes.body().isNullOrEmpty()) {
                profileRes.body()!!.first()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun mapDtoToAuthUser(
        userDto: com.roamly.app.auth.network.UserDto?,
        profile: ProfileDto?,
        fallbackRole: UserRole
    ): AuthUser {
        val roleStr = profile?.role ?: (userDto?.userMetadata?.get("role") as? String)
        val resolvedRole = if (roleStr?.equals("guide", ignoreCase = true) == true) {
            UserRole.GUIDE
        } else if (roleStr?.equals("traveller", ignoreCase = true) == true) {
            UserRole.TRAVELLER
        } else {
            fallbackRole
        }

        val approvalStr = profile?.approvalStatus
        val resolvedApproval = when (approvalStr?.lowercase()) {
            "approved" -> ApprovalStatus.APPROVED
            "rejected" -> ApprovalStatus.REJECTED
            "pending" -> ApprovalStatus.PENDING
            else -> if (resolvedRole == UserRole.GUIDE) ApprovalStatus.PENDING else ApprovalStatus.APPROVED
        }

        val fullName = profile?.fullName
            ?: (userDto?.userMetadata?.get("full_name") as? String)
            ?: (userDto?.userMetadata?.get("name") as? String)
            ?: userDto?.email?.substringBefore("@")
            ?: "Explorer"

        return AuthUser(
            id = userDto?.id ?: profile?.id ?: "",
            email = userDto?.email ?: profile?.email ?: "",
            fullName = fullName,
            role = resolvedRole,
            approvalStatus = resolvedApproval,
            phone = profile?.phone,
            isEmailConfirmed = userDto?.emailConfirmedAt != null
        )
    }

    private fun saveSession(authResponse: AuthResponse, user: AuthUser) {
        val expiresInMs = (authResponse.expiresIn ?: 3600L) * 1000L
        val expiresAt = System.currentTimeMillis() + expiresInMs

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, authResponse.accessToken)
            .putString(KEY_REFRESH_TOKEN, authResponse.refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putString(KEY_CACHED_USER, gson.toJson(user))
            .apply()
    }

    private fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_CACHED_USER)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "key_access_token"
        private const val KEY_REFRESH_TOKEN = "key_refresh_token"
        private const val KEY_EXPIRES_AT = "key_expires_at"
        private const val KEY_CACHED_USER = "key_cached_user"

        @Volatile
        private var instance: SupabaseAuthRepository? = null

        fun getInstance(context: Context): SupabaseAuthRepository {
            return instance ?: synchronized(this) {
                instance ?: SupabaseAuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
