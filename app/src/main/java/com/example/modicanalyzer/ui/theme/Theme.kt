package com.example.modicanalyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.example.modicanalyzer.ui.theme.*

private val LightColorScheme = lightColorScheme(
    primary = ModicarePrimary,
    secondary = ModicareSecondary,
    tertiary = ModicareAccent,
    background = ModicareBackground,
    surface = ModicareSurface,
    onPrimary = TextPrimary,
    onSecondary = TextSecondary,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun ModicAnalyzerTheme(
    darkTheme: Boolean = false, // Force light theme only
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Always use light color scheme - ignore dark theme preference
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicLightColorScheme(context) // Always use light scheme even for dynamic colors
        }
        else -> LightColorScheme // Always use light color scheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}