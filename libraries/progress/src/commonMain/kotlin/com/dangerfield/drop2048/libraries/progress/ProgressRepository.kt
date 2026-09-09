package com.dangerfield.drop2048.libraries.progress

import kotlinx.coroutines.flow.Flow

/**
 * The player's run history, and the only thing allowed to answer "what is my
 * best score".
 *
 * Callers report a finished run and read derived numbers back. Nothing writes a
 * best, an average or a lifetime total, because nothing is allowed to hold one
 * (see [RunStats]).
 *
 * Device-local by design. There are no accounts (`docs/decisions.md`), so this
 * does not survive a reinstall.
 */
interface ProgressRepository {

    /** Appends a completed run. Rows are never updated. */
    suspend fun record(run: RunRecord)

    /** Emits the full fold on every write, starting with the current one. */
    fun observeStats(): Flow<RunStats>

    /**
     * The best score across every run, or 0 before the first one.
     *
     * Its own query rather than `observeStats().first().bestScore` because the
     * HUD asks for it at the top of every run and does not want the rest of the
     * table read to answer it.
     */
    suspend fun bestScore(): Long
}
