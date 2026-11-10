package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TawazunEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = TawazunEmeraldDark,
    onPrimaryContainer = Color(0xFFD1FADF),
    secondary = TawazunGold,
    onSecondary = Color.Black,
    secondaryContainer = TawazunGoldContainer,
    onSecondaryContainer = TawazunGold,
    tertiary = TawazunWhatsAppGreen,
    background = TawazunTealBg,
    onBackground = TawazunTextPrimary,
    surface = TawazunSurface,
    onSurface = TawazunTextPrimary,
    surfaceVariant = TawazunSurfaceVariant,
    onSurfaceVariant = TawazunTextSecondary,
    outline = TawazunCardBorder,
    error = TawazunErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TawazunLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FADF),
    onPrimaryContainer = TawazunEmeraldDark,
    secondary = TawazunGold,
    onSecondary = Color.Black,
    background = TawazunLightBg,
    onBackground = Color(0xFF111B21),
    surface = TawazunLightSurface,
    onSurface = Color(0xFF111B21),
    surfaceVariant = Color(0xFFE9EDEF),
    onSurfaceVariant = Color(0xFF54656F),
    outline = Color(0xFFD1D7DB),
    error = TawazunErrorRed,
    onError = Color.White
)

@Composable
fun TawazunTheme(
    darkTheme: Boolean = true, // Default to sleek dark mode for dashboard/night usage
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

// Keep backwards-compatibility alias for template
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    TawazunTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
