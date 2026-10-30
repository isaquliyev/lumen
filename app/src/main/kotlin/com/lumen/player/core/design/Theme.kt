package com.lumen.player.core.design

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalAccent = compositionLocalOf { DefaultAccent }

private fun colorScheme(accent: Accent) = (if (LumenColors.light) lightColorScheme() else darkColorScheme()).copy(
    primary = accent.accent,
    onPrimary = LumenColors.ink,
    secondary = LumenColors.paper2,
    background = LumenColors.ink,
    onBackground = LumenColors.paper,
    surface = LumenColors.ink2,
    onSurface = LumenColors.paper,
    surfaceVariant = LumenColors.ink3,
    onSurfaceVariant = LumenColors.paper2,
    surfaceContainer = LumenColors.ink2,
    surfaceContainerHigh = LumenColors.ink3,
    surfaceContainerHighest = LumenColors.ink3,
    surfaceContainerLow = LumenColors.ink2,
    outline = LumenColors.line,
    outlineVariant = LumenColors.line,
    error = LumenColors.danger,
    inverseSurface = LumenColors.paper,
    inverseOnSurface = LumenColors.ink,
)

private val typography = Typography(
    titleLarge = LumenType.title,
    titleMedium = LumenType.body,
    bodyLarge = LumenType.body,
    bodyMedium = LumenType.label,
    bodySmall = LumenType.label,
    labelLarge = LumenType.label,
    labelMedium = LumenType.label,
    labelSmall = LumenType.overline,
)

@Composable
fun LumenTheme(reduceMotion: Boolean = false, accent: Accent = DefaultAccent, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme(accent), typography = typography) {
        CompositionLocalProvider(
            LocalAccent provides accent,
            LocalReduceMotion provides reduceMotion,
            LocalContentColor provides LumenColors.paper,
            content = content,
        )
    }
}
