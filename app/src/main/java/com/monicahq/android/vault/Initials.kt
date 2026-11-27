package com.monicahq.android.vault

import java.text.BreakIterator
import java.util.Locale

/**
 * The letter on a vault's square: the first character a person sees in its name, uppercased, as
 * the web does. Graphemes keep an accented letter or an emoji whole.
 */
fun vaultInitial(
    name: String,
    locale: Locale = Locale.getDefault(),
): String = firstGrapheme(name.trim())?.uppercase(locale).orEmpty()

/**
 * Up to two letters for the account button: the first of the first two words that start with a
 * letter ("Jean-Pierre Martin" → "JM"). Null when the name has none, so a person icon is shown.
 */
fun personInitials(
    name: String,
    locale: Locale = Locale.getDefault(),
): String? {
    val letters =
        name
            .split(Regex("\\s+"))
            .filter { it.firstOrNull()?.isLetter() == true }
            .take(2)
            .mapNotNull { firstGrapheme(it) }
    return letters.joinToString("").uppercase(locale).ifEmpty { null }
}

private fun firstGrapheme(text: String): String? {
    if (text.isEmpty()) return null
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    return text.substring(0, iterator.next())
}
