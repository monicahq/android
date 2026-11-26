package com.monicahq.android.instance

/** The Monica instance the app talks to until the person chooses another one. */
const val DEFAULT_INSTANCE_URL = "https://app.monicahq.com"

/**
 * The address of an instance as people read it: without the scheme and the trailing slash, but
 * with the port and the path, since self-hosted instances often need them to be told apart.
 */
fun instanceHost(url: String): String = url.trim().substringAfter("://").trimEnd('/')
