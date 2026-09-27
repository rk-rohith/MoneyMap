package com.moneymap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build

private val Light = lightColorScheme(
    primary = Color(0xFF0F6B43),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0CF),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4E6356),
    secondaryContainer = Color(0xFFD1E8D8),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF7FBF6),
    surface = Color(0xFFF7FBF6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7DDBA7),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005231),
    onPrimaryContainer = Color(0xFFB7F0CF),
    secondary = Color(0xFFB5CCBC),
    secondaryContainer = Color(0xFF374B3F),
    tertiary = Color(0xFFFFB950),
    tertiaryContainer = Color(0xFF693F00),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF101411),
    surface = Color(0xFF101411),
)

/** Colours for money going in / out that work in both themes. */
object MoneyColors {
    val positive: Color
        @Composable @ReadOnlyComposable get() = if (isSystemInDarkTheme()) Color(0xFF7DDBA7) else Color(0xFF0F6B43)
    val negative: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
}

/** Material You (wallpaper colours) needs Android 12+. */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun MoneyMapTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) Dark else Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
