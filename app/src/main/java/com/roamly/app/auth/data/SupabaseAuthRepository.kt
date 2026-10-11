package com.roamly.app.auth.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.roamly.app.BuildConfig
import com.roamly.app.auth.firebase.FirebaseAuthManager
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

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("roamly_auth_session", Context.MODE_PRIVATE)
    private val roamlyPrefs: SharedPreferences =
        appContext.getSharedPreferences("roamly_app_prefs", Context.MODE_PRIVATE)
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

    fun getActiveNetworkClient(): SupabaseNetworkClient? {
        return if (isBackendConfigured) networkClient else null
    }

    fun isBackendEnabled(): Boolean = isBackendConfigured

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private fun getSavedRole(): UserRole? {
        val roleStr = roamlyPrefs.getString("key_selected_role", null)
            ?: prefs.getString(KEY_USER_ROLE, null)
        return if (roleStr != null) {
            try { UserRole.valueOf(roleStr) } catch (e: Exception) { null }
        } else null
    }

    private fun saveRole(role: UserRole) {
        roamlyPrefs.edit()
            .putString("key_selected_role", role.name)
            .putString("key_role", role.name)
            .apply()
        prefs.edit().putString(KEY_USER_ROLE, role.name).apply()
    }

    override suspend fun restoreSession() = withContext(ioDispatcher) {
        // 1. Check if Firebase Authentication has an active session
        val firebaseUser = FirebaseAuthManager.getCurrentUser(appContext)
        if (firebaseUser != null) {
            val cachedUserJson = prefs.getString(KEY_CACHED_USER, null)
            val cachedUser = try {
                if (cachedUserJson != null) gson.fromJson(cachedUserJson, AuthUser::class.java) else null
            } catch (e: Exception) {
                null
            }

            val resolvedRole = cachedUser?.role ?: getSavedRole()
            val authUser = AuthUser(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: cachedUser?.email ?: "",
                fullName = firebaseUser.displayName
                    ?: cachedUser?.fullName
                    ?: firebaseUser.email?.substringBefore("@")
                    ?: "Explorer",
                role = resolvedRole,
                approvalStatus = ApprovalStatus.APPROVED,
                photoUrl = firebaseUser.photoUrl?.toString() ?: cachedUser?.photoUrl,
                isEmailConfirmed = firebaseUser.isEmailVerified || cachedUser?.isEmailConfirmed == true
            )

            _currentUser.value = authUser

            when {
                resolvedRole == null -> {
                    _authState.value = AuthState.AwaitingRoleSelection(authUser)
                }
                else -> {
                    _authState.value = AuthState.Authenticated(authUser)
                }
            }
            return@withContext
        }

        // 2. Check cached email/password session
        val cachedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        val cachedUserJson = prefs.getString(KEY_CACHED_USER, null)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)

        if (cachedToken.isNullOrBlank() || cachedUserJson.isNullOrBlank()) {
            _currentUser.value = null
            _authState.value = AuthState.SignedOut
            return@withContext
        }

        var cachedUser = try {
            gson.fromJson(cachedUserJson, AuthUser::class.java)
        } catch (e: Exception) {
            null
        }

        if (cachedUser == null) {
            clearSession()
            _authState.value = AuthState.SignedOut
            return@withContext
        }

        // Check token expiry
        val now = System.currentTimeMillis()
        var currentToken = cachedToken
        if (now >= expiresAt && isBackendConfigured) {
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            if (!refreshToken.isNullOrBlank()) {
                try {
                    val refreshResponse = networkClient.authApi.refreshToken(
                        RefreshTokenRequest(refreshToken)
                    )
                    if (refreshResponse.isSuccessful && refreshResponse.body() != null) {
                        val body = refreshResponse.body()!!
                        currentToken = body.accessToken ?: cachedToken
                        saveSession(body, cachedUser)
                    } else {
                        clearSession()
                        _authState.value = AuthState.SignedOut
                        return@withContext
                    }
                } catch (e: Exception) {
                    // Preserve cached user if temporary network issue
                }
            }
        }

        // Refresh authoritative profile from backend to sync approval status and role
        if (isBackendConfigured && !currentToken.isNullOrBlank()) {
            val freshProfile = fetchProfileSafely(cachedUser.id, currentToken)
            if (freshProfile != null) {
                val authoritativeRole = when (freshProfile.role?.lowercase()) {
                    "guide" -> UserRole.GUIDE
                    "traveller" -> UserRole.TRAVELLER
                    else -> cachedUser.role
                }
                val authoritativeApproval = when (freshProfile.approvalStatus?.lowercase()) {
                    "rejected" -> ApprovalStatus.REJECTED
                    else -> ApprovalStatus.APPROVED
                }
                val updatedUser = cachedUser.copy(
                    fullName = freshProfile.fullName ?: cachedUser.fullName,
                    role = authoritativeRole,
                    approvalStatus = authoritativeApproval,
                    phone = freshProfile.phone ?: cachedUser.phone
                )
                if (updatedUser != cachedUser) {
                    cachedUser = updatedUser
                    prefs.edit().putString(KEY_CACHED_USER, gson.toJson(updatedUser)).apply()
                    authoritativeRole?.let { saveRole(it) }
                }
            }
        }

        _currentUser.value = cachedUser
        when {
            cachedUser.role == null -> {
                _authState.value = AuthState.AwaitingRoleSelection(cachedUser)
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
            val token = authBody.accessToken

            // Fetch Profile from PostgreSQL with access token to determine true role and approval status
            val profile = fetchProfileSafely(userId, token)
            val authUser = mapDtoToAuthUser(userDto, profile, fallbackRole = null)

            saveSession(authBody, authUser)
            authUser.role?.let { saveRole(it) }
            _currentUser.value = authUser

            // Route based on role
            when {
                authUser.role == null -> {
                    _authState.value = AuthState.AwaitingRoleSelection(authUser)
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

            val authUser = AuthUser(
                id = userDto?.id ?: "",
                email = email.trim(),
                fullName = fullName.trim(),
                role = role,
                approvalStatus = ApprovalStatus.APPROVED,
                isEmailConfirmed = isEmailConfirmed
            )

            // If an access token was provided in the signup response, ensure the profile
            // is persisted in PostgREST with the correct authoritative role.
            if (!authBody.accessToken.isNullOrBlank() && authUser.id.isNotBlank()) {
                try {
                    val profileToUpsert = ProfileDto(
                        id = authUser.id,
                        email = authUser.email,
                        fullName = authUser.fullName,
                        role = if (role == UserRole.GUIDE) "guide" else "traveller",
                        approvalStatus = "approved"
                    )
                    networkClient.postgrestApi.insertProfile(
                        profile = profileToUpsert,
                        authHeader = "Bearer ${authBody.accessToken}"
                    )
                } catch (e: Exception) {
                    // Ignored: database trigger handle_new_user already created the profile
                }
            }

            // CRITICAL: Registration must NOT automatically log the user in!
            // Even if Supabase returns tokens (e.g. when email confirmation is disabled),
            // do not save session or transition to AuthState.Authenticated.
            // Clear any temporary tokens so restoreSession() does not bypass login.
            clearSession()
            _currentUser.value = null
            _authState.value = AuthState.SignedOut

            AuthResult.Success(authUser)
        } catch (e: Exception) {
            val message = e.localizedMessage ?: "Registration failed due to a network error."
            _authState.value = AuthState.Error(message)
            AuthResult.Failure(message)
        }
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        intendedRole: UserRole?
    ): AuthResult<AuthUser> = withContext(ioDispatcher) {
        _authState.value = AuthState.SigningIn

        // 1. Verify Google ID token via Firebase Authentication
        val firebaseResult = FirebaseAuthManager.verifyGoogleIdToken(appContext, idToken)
        if (firebaseResult.isFailure) {
            val errorMsg = firebaseResult.exceptionOrNull()?.message
                ?: "Google sign-in could not be completed. Please try again."
            _authState.value = AuthState.Error(errorMsg)
            return@withContext AuthResult.Failure(errorMsg)
        }

        val firebaseUser = firebaseResult.getOrThrow()

        // 2. Resolve Role
        val resolvedRole = intendedRole ?: getSavedRole()

        val authUser = AuthUser(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            fullName = firebaseUser.displayName
                ?: firebaseUser.email?.substringBefore("@")
                ?: "Explorer",
            role = resolvedRole,
            approvalStatus = ApprovalStatus.APPROVED,
            phone = firebaseUser.phoneNumber,
            photoUrl = firebaseUser.photoUrl?.toString(),
            isEmailConfirmed = firebaseUser.isEmailVerified
        )

        // 3. Cache session in SharedPreferences (safe user metadata, no raw passwords/tokens)
        prefs.edit()
            .putString(KEY_CACHED_USER, gson.toJson(authUser))
            .apply()

        if (resolvedRole != null) {
            saveRole(resolvedRole)
        }

        _currentUser.value = authUser

        // 4. Navigate to appropriate state
        when {
            resolvedRole == null -> {
                _authState.value = AuthState.AwaitingRoleSelection(authUser)
            }
            else -> {
                _authState.value = AuthState.Authenticated(authUser)
            }
        }

        AuthResult.Success(authUser)
    }

    override fun assignUserRole(role: UserRole) {
        val user = _currentUser.value
        val updated = user?.copy(role = role, approvalStatus = ApprovalStatus.APPROVED) ?: AuthUser(
            id = "google-user",
            email = "",
            fullName = "Explorer",
            role = role,
            approvalStatus = ApprovalStatus.APPROVED
        )

        saveRole(role)
        prefs.edit().putString(KEY_CACHED_USER, gson.toJson(updated)).apply()
        _currentUser.value = updated

        _authState.value = AuthState.Authenticated(updated)
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
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Failure("Network error. Please verify your connection.")
        }
    }

    override suspend fun signInAsDemoUser(role: UserRole): AuthResult<AuthUser> = withContext(ioDispatcher) {
        val demoUser = AuthUser(
            id = "demo_user_" + if (role == UserRole.GUIDE) "guide" else "traveller",
            email = if (role == UserRole.GUIDE) "sarah.guide@roamly.app" else "alex.chen@roamly.app",
            fullName = if (role == UserRole.GUIDE) "Sarah Jenkins" else "Alex Chen",
            role = role,
            approvalStatus = ApprovalStatus.APPROVED,
            isEmailConfirmed = true
        )
        prefs.edit()
            .putString(KEY_CACHED_USER, gson.toJson(demoUser))
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + 86400000L)
            .apply()
        saveRole(role)
        _currentUser.value = demoUser
        _authState.value = AuthState.Authenticated(demoUser)
        AuthResult.Success(demoUser)
    }

    override suspend fun signOut() = withContext(ioDispatcher) {
        _authState.value = AuthState.SigningOut
        try {
            FirebaseAuthManager.signOut(appContext)
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
            _authState.value = if (user != null) {
                if (user.role == null) AuthState.AwaitingRoleSelection(user) else AuthState.Authenticated(user)
            } else {
                AuthState.SignedOut
            }
        }
    }

    private suspend fun fetchProfileSafely(userId: String, token: String? = null): ProfileDto? {
        if (userId.isBlank()) return null
        val effectiveToken = token ?: prefs.getString(KEY_ACCESS_TOKEN, null)
        val authHeader = if (!effectiveToken.isNullOrBlank()) "Bearer $effectiveToken" else null
        return try {
            val profileRes = networkClient.postgrestApi.getProfile(
                idFilter = "eq.$userId",
                authHeader = authHeader
            )
            if (profileRes.isSuccessful && !profileRes.body().isNullOrEmpty()) {
                profileRes.body()!!.first()
            } else null
        } catch (e: Exception) {
            null
        }
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
            .remove(KEY_USER_ROLE)
            .apply()
        roamlyPrefs.edit()
            .remove("key_selected_role")
            .remove("key_role")
            .apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "key_access_token"
        private const val KEY_REFRESH_TOKEN = "key_refresh_token"
        private const val KEY_EXPIRES_AT = "key_expires_at"
        private const val KEY_CACHED_USER = "key_cached_user"
        private const val KEY_USER_ROLE = "key_user_role"

        @Volatile
        private var instance: SupabaseAuthRepository? = null

        fun getInstance(context: Context): SupabaseAuthRepository {
            return instance ?: synchronized(this) {
                instance ?: SupabaseAuthRepository(context.applicationContext).also { instance = it }
            }
        }

        internal fun mapDtoToAuthUser(
            userDto: com.roamly.app.auth.network.UserDto?,
            profile: ProfileDto?,
            fallbackRole: UserRole? = null
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
                "rejected" -> ApprovalStatus.REJECTED
                else -> ApprovalStatus.APPROVED
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
    }
}
