package com.trailcam.mesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Forest-inspired color palette
private val ForestGreen = Color(0xFF2D5A3D)
private val DarkGreen = Color(0xFF1A3D2A)
private val LightGreen = Color(0xFF4A7C59)
private val Bark = Color(0xFF5D4037)
private val Cream = Color(0xFFF5F5DC)
private val AlertOrange = Color(0xFFE65100)
private val AlertRed = Color(0xFFD32F2F)

private val DarkColorScheme = darkColorScheme(
    primary = LightGreen,
    onPrimary = Color.White,
    primaryContainer = ForestGreen,
    onPrimaryContainer = Cream,
    secondary = Bark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3E2723),
    onSecondaryContainer = Cream,
    tertiary = AlertOrange,
    onTertiary = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFCACACA),
    error = AlertRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = Color.White,
    primaryContainer = LightGreen,
    onPrimaryContainer = Color.White,
    secondary = Bark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7CCC8),
    onSecondaryContainer = Bark,
    tertiary = AlertOrange,
    onTertiary = Color.White,
    background = Cream,
    onBackground = DarkGreen,
    surface = Color.White,
    onSurface = DarkGreen,
    surfaceVariant = Color(0xFFE8E8E0),
    onSurfaceVariant = Color(0xFF4A4A4A),
    error = AlertRed,
    onError = Color.White
)

@Composable
fun TrailCamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}


