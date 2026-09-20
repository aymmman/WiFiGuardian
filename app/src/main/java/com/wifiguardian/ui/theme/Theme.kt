package com.wifiguardian.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(primary = Color(0xFF7DD3FC), secondary = Color(0xFF94A3B8), tertiary = Color(0xFF34D399))
private val LightColors = lightColorScheme(primary = Color(0xFF075985), secondary = Color(0xFF334155), tertiary = Color(0xFF047857))

@Composable
fun WiFiGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, typography = Typography(), content = content)
}
