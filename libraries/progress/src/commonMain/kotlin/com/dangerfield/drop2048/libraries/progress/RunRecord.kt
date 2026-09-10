package com.dangerfield.drop2048.libraries.progress

import kotlinx.serialization.Serializable

/**
 * Which set of rules a run was played under.
 *
 * Daily shares `run_record` rather than getting a table of its own — the stats in
 * SPEC 15 are lifetime numbers and a second table would mean every one of them
 * became a union. `daily_result` holds what is true about a *day*; this column
 * holds what is true about a *run*.
 *
 * `@Serializable` for two reasons that both had to be satisfied at once: it is a
 * field of the saved-run blob, and it is a `GameRoute` argument. Native has no
 * built-in enum NavType, so a route enum is resolved through the destination's
 * typeMap and a plain enum there fails graph construction with a message naming
 * the wrong argument. See `baseRouteTypeMap`.
 */
@Serializable
enum class GameMode { ENDLESS, DAILY }

/**
 * One completed run, exactly as SPEC 11 lists it.
 *
 * The table is the stats page and the analytics backup, which is why the row
 * carries the seed: a run that produced a surprising number can be replayed
 * byte for byte from it (SPEC 4.1), and no other record of it survives.
 *
 * Two fields are worth their own note.
 *
 * [highestTier] is the tier **reached during the run**, never what was on the
 * board when it ended (decision D7). A 2048 bursts its own row, so the literal
 * reading reports zero of the game's defining moment.
 *
 * [cause] is `DeathCause.name` from `:libraries:cascade`, carried as a string so
 * this module does not depend on the engine and so renaming an engine enum
 * cannot silently retype a stored column. Rows written by an older build keep
 * the name they were written with.
 *
 * [merges] is not in SPEC 11's column list and is needed by SPEC 15's "total
 * merges". It is stored per-run rather than as a lifetime counter for the same
 * reason as everything else here: a counter has no witness, and a fold over the
 * rows is retroactively fixable.
 */
data class RunRecord(
    val id: Long = 0,
    /** Epoch millis the run ended. Ordering key for "recent runs". */
    val endedAt: Long,
    val score: Long,
    val level: Int,
    val blocksPlaced: Int,
    /**
     * Milliseconds the run was actually being played.
     *
     * Wall time between start and end would count a run left paused overnight as
     * eight hours, and SPEC 15 shows the sum of these as "total playtime". Time
     * spent paused or backgrounded is excluded.
     */
    val durationMs: Long,
    val highestTier: Int,
    val cause: String,
    val longestCascade: Int,
    val bursts: Int,
    val merges: Int,
    val mode: GameMode,
    val seed: Long,
)
