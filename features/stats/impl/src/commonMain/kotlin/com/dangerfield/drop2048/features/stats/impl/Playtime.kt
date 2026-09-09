package com.dangerfield.drop2048.features.stats.impl

import androidx.compose.runtime.Composable
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.stats_duration_hours
import drop2048.libraries.resources.generated.resources.stats_duration_minutes
import drop2048.libraries.resources.generated.resources.stats_duration_seconds
import org.jetbrains.compose.resources.stringResource

/**
 * Lifetime playtime, in the largest unit that says something.
 *
 * Three formats rather than one `h:mm:ss`, because this number starts at a few
 * seconds on the first run and ends in the tens of hours, and `00:00:41` reads
 * like a stopwatch that has not started. Seconds are dropped as soon as there is
 * a minute to show — nobody reads the seconds of a lifetime total, and keeping
 * them makes the value change while the page is open.
 */
@Composable
fun formatPlaytime(millis: Long): String {
    val totalMinutes = millis / MillisPerMinute
    val hours = totalMinutes / MinutesPerHour
    val minutes = totalMinutes % MinutesPerHour
    return when {
        hours > 0 -> stringResource(Res.string.stats_duration_hours, hours, minutes)
        totalMinutes > 0 -> stringResource(Res.string.stats_duration_minutes, minutes)
        else -> stringResource(Res.string.stats_duration_seconds, millis / MillisPerSecond)
    }
}

private const val MillisPerSecond = 1_000L
private const val MillisPerMinute = 60_000L
private const val MinutesPerHour = 60L
