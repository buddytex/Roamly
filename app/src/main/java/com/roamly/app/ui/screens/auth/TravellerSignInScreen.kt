package com.roamly.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.sp
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
fun TravellerSignInScreen(
    authViewModel: AuthViewModel,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onBackToRoleSelection: () -> Unit
) {
    BackHandler(onBack = onBackToRoleSelection)

    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()
    val formErrors by authViewModel.formErrors.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val isLoading = authState is AuthState.SigningIn || authState is AuthState.Initializing
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
            title = "Welcome back",
            subtitle = "Your next journey starts here."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error Banner
        if (formErrors.generalError != null) {
            AuthErrorBanner(message = formErrors.generalError!!)
            
            if (formErrors.generalError!!.contains("verified", ignoreCase = true) ||
                formErrors.generalError!!.contains("confirmation link", ignoreCase = true)) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = RoamlySurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoamlyBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Email verification is required by default in your Supabase project. You can click the link in your inbox (or in Supabase Dashboard -> Auth -> Users -> click 'Confirm user').",
                            style = MaterialTheme.typography.bodySmall,
                            color = RoamlyTextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { authViewModel.signInAsDemo(UserRole.TRAVELLER) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoamlyPrimary)
                        ) {
                            Text(
                                text = "Instant Demo Access (Bypass Verification)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

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

        Spacer(modifier = Modifier.height(16.dp))

        // Password Field
        AuthTextField(
            value = password,
            onValueChange = {
                password = it
                if (formErrors.passwordError != null) authViewModel.clearErrors()
            },
            label = "Password",
            placeholder = "Enter your password",
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            errorMessage = formErrors.passwordError,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Forgot Password
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = RoamlyPrimary,
                modifier = Modifier
                    .clickable(enabled = !isLoading) { onNavigateToForgotPassword() }
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Primary Sign In Button
        Button(
            onClick = {
                authViewModel.signIn(email, password, UserRole.TRAVELLER)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
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
                    text = "Sign In as Traveller",
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

        // Google Sign-In
        GoogleSignInButton(
            onClick = {
                authViewModel.signInWithGoogle(context, UserRole.TRAVELLER)
            },
            isLoading = isLoading
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Demo Traveller Fast Sign-In
        OutlinedButton(
            onClick = { authViewModel.signInAsDemo(UserRole.TRAVELLER) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, RoamlyPrimary)
        ) {
            Text(
                text = "Demo Sign-In (Skip for Hackathon)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = RoamlyPrimary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Sign Up Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Don't have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = RoamlyTextSecondary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Sign Up",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = RoamlyPrimary,
                modifier = Modifier
                    .clickable(enabled = !isLoading) { onNavigateToSignUp() }
                    .padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
