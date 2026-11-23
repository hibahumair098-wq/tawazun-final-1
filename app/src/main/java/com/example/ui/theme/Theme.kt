package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00A884),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF005C4B),
    onPrimaryContainer = Color(0xFFD1FADF),
    secondary = Color(0xFFE5C158),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF382E10),
    onSecondaryContainer = Color(0xFFE5C158),
    tertiary = Color(0xFF25D366),
    background = Color(0xFF0B141A),
    onBackground = Color(0xFFE9EDEF),
    surface = Color(0xFF111B21),
    onSurface = Color(0xFFE9EDEF),
    surfaceVariant = Color(0xFF202C33),
    onSurfaceVariant = Color(0xFF8696A0),
    outline = Color(0xFF2A3942),
    error = Color(0xFFEF5350),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TawazunEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = TawazunMintSoft,
    onPrimaryContainer = TawazunEmeraldDark,
    secondary = TawazunGold,
    onSecondary = Color.White,
    secondaryContainer = TawazunAmberSoft,
    onSecondaryContainer = Color(0xFF7A5511),
    tertiary = TawazunWhatsAppGreen,
    background = TawazunCream,
    onBackground = TawazunInk,
    surface = Color.White,
    onSurface = TawazunInk,
    surfaceVariant = TawazunSurfaceVariant,
    onSurfaceVariant = TawazunTextSecondary,
    outline = TawazunCardBorder,
    error = TawazunErrorRed,
    onError = Color.White
)

private val TawazunShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TawazunTheme(
    darkTheme: Boolean = false, // Warm cream light theme is the brand default
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

    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = TawazunShapes, content = content)
}

// Keep backwards-compatibility alias for template
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    TawazunTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
