package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NanopunkColorScheme = darkColorScheme(
    primary = NanoCyan,
    onPrimary = VoidDark,
    primaryContainer = SlateCard,
    onPrimaryContainer = NanoCyan,
    secondary = NanoPurple,
    onSecondary = VoidDark,
    tertiary = PlasmaPink,
    onTertiary = VoidDark,
    background = VoidDark,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = LaserRed
)

@Composable
fun NanoMarshalTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NanopunkColorScheme,
        typography = Typography,
        content = content
    )
}
