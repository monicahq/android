package com.monicahq.android.ui

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.monicahq.android.R
import java.util.concurrent.TimeUnit

/**
 * Says that what the screen shows is the copy kept on the phone, not the instance's latest answer,
 * and since when ("Not up to date. Last updated 3 hours ago."). Every screen reading offline uses
 * it (constitution, principle VIII). Words carry the meaning; the icon only accompanies them.
 *
 * The screen keeps it composed even when [staleSince] is null: TalkBack announces a live region
 * whose content changes, not one that appears.
 */
@Composable
fun StaleNotice(
    staleSince: Long?,
    modifier: Modifier = Modifier,
    /** Applied only when the line shows, so an up-to-date screen keeps no empty gap. */
    contentPadding: PaddingValues = PaddingValues(),
    now: Long = System.currentTimeMillis(),
) {
    Box(modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        if (staleSince == null) return@Box
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp).size(16.dp),
            )
            Text(
                text = stringResource(R.string.stale_notice, relativeTime(staleSince, now)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "3 hours ago" in the language the screen is shown in, so the sentence never mixes two languages. */
@Composable
private fun relativeTime(
    since: Long,
    now: Long,
): String {
    val formatter = RelativeDateTimeFormatter.getInstance(LocalConfiguration.current.locales[0])
    val minutes = TimeUnit.MILLISECONDS.toMinutes((now - since).coerceAtLeast(0))
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> formatter.format(Direction.PLAIN, AbsoluteUnit.NOW)
        hours < 1 -> formatter.format(minutes.toDouble(), Direction.LAST, RelativeUnit.MINUTES)
        days < 1 -> formatter.format(hours.toDouble(), Direction.LAST, RelativeUnit.HOURS)
        else -> formatter.format(days.toDouble(), Direction.LAST, RelativeUnit.DAYS)
    }
}
