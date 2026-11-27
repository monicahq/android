package com.monicahq.android

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import java.util.Locale

/** A node TalkBack announces when it changes. */
fun isLiveRegion(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)

/** Shows [content] with the resources of [locale], without changing the device's language. */
@Composable
internal fun WithLocale(
    locale: Locale?,
    content: @Composable () -> Unit,
) {
    if (locale == null) return content()
    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
    // Only the resources change: the real context is kept, since windows such as bottom sheets
    // need the activity's.
    CompositionLocalProvider(
        LocalResources provides context.createConfigurationContext(configuration).resources,
        LocalConfiguration provides configuration,
        content = content,
    )
}

/** Shows [content] as with the system font size set to [fontScale]. */
@Composable
internal fun WithFontScale(
    fontScale: Float?,
    content: @Composable () -> Unit,
) {
    if (fontScale == null) return content()
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale),
        content = content,
    )
}
