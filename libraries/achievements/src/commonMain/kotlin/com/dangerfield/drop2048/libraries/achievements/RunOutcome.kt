package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.RunRecord

/**
 * The raw record of one finished run, from the achievement system's point of
 * view: `run_record` (SPEC 11) plus [RunFacts] plus the Daily streak the run
 * landed on.
 *
 * This — not a set of counters — is what gets stored, append-only. Keeping the
 * facts is what makes every number in [AchievementCounters] re-derivable: a
 * badge added in a later release back-fills from a player's history the first
 * time they finish anything, because the fold runs over the whole log rather
 * than over a tally somebody has to remember to migrate.
 *
 * Every field is something the game already knows at the moment a run ends.
 * Nothing is a projection and nothing is a running total — streaks and
 * high-water marks are the *fold's* business, so a reinstall can never hand the
 * store a pre-computed best and clobber a real one.
 *
 * [dailyStreakDays] is deliberately reported rather than derived here. The
 * streak is folded out of `daily_result` by `:libraries:progress`, and a second
 * fold over the dates in this log would quietly disagree with the number on the
 * Daily card. One owner, watermarked here.
 */
data class RunOutcome(
    val mode: GameMode,
    val score: Long,
    val level: Int,
    val blocksPlaced: Int,
    val durationMs: Long,
    val highestTier: Int,
    val longestCascade: Int,
    val bursts: Int,
    val merges: Int,
    val facts: RunFacts,
    /** Zero for an Endless run and for a Daily that broke the streak. */
    val dailyStreakDays: Int = 0,
    /** Epoch millis the run ended. Also the timestamp a badge it unlocks gets. */
    val endedAt: Long,
) {
    /**
     * Stable identity for this run, so recording it twice is a no-op rather than
     * a double count. Two runs cannot end in the same millisecond in the same
     * mode, and a run replayed from the log carries its original timestamp.
     */
    val key: String get() = "${mode.name}:$endedAt"
}

/**
 * The fact a finished run produces, assembled from the row `run_record` already
 * got and the two things it does not hold.
 *
 * An extension rather than a constructor parameter list at the call site,
 * because the call site is `GameViewModel.endRun` and the one thing that must
 * not happen there is the two records disagreeing: a badge for a 10-step cascade
 * and a stats page reporting 9 would be the same bug Sodogku's second scoring
 * channel was.
 */
fun RunRecord.outcomeWith(facts: RunFacts, dailyStreakDays: Int = 0): RunOutcome = RunOutcome(
    mode = mode,
    score = score,
    level = level,
    blocksPlaced = blocksPlaced,
    durationMs = durationMs,
    highestTier = highestTier,
    longestCascade = longestCascade,
    bursts = bursts,
    merges = merges,
    facts = facts,
    dailyStreakDays = dailyStreakDays,
    endedAt = endedAt,
)
