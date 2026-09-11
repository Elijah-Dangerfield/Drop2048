package com.dangerfield.drop2048.libraries.telemetry.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.drop2048.AppEventListener
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import kotlin.time.Clock
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 17's day 1 / 3 / 7 return funnel.
 *
 * Every foreground asks how many whole days have passed since the first one
 * this install ever had, and emits `funnel.return_day` the first time that
 * number reaches 1, 3 or 7. The milestones already reported are persisted, so
 * a player who opens the app nine times on day 3 is one day-3 return rather
 * than nine — the funnel counts *players who came back*, and a per-foreground
 * event would count launches wearing a retention label.
 *
 * ### Reached-or-passed, not exactly-on
 *
 * A player who is away for five days and returns reports **both** day 1 and
 * day 3 on that foreground. That reads odd and it is the correct reading of
 * the question a retention curve asks, which is "were they still here by day
 * N", not "did they open the app on day N". Firing only on an exact match
 * would silently drop every player whose return skipped a milestone, and those
 * are disproportionately the lapsed players the curve exists to count.
 *
 * ### Day zero is not a milestone
 *
 * The install's own first day is not reported. `app.launched` already counts
 * it, and a "day 0 return" is a install, not a return.
 *
 * ### It is deliberately an `AppEventListener` and not `AutoInit`
 *
 * The listener set is dispatched on every foreground including the cold-boot
 * one, which is the moment the first-launch stamp needs to exist. A boot-time
 * constructor would have to reproduce the foreground it is trying to observe.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AppEventListener::class, multibinding = true)
@Inject
class RetentionReporter(
    private val appCache: AppCache,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
) : AppEventListener {

    private val logger = KLog.withTag("Retention")

    override fun onForeground(event: AppEvent.OnForeground) {
        appScope.launch { report() }
    }

    private suspend fun report() {
        val now = clock.now().toEpochMilliseconds()
        val data = Catching { appCache.get() }
            .logOnFailure { "Could not read the retention marker" }
            .getOrNull() ?: return

        if (data.firstLaunchAt == 0L) {
            Catching { appCache.update { it.copy(firstLaunchAt = now) } }
                .logOnFailure { "Could not stamp the first launch" }
            return
        }

        // A device whose clock has moved backwards reads as day zero rather
        // than as a negative day, so it reports nothing instead of reporting
        // a milestone it has no business reaching. Same direction as the ad
        // gate's install-grace rounding, and for the same reason: the
        // farmable direction is the one that hands something out.
        val day = ((now - data.firstLaunchAt).coerceAtLeast(0) / MillisPerDay).toInt()
        val due = MILESTONES.filter { it <= day && it !in data.returnDaysReported }
        if (due.isEmpty()) return

        Catching { appCache.update { it.copy(returnDaysReported = it.returnDaysReported + due) } }
            .logOnFailure { "Could not record the reported return days" }

        due.forEach { milestone ->
            logger.logEvent("funnel.return_day", "day" to milestone, "days_since_install" to day)
        }
    }

    private companion object {
        val MILESTONES = listOf(1, 3, 7)
        const val MillisPerDay = 24L * 60 * 60 * 1000
    }
}
