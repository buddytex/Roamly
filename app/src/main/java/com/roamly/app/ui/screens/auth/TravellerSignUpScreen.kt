package com.roamly.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.roamly.app.auth.model.AuthState
import com.roamly.app.auth.ui.AuthViewModel
import com.roamly.app.model.UserRole
import com.roamly.app.ui.components.AuthErrorBanner
import com.roamly.app.ui.components.AuthHeader
import com.roamly.app.ui.components.AuthTextField
import com.roamly.app.ui.components.GoogleSignInButton
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyBorder
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun TravellerSignUpScreen(
    authViewModel: AuthViewModel,
    onNavigateToSignIn: () -> Unit,
    onBackToRoleSelection: () -> Unit
) {
    BackHandler(onBack = onBackToRoleSelection)

    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()
    val formErrors by authViewModel.formErrors.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val isLoading = authState is AuthState.SigningUp || authState is AuthState.Initializing
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // Back Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToRoleSelection,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to role selection",
                    tint = RoamlyTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Back",
                style = MaterialTheme.typography.bodyMedium,
                color = RoamlyTextSecondary,
                modifier = Modifier.clickable { onBackToRoleSelection() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Branding & Header
        AuthHeader(
            title = "Create Account",
            subtitle = "Join Roamly for a seamless travel experience."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error Banner
        if (formErrors.generalError != null) {
            AuthErrorBanner(message = formErrors.generalError!!)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Full Name Field
        AuthTextField(
            value = fullName,
            onValueChange = {
                fullName = it
                if (formErrors.fullNameError != null) authViewModel.clearErrors()
            },
            label = "Full Name",
            placeholder = "e.g. Eleanor Vance",
            leadingIcon = Icons.Default.Person,
            errorMessage = formErrors.fullNameError,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Email Field
        AuthTextField(
            value = email,
            onValueChange = {
                email = it
                if (formErrors.emailError != null) authViewModel.clearErrors()
            },
            label = "Email Address",
            placeholder = "name@example.com",
            leadingIcon = Icons.Default.Email,
            errorMessage = formErrors.emailError,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Password Field
        AuthTextField(
            value = password,
            onValueChange = {
                password = it
                if (formErrors.passwordError != null) authViewModel.clearErrors()
            },
            label = "Password (min. 6 characters)",
            placeholder = "Create a password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            errorMessage = formErrors.passwordError,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Confirm Password Field
        AuthTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                if (formErrors.confirmPasswordError != null) authViewModel.clearErrors()
            },
            label = "Confirm Password",
            placeholder = "Re-enter your password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            errorMessage = formErrors.confirmPasswordError,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Create Account Button
        Button(
            onClick = {
                authViewModel.signUp(
                    fullName = fullName,
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    role = UserRole.TRAVELLER,
                    onSuccess = onNavigateToSignIn
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading && fullName.isNotBlank() && email.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RoamlyPrimary,
                contentColor = RoamlySurface
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = RoamlySurface
                )
            } else {
                Text(
                    text = "Create Traveller Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Divider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = RoamlyBorder)
            Text(
                text = "OR",
                style = MaterialTheme.typography.labelMedium,
                color = RoamlyTextSecondary,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = RoamlyBorder)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Continue with Google
        GoogleSignInButton(
            onClick = {
                authViewModel.signInWithGoogle(context, UserRole.TRAVELLER)
            },
            isLoading = isLoading,
            text = "Sign up with Google"
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Already have an account?
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Already have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = RoamlyTextSecondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Sign In",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = RoamlyPrimary,
                modifier = Modifier
                    .clickable(enabled = !isLoading) { onNavigateToSignIn() }
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
