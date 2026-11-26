package com.monicahq.android.instance

import java.net.URI

/** The Monica instance the app talks to until the person chooses another one. */
const val DEFAULT_INSTANCE_URL = "https://app.monicahq.com"

/**
 * The address of an instance as people read it: without the scheme and the trailing slash, but
 * with the port and the path, since self-hosted instances often need them to be told apart.
 */
fun instanceHost(url: String): String = url.trim().substringAfter("://").trimEnd('/')

/**
 * Turns what a person typed into the address to store, or null when it is not an address the app
 * can use. Empty means the default instance; a missing scheme means `https://`. Plain `http` is
 * accepted because self-hosted instances on a home network often have no certificate.
 */
fun normalizeInstanceUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return DEFAULT_INSTANCE_URL
    if (trimmed.any { it.isWhitespace() }) return null

    val url = (if ("://" in trimmed) trimmed else "https://$trimmed").trimEnd('/')
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val isWeb = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
    return url.takeIf { isWeb && !uri.host.isNullOrEmpty() }
}
