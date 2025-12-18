package com.trailcam.mesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Original forest-inspired palette (kept for reference / light theme)
private val ForestGreen = Color(0xFF2D5A3D)
private val DarkGreen = Color(0xFF1A3D2A)
private val LightGreen = Color(0xFF4A7C59)
private val Bark = Color(0xFF5D4037)
private val Cream = Color(0xFFF5F5DC)
private val AlertOrange = Color(0xFFE65100)
private val AlertRed = Color(0xFFD32F2F)

// Deep Forest Dark palette (option 1)
private val DeepForestPrimary = Color(0xFF4CAF50)
private val DeepForestOnPrimary = Color(0xFFFFFFFF)
private val DeepForestPrimaryContainer = Color(0xFF1B5E20)
private val DeepForestOnPrimaryContainer = Color(0xFFE8F5E9)

private val DeepForestSecondary = Color(0xFF8D6E63)
private val DeepForestOnSecondary = Color(0xFFFFFFFF)
private val DeepForestSecondaryContainer = Color(0xFF4E342E)
private val DeepForestOnSecondaryContainer = Color(0xFFFFEBE0)

private val DeepForestTertiary = Color(0xFFFFB300)
private val DeepForestOnTertiary = Color(0xFF000000)

private val DeepForestBackground = Color(0xFF101515)
private val DeepForestOnBackground = Color(0xFFE0F2F1)

private val DeepForestSurface = Color(0xFF161C1C)
private val DeepForestOnSurface = Color(0xFFE0F2F1)
private val DeepForestSurfaceVariant = Color(0xFF263238)
private val DeepForestOnSurfaceVariant = Color(0xFFB0BEC5)

private val DeepForestError = Color(0xFFEF5350)
private val DeepForestOnError = Color(0xFFFFFFFF)

/**
 * Semantic status colors for connection state and alerts.
 * These are used alongside the Material color scheme.
 */
object TrailCamStatusColors {
    val Connected = DeepForestPrimary         // Rich green for healthy connection
    val Connecting = DeepForestTertiary      // Amber for in‑progress states
    val Disconnected = Color(0xFF616161)      // Neutral gray for idle / disconnected
}

/**
 * App-wide spacing and sizing tokens.
 * Using these keeps padding and layout consistent across screens.
 */
object TrailCamDimens {
    val ScreenPadding = 16.dp
    val CardCornerRadius = 12.dp
    val CardPadding = 16.dp
    val ContentSpacingSmall = 8.dp
    val ContentSpacingMedium = 16.dp
    val ContentSpacingLarge = 24.dp
}

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.15.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 0.15.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.25.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

private val DarkColorScheme = darkColorScheme(
    primary = DeepForestPrimary,
    onPrimary = DeepForestOnPrimary,
    primaryContainer = DeepForestPrimaryContainer,
    onPrimaryContainer = DeepForestOnPrimaryContainer,
    secondary = DeepForestSecondary,
    onSecondary = DeepForestOnSecondary,
    secondaryContainer = DeepForestSecondaryContainer,
    onSecondaryContainer = DeepForestOnSecondaryContainer,
    tertiary = DeepForestTertiary,
    onTertiary = DeepForestOnTertiary,
    background = DeepForestBackground,
    onBackground = DeepForestOnBackground,
    surface = DeepForestSurface,
    onSurface = DeepForestOnSurface,
    surfaceVariant = DeepForestSurfaceVariant,
    onSurfaceVariant = DeepForestOnSurfaceVariant,
    error = DeepForestError,
    onError = DeepForestOnError
)

private val LightColorScheme = lightColorScheme(
    primary = DeepForestPrimary,
    onPrimary = DeepForestOnPrimary,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = DeepForestPrimaryContainer,
    secondary = DeepForestSecondary,
    onSecondary = DeepForestOnSecondary,
    secondaryContainer = Color(0xFFD7CCC8),
    onSecondaryContainer = DeepForestSecondaryContainer,
    tertiary = DeepForestTertiary,
    onTertiary = DeepForestOnTertiary,
    background = Color(0xFFF1F8E9),
    onBackground = DarkGreen,
    surface = Color.White,
    onSurface = DarkGreen,
    surfaceVariant = Color(0xFFE0F2F1),
    onSurfaceVariant = Color(0xFF33691E),
    error = DeepForestError,
    onError = DeepForestOnError
)

@Composable
fun TrailCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}


