package com.dangerfield.drop2048.libraries.progress.daily

import kotlinx.datetime.LocalDate

/**
 * One UTC day of the Daily Challenge, exactly the columns SPEC 11 lists.
 *
 * [seed] is stored rather than recomputed. `dailySeedFor` is a pure function of
 * the date and is not supposed to move, but a stored score that no longer names
 * the board it was scored on is worse than no score at all — and this row is the
 * only witness a device has, because there are no accounts.
 *
 * [attemptsUsed] counts attempts **started**, not finished. Counting finishes
 * would make force-quitting a bad run a free reroll, which is the one thing
 * one-attempt-per-day exists to prevent. See [DailyRepository.startAttempt].
 *
 * [score] is the best of the day's attempts. SPEC 14 says the mode is scored on
 * final score, meaning the score a run ends on rather than some other in-run
 * measure; it does not say which of two attempts counts, and taking the better
 * one is the only reading under which paying for a retry is worth anything.
 *
 * [completed] is whether any attempt of the day actually finished. It is the
 * difference between a day in progress and a day spent, and only a completed day
 * counts toward the streak.
 *
 * [retriesUsed] is the one column SPEC 11 does not list, and it is not optional:
 * SPEC 12 caps rewarded Daily retries per day, and a cap that is not counted on
 * disk is a cap a force-quit resets. Attempts allowed is
 * `(Pro ? 2 : 1) + retriesUsed`, so this is also the only reason the allowance
 * survives a restart.
 */
data class DailyResult(
    val date: LocalDate,
    val seed: Long,
    val score: Long,
    val attemptsUsed: Int,
    val completed: Boolean,
    val retriesUsed: Int = 0,
)
