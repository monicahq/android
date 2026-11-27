package com.monicahq.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.monicahq.android.vault.VaultColour

/** How a vault colour is drawn: the initial (also the swatch of the form) on its tinted square. */
data class VaultColourPair(
    val initial: Color,
    val tint: Color,
)

/**
 * Measured pairs, each initial at least 4.5:1 on its tint (specs/003-vaults-list/research.md, R6).
 * Light: the web's 12% tint on white; green, ochre and teal are darkened just enough to pass.
 * Dark: the colour lifted toward white, on a 20% tint over the dark card.
 */
internal val LightVaultColours: Map<VaultColour, VaultColourPair> =
    mapOf(
        VaultColour.Blue to VaultColourPair(Color(0xFF3F5FD0), Color(0xFFE8ECF9)),
        VaultColour.Green to VaultColourPair(Color(0xFF447740), Color(0xFFEAF1E9)),
        VaultColour.Ochre to VaultColourPair(Color(0xFF8D6122), Color(0xFFF3EDE5)),
        VaultColour.Rust to VaultColourPair(Color(0xFF9A3F2B), Color(0xFFF3E8E6)),
        VaultColour.Violet to VaultColourPair(Color(0xFF6B4FA8), Color(0xFFEDEAF5)),
        VaultColour.Teal to VaultColourPair(Color(0xFF2C747B), Color(0xFFE6F0F0)),
        VaultColour.Rose to VaultColourPair(Color(0xFFA8447A), Color(0xFFF5E9EF)),
        VaultColour.Graphite to VaultColourPair(Color(0xFF4A4A52), Color(0xFFE9E9EA)),
    )

internal val DarkVaultColours: Map<VaultColour, VaultColourPair> =
    mapOf(
        VaultColour.Blue to VaultColourPair(Color(0xFF97A9E6), Color(0xFF333F5B)),
        VaultColour.Green to VaultColourPair(Color(0xFF8EB48B), Color(0xFF314249)),
        VaultColour.Ochre to VaultColourPair(Color(0xFFC2A67C), Color(0xFF3C3F46)),
        VaultColour.Rust to VaultColourPair(Color(0xFFCC9F95), Color(0xFF3E3D4B)),
        VaultColour.Violet to VaultColourPair(Color(0xFFB2A3D2), Color(0xFF383E57)),
        VaultColour.Teal to VaultColourPair(Color(0xFF82B2B6), Color(0xFF2F4151)),
        VaultColour.Rose to VaultColourPair(Color(0xFFD09AB7), Color(0xFF3E3C51)),
        VaultColour.Graphite to VaultColourPair(Color(0xFFAAAAAE), Color(0xFF374050)),
    )

/**
 * The pair for the theme in use. Read from the theme rather than the system setting, so a screen
 * shown in [MonicaTheme] with an explicit `darkTheme` (previews, tests) gets the matching pairs.
 */
@Composable
@ReadOnlyComposable
fun VaultColour.colours(): VaultColourPair {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return (if (dark) DarkVaultColours else LightVaultColours).getValue(this)
}
