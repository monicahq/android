package com.monicahq.android.welcome

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/** Styles [part] inside [text]. Each language places the host where its grammar wants it. */
internal fun highlight(
    text: String,
    part: String,
    style: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        val start = text.indexOf(part)
        if (part.isNotEmpty() && start >= 0) addStyle(style, start, start + part.length)
    }
