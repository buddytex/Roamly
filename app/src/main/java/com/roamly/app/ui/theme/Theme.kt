package com.roamly.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val RoamlyLightColorScheme = lightColorScheme(
    primary = RoamlyPrimary,
    onPrimary = RoamlyOnPrimary,
    primaryContainer = RoamlyPrimaryContainer,
    onPrimaryContainer = RoamlyOnPrimaryContainer,
    secondary = RoamlySecondary,
    onSecondary = RoamlyOnSecondary,
    secondaryContainer = RoamlyPrimaryContainer,
    onSecondaryContainer = RoamlyOnPrimaryContainer,
    tertiary = RoamlyAccent,
    onTertiary = RoamlyTextPrimary,
    tertiaryContainer = RoamlyAccentContainer,
    onTertiaryContainer = RoamlyTextPrimary,
    background = RoamlyBackground,
    onBackground = RoamlyTextPrimary,
    surface = RoamlySurface,
    onSurface = RoamlyTextPrimary,
    surfaceVariant = RoamlySurfaceVariant,
    onSurfaceVariant = RoamlyTextSecondary,
    outline = RoamlyBorder,
    error = RoamlyError,
    onError = RoamlySurface
)

val RoamlyShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RoamlyTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = RoamlyBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = RoamlyLightColorScheme,
        typography = RoamlyTypography,
        shapes = RoamlyShapes,
        content = content
    )
}
