package com.monicahq.android.instance

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/** The Monica instance the app talks to until the person chooses another one. A local test instance for now; my.monicahq.com later. */
const val DEFAULT_INSTANCE_URL = "https://monica.test"

/**
 * The address of an instance as people read it: without the scheme and the trailing slash, but
 * with the port and the path, since self-hosted instances often need them to be told apart.
 */
fun instanceHost(url: String): String = url.trim().substringAfter("://").trimEnd('/')

/**
 * Turns what a person typed into the address to store, or null when it is not an address the app
 * can use. Empty means the default instance; a missing scheme means `https://`. Scheme and host
 * are lowercased, the port and the path are kept, the trailing slash is removed. Addresses with
 * credentials, a query or a fragment are refused. Plain `http` is accepted only when [allowHttp]
 * is true, which only debug builds pass: release builds talk to instances over HTTPS only.
 */
fun normalizeInstanceUrl(
    input: String,
    allowHttp: Boolean,
): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return DEFAULT_INSTANCE_URL
    if (trimmed.any { it.isWhitespace() }) return null

    val uri = runCatching { URI(if ("://" in trimmed) trimmed else "https://$trimmed") }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase()
    val allowed = scheme == "https" || (scheme == "http" && allowHttp)
    if (!allowed || host.isNullOrEmpty() || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) {
        return null
    }
    val port = if (uri.port == -1) "" else ":${uri.port}"
    return "$scheme://$host$port${uri.rawPath.orEmpty().trimEnd('/')}"
}

/** True when [input] is an address a debug build would accept but a release build refuses. */
fun requiresHttps(
    input: String,
    allowHttp: Boolean,
): Boolean = !allowHttp && normalizeInstanceUrl(input, allowHttp = true)?.startsWith("http://") == true

/** True when [url] has the same scheme, host and port as [base] and sits under its path. */
fun isUnderBase(
    url: String,
    base: String,
): Boolean {
    val target = url.toHttpUrlOrNull() ?: return false
    val root = base.toHttpUrlOrNull() ?: return false
    val basePath = root.encodedPath.trimEnd('/')
    return target.sameOrigin(root) && (target.encodedPath == basePath || target.encodedPath.startsWith("$basePath/"))
}

/** True when both addresses have the same scheme, host and port. */
fun HttpUrl.sameOrigin(other: HttpUrl): Boolean = scheme == other.scheme && host == other.host && port == other.port
