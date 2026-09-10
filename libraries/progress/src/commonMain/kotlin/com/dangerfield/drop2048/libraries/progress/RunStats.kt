package com.dangerfield.drop2048.libraries.progress

/**
 * Every number on the stats page (SPEC 15), folded out of `run_record`.
 *
 * Nothing here is stored. A stored tally has no witness: if a crash between two
 * writes, or a bug in one call site, leaves it wrong, nothing on the device can
 * tell it is wrong, and progress is device-local so there is no server copy to
 * rebuild from. A fold makes every past bug retroactively fixable — ship the fix
 * and the number is right on the next read.
 *
 * [bestScore] is the same rule stated once more, because it is the one that has
 * already been got wrong here: it is `max(score)` over these rows and lives
 * nowhere else. `AppData` held a copy through C3 and C4 deleted it.
 *
 * It is also the one number on the page that does **not** count every row.
 * Decision D19 rules that a Daily score cannot own the all-time best, so
 * [bestScore] folds Endless rows only while everything beside it stays a
 * lifetime total. The screen has to say so, or the page contradicts itself: a
 * player can see fourteen runs played and a best lower than a Daily they
 * remember.
 *
 * Daily streak is deliberately absent. It needs `daily_result`, which is C6's.
 */
data class RunStats(
    val runsPlayed: Int = 0,
    val bestScore: Long = 0,
    val averageScore: Long = 0,
    val highestTier: Int = 0,
    val totalMerges: Long = 0,
    val totalBlocksPlaced: Long = 0,
    val longestCascade: Int = 0,
    val mostBurstsInARun: Int = 0,
    val lifetimeBursts: Long = 0,
    val totalPlaytimeMs: Long = 0,
    /** Most recent first. Feeds the score-history chart, not a stat of its own. */
    val recentScores: List<Long> = emptyList(),
) {
    val hasRuns: Boolean get() = runsPlayed > 0

    companion object {
        val Empty = RunStats()

        /** How many runs the score chart draws. Chosen to fit a phone width. */
        const val RecentRunCount = 10
    }
}

/**
 * The fold. [records] is the whole table; order does not matter except to
 * [RunStats.recentScores], which reads [RunRecord.endedAt].
 *
 * The average is truncated rather than rounded because it sits next to a best
 * score and a total, and a mean that reads higher than every run it averages is
 * the kind of small wrongness that makes a player distrust the rest of the page.
 */
fun statsFrom(records: List<RunRecord>): RunStats {
    if (records.isEmpty()) return RunStats.Empty
    val totalScore = records.sumOf { it.score }
    return RunStats(
        runsPlayed = records.size,
        bestScore = records.filter { it.mode == GameMode.ENDLESS }.maxOfOrNull { it.score } ?: 0,
        averageScore = totalScore / records.size,
        highestTier = records.maxOf { it.highestTier },
        totalMerges = records.sumOf { it.merges.toLong() },
        totalBlocksPlaced = records.sumOf { it.blocksPlaced.toLong() },
        longestCascade = records.maxOf { it.longestCascade },
        mostBurstsInARun = records.maxOf { it.bursts },
        lifetimeBursts = records.sumOf { it.bursts.toLong() },
        totalPlaytimeMs = records.sumOf { it.durationMs },
        recentScores = records.sortedByDescending { it.endedAt }
            .take(RunStats.RecentRunCount)
            .map { it.score },
    )
}
