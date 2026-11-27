package com.monicahq.android.welcome

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {
    private val style = SpanStyle(fontWeight = FontWeight.Medium)

    @Test
    fun `styles the part where it appears in the text`() {
        val result = highlight("Instance: app.monicahq.com", "app.monicahq.com", style)

        assertEquals("Instance: app.monicahq.com", result.text)
        assertEquals(1, result.spanStyles.size)
        assertEquals(10, result.spanStyles[0].start)
        assertEquals(26, result.spanStyles[0].end)
        assertEquals(style, result.spanStyles[0].item)
    }

    @Test
    fun `follows the part when a language moves it`() {
        val result = highlight("app.monicahq.com est l'instance", "app.monicahq.com", style)

        assertEquals(0, result.spanStyles[0].start)
        assertEquals(16, result.spanStyles[0].end)
    }

    @Test
    fun `leaves the text unstyled when the part is missing`() {
        val result = highlight("Instance", "app.monicahq.com", style)

        assertEquals("Instance", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `leaves the text unstyled when the part is empty`() {
        assertTrue(highlight("Instance: ", "", style).spanStyles.isEmpty())
    }
}
