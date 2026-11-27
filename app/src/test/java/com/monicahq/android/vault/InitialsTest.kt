package com.monicahq.android.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class InitialsTest {
    @Test
    fun `a vault's initial is its first letter, uppercased`() {
        assertEquals("P", vaultInitial("personal"))
        assertEquals("F", vaultInitial("  Family"))
    }

    @Test
    fun `an accented letter stays whole`() {
        // "é" written as "e" followed by a combining accent.
        assertEquals("É", vaultInitial("école"))
        assertEquals("É", vaultInitial("école"))
    }

    @Test
    fun `an emoji stays whole`() {
        assertEquals("👨‍👩‍👧", vaultInitial("👨‍👩‍👧 Family"))
    }

    @Test
    fun `uppercasing follows the locale`() {
        assertEquals("İ", vaultInitial("istanbul", Locale.forLanguageTag("tr")))
    }

    @Test
    fun `an empty name has no initial`() {
        assertEquals("", vaultInitial("   "))
    }

    @Test
    fun `a person's initials are the first letters of their first two words`() {
        assertEquals("JD", personInitials("Jane Doe"))
        assertEquals("J", personInitials("jane"))
        assertEquals("JM", personInitials("Jean-Pierre Martin Dupont"))
        assertEquals("ÉZ", personInitials("élodie  zola"))
    }

    @Test
    fun `words that do not start with a letter are skipped`() {
        assertEquals("JD", personInitials("— Jane Doe"))
        assertEquals("D", personInitials("123 Doe"))
    }

    @Test
    fun `a name without letters has no initials`() {
        assertNull(personInitials(""))
        assertNull(personInitials("   "))
        assertNull(personInitials("123 !!"))
    }
}
