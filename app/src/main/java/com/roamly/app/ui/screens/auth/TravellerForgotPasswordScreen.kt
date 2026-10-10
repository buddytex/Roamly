package com.roamly.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roamly.app.auth.ui.AuthViewModel
import com.roamly.app.ui.components.AuthErrorBanner
import com.roamly.app.ui.components.AuthHeader
import com.roamly.app.ui.components.AuthTextField
import com.roamly.app.ui.theme.RoamlyBackground
import com.roamly.app.ui.theme.RoamlyPrimary
import com.roamly.app.ui.theme.RoamlyPrimaryContainer
import com.roamly.app.ui.theme.RoamlySurface
import com.roamly.app.ui.theme.RoamlyTextPrimary
import com.roamly.app.ui.theme.RoamlyTextSecondary

@Composable
fun TravellerForgotPasswordScreen(
    authViewModel: AuthViewModel,
    onBackToSignIn: () -> Unit
) {
    val formErrors by authViewModel.formErrors.collectAsState()
    val isResetSent by authViewModel.passwordResetSent.collectAsState()

    var email by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RoamlyBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Back Navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackToSignIn,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to sign in",
                    tint = RoamlyTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Back to Sign In",
                style = MaterialTheme.typography.bodyMedium,
                color = RoamlyTextSecondary,
                modifier = Modifier.clickable { onBackToSignIn() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Branding & Header
        AuthHeader(
            title = "Reset Password",
            subtitle = "Enter your registered email address and we'll send you recovery instructions."
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isResetSent) {
            // Success confirmation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RoamlyPrimaryContainer.copy(alpha = 0.5f))
                    .border(1.dp, RoamlyPrimary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RoamlyPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Recovery Link Sent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RoamlyTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If an account with $email exists, instructions have been sent. Please check your inbox and spam folder.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoamlyTextSecondary,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    authViewModel.resetPasswordState()
                    onBackToSignIn()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoamlyPrimary,
                    contentColor = RoamlySurface
                )
            ) {
                Text(
                    text = "Return to Sign In",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Error Banner
            if (formErrors.generalError != null) {
                AuthErrorBanner(message = formErrors.generalError!!)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Email Field
            AuthTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (formErrors.emailError != null) authViewModel.clearErrors()
                },
                label = "Registered Email Address",
                placeholder = "name@example.com",
                leadingIcon = Icons.Default.Email,
                errorMessage = formErrors.emailError,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Submit Button
            Button(
                onClick = {
                    isSubmitting = true
                    authViewModel.sendPasswordReset(email)
                    isSubmitting = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isSubmitting && email.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoamlyPrimary,
                    contentColor = RoamlySurface
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = RoamlySurface
                    )
                } else {
                    Text(
                        text = "Send Recovery Email",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
