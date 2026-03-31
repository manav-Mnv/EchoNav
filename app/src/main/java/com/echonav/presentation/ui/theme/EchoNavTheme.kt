package com.echonav.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * EchoNav color palette — dark-first, minimal, OLED-friendly.
 *
 * Cyan accent (#00E5FF) is chosen because:
 *   - High contrast on dark backgrounds
 *   - Visible across most color blindness types
 *   - Calm and non-alarming for ambient use
 */
private val EchoNavColorScheme = darkColorScheme(
    primary          = Color(0xFF00E5FF), // Cyan
    onPrimary        = Color(0xFF000000),
    secondary        = Color(0xFF78909C), // Cool grey
    onSecondary      = Color(0xFFFFFFFF),
    background       = Color(0xFF0A0A0A), // Near-black
    onBackground     = Color(0xFFECEFF1),
    surface          = Color(0xFF121212),
    onSurface        = Color(0xFFB0BEC5),
    error            = Color(0xFFEF5350),
    onError          = Color(0xFFFFFFFF)
)

@Composable
fun EchoNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EchoNavColorScheme,
        content     = content
    )
}
