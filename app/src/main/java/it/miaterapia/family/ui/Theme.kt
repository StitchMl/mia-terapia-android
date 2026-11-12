package it.miaterapia.family.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Palette = lightColorScheme(
    primary = Color(0xFF0F766E), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5F0), onPrimaryContainer = Color(0xFF073B37),
    secondary = Color(0xFF4F635F), secondaryContainer = Color(0xFFDCE8E5),
    tertiary = Color(0xFF9A5B13), tertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFBA1A1A), background = Color(0xFFF7F9F8), surface = Color(0xFFF7F9F8),
    surfaceVariant = Color(0xFFE1E9E7), outline = Color(0xFF707976)
)
@Composable fun MiaTerapiaTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = Palette, content = content) }
