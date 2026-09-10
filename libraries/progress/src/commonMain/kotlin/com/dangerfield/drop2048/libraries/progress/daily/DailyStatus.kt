package com.dangerfield.drop2048.libraries.progress.daily

import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * Everything the Daily screen draws, resolved for one UTC day.
 *
 * The screen does no date arithmetic of its own. [date], [seed] and [resetsIn]
 * all come from one snapshot of the clock, so a card built from one status
 * cannot show today's date beside tomorrow's board.
 */
data class DailyStatus(
    val date: LocalDate,
    val seed: Long,
    /** Today's row, or null while the day is untouched. */
    val result: DailyResult?,
    val streak: DailyStreak,
    /**
     * How many attempts today allows: one, two for Pro (SPEC 12), plus one for
     * each rewarded retry already taken.
     */
    val attemptsAllowed: Int,
    /**
     * Whether a rewarded retry is on the table right now — the day is spent, the
     * per-day cap has room, and the player is not Pro-with-attempts-left.
     *
     * False while no ad system exists (C10), which is why the screen must treat
     * it as a state rather than as a button it can always draw.
     */
    val retryOffered: Boolean,
    /** Until 00:00 UTC. Rendered against the player's own clock. */
    val resetsIn: Duration,
    /** `feature.dailyChallenge`. Off means no entry point, not a broken screen. */
    val enabled: Boolean,
) {
    val attemptsUsed: Int get() = result?.attemptsUsed ?: 0

    val attemptsRemaining: Int get() = (attemptsAllowed - attemptsUsed).coerceAtLeast(0)

    /** A day that has been played through at least once. */
    val completed: Boolean get() = result?.completed == true

    /**
     * An attempt was started and never finished. The run is still in the saved
     * run store, so the screen offers to go back to it rather than to start
     * another one.
     */
    val inProgress: Boolean get() = attemptsUsed > 0 && result?.completed != true

    val playable: Boolean get() = enabled && attemptsRemaining > 0
}

/**
 * SPEC 15's "Daily streak current and best", and SPEC 14's rewards at 3, 7, 14
 * and 30 days.
 *
 * Neither number is stored. Both are folded out of `daily_result` on every read,
 * for the reason [com.dangerfield.drop2048.libraries.progress.RunStats] states:
 * a stored counter has no witness, and progress is device-local so there is no
 * server copy to rebuild it from.
 */
data class DailyStreak(
    val current: Int = 0,
    val best: Int = 0,
) {
    /**
     * The milestone [current] has just landed on, or null. Non-null on exactly
     * the day the streak reaches it, which is when the screen celebrates.
     */
    val milestone: Int? get() = Milestones.firstOrNull { it == current }

    /** The next milestone ahead, or null once the last one is behind them. */
    val nextMilestone: Int? get() = Milestones.firstOrNull { it > current }

    companion object {
        val Empty = DailyStreak()

        /** SPEC 14's reward days, ascending. */
        val Milestones = listOf(3, 7, 14, 30)
    }
}

/** The attempt [DailyRepository.startAttempt] handed out, or why it did not. */
sealed interface DailyAttempt {

    /**
     * Go and play. [seed] is what the run must be started from and [date] is the
     * day the result belongs to — passed back to
     * [DailyRepository.recordAttempt] rather than re-read from the clock,
     * because a run started at 23:58 UTC and finished at 00:01 belongs to the
     * board it was started on.
     */
    data class Granted(val date: LocalDate, val seed: Long, val attemptNumber: Int) : DailyAttempt

    /** Today is spent and no retry has been bought. */
    data object NoAttemptsLeft : DailyAttempt

    /** `feature.dailyChallenge` is off. */
    data object Disabled : DailyAttempt
}

/** Outcome of trading a rewarded ad for one more attempt (SPEC 12). */
sealed interface DailyRetryResult {

    data class Granted(val attemptsAllowed: Int) : DailyRetryResult

    /** No ad system yet, or the network had no fill. Never the player's fault. */
    data object Unavailable : DailyRetryResult

    /** The player closed the ad early. The only outcome that withholds the retry. */
    data object Declined : DailyRetryResult

    /** `ads.rewarded.dailyRetriesPerDay` is spent for today. */
    data object NoneLeft : DailyRetryResult

    /** The day is not spent, so there is nothing to buy. */
    data object NothingToRetry : DailyRetryResult
}
