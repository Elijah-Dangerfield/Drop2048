package com.dangerfield.drop2048.libraries.progress.daily

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * SPEC 14: which board today is, how many attempts are left on it, and how long
 * the streak is.
 *
 * **Nothing here is a counter.** The streak is folded out of `daily_result` on
 * every read, so there is no number on disk that a bug, a reinstall of an older
 * build or a device clock set to 1970 can leave wrong. The worst a strange clock
 * can do is make today resolve to a different day; the history it reads is
 * unchanged and the answer corrects itself the moment the clock does.
 *
 * Device-local like the rest of `:libraries:progress`. No server decides the
 * date and no server can be asked to arbitrate a streak, which is also why a
 * player who moves their clock forward can see tomorrow's board early. That is
 * accepted rather than defended against: the only real defence is a server clock,
 * and C9's platform leaderboards are where a contested score is arbitrated.
 */
interface DailyRepository {

    /**
     * The screen's live state. Re-emits when a result is written **and** when the
     * UTC day rolls over, so a screen left open across midnight picks up the new
     * board without watching the clock itself.
     */
    fun observe(): Flow<DailyStatus>

    suspend fun status(): DailyStatus

    /**
     * Spends one of today's attempts and hands back the seed to play it on.
     *
     * The attempt is spent **here**, before a block has fallen, and that is the
     * whole of one-attempt-per-day. Spending it on completion instead would make
     * force-quitting a bad run free, and the run is resumable from the saved run
     * store, so a player who leaves mid-attempt comes back to the same attempt
     * rather than losing it.
     *
     * The exception the player pays for: a Daily attempt abandoned and then
     * overwritten by starting an Endless run is gone, because the saved run store
     * holds one run. See `docs/todos.md`.
     */
    suspend fun startAttempt(): DailyAttempt

    /**
     * Banks a finished attempt against the day it was started on.
     *
     * [date] is a parameter rather than "now" for the reason
     * [DailyAttempt.Granted] gives: an attempt that crosses 00:00 UTC belongs to
     * the board it began on. Only the better of the day's scores is kept.
     */
    suspend fun recordAttempt(date: LocalDate, score: Long)

    /** SPEC 12's Daily retry placement: one rewarded ad, one more attempt. */
    suspend fun grantRetry(): DailyRetryResult

    /** Every day on record, oldest first. For the streak calendar and C9's share. */
    suspend fun history(): List<DailyResult>

    suspend fun reset()
}
