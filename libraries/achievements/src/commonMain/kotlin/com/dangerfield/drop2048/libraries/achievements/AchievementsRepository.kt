package com.dangerfield.drop2048.libraries.achievements

import kotlinx.coroutines.flow.Flow

/**
 * The player's achievement history, and the only thing allowed to decide what
 * has been earned.
 *
 * Callers report a finished run and are told what that just unlocked. They never
 * write an unlock themselves, which is what keeps "announce a badge exactly
 * once" in one place.
 *
 * Device-local, like everything else in this app: there are no accounts, so this
 * does not survive a reinstall and Settings says so.
 */
interface AchievementsRepository {

    /** Current counters and unlocks, re-emitted whenever either changes. */
    fun observe(): Flow<AchievementState>

    suspend fun state(): AchievementState

    /**
     * Records one finished run and returns what it unlocked, in catalog order.
     * Empty is the normal answer.
     *
     * Recording the same run twice is a no-op: outcomes are stored under
     * [RunOutcome.key], so a retry of a failed write cannot double-count.
     */
    suspend fun record(outcome: RunOutcome): List<Achievement>

    suspend fun reset()
}
