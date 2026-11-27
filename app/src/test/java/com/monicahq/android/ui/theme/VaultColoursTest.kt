package com.monicahq.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.monicahq.android.vault.VaultColour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every vault colour stays readable in both themes (FR-020, research R6). */
class VaultColoursTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (light, dark) = listOf(a.luminance().toDouble(), b.luminance().toDouble()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test
    fun `every colour has a pair in each theme`() {
        assertEquals(VaultColour.entries.toSet(), LightVaultColours.keys)
        assertEquals(VaultColour.entries.toSet(), DarkVaultColours.keys)
    }

    @Test
    fun `every initial is readable on its tint in light and in dark`() {
        for ((colour, pair) in LightVaultColours) {
            assertTrue("$colour light: ${contrast(pair.initial, pair.tint)}", contrast(pair.initial, pair.tint) >= 4.5)
        }
        for ((colour, pair) in DarkVaultColours) {
            assertTrue("$colour dark: ${contrast(pair.initial, pair.tint)}", contrast(pair.initial, pair.tint) >= 4.5)
        }
    }

    @Test
    fun `every swatch stands out from the page and the card`() {
        for ((colour, pair) in LightVaultColours) {
            assertTrue("$colour on light page", contrast(pair.initial, PageLight) >= 3.0)
            assertTrue("$colour on light card", contrast(pair.initial, CardLight) >= 3.0)
        }
        for ((colour, pair) in DarkVaultColours) {
            assertTrue("$colour on dark page", contrast(pair.initial, PageDark) >= 3.0)
            assertTrue("$colour on dark card", contrast(pair.initial, CardDark) >= 3.0)
        }
    }
}
