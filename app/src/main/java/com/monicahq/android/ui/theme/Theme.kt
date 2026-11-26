package com.monicahq.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Monica's palette mapped onto Material 3 roles, so Material components pick the right colours.
// No dynamic colour: the design system has no accent colour to take from the wallpaper.
private val LightColors =
    lightColorScheme(
        primary = PrimaryLight,
        onPrimary = OnPrimaryLight,
        background = PageLight,
        onBackground = InkLight,
        surface = PageLight,
        onSurface = InkLight,
        surfaceVariant = CardLight,
        onSurfaceVariant = MutedLight,
        surfaceContainerLowest = PageLight,
        surfaceContainerLow = CardLight,
        surfaceContainer = CardLight,
        surfaceContainerHigh = CardLight,
        surfaceContainerHighest = CardLight,
        outline = HairlineStrongLight,
        outlineVariant = HairlineLight,
    )

private val DarkColors =
    darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        background = PageDark,
        onBackground = InkDark,
        surface = PageDark,
        onSurface = InkDark,
        surfaceVariant = CardDark,
        onSurfaceVariant = MutedDark,
        surfaceContainerLowest = PageDark,
        surfaceContainerLow = CardDark,
        surfaceContainer = CardDark,
        surfaceContainerHigh = CardDark,
        surfaceContainerHighest = CardDark,
        outline = HairlineStrongDark,
        outlineVariant = HairlineDark,
    )

@Composable
fun MonicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MonicaTypography,
        content = content,
    )
}
