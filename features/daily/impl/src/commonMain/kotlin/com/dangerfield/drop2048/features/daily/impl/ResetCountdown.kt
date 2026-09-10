package com.dangerfield.drop2048.features.daily.impl

import androidx.compose.runtime.Composable
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.daily_resets_hours
import drop2048.libraries.resources.generated.resources.daily_resets_minutes
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * How long until the board rotates, in the largest unit that says anything.
 *
 * Rounded **down**, deliberately. "1m" with fifty seconds left is a promise the
 * screen can keep; "2m" with sixty-one seconds left is one it cannot, and the
 * player finds out by watching a countdown skip.
 *
 * Never shows seconds. The state it is rendered from is a single snapshot taken
 * when the status was resolved, so a seconds field would be visibly stale within
 * a second of the screen appearing — and a live one would mean recomposing the
 * whole page once a second for a number nobody is racing.
 */
@Composable
internal fun formatResetsIn(duration: Duration): String {
    val minutes = duration.inWholeMinutes
    return if (minutes >= MinutesPerHour) {
        stringResource(
            Res.string.daily_resets_hours,
            minutes / MinutesPerHour,
            minutes % MinutesPerHour,
        )
    } else {
        stringResource(Res.string.daily_resets_minutes, minutes)
    }
}

private const val MinutesPerHour = 60L
