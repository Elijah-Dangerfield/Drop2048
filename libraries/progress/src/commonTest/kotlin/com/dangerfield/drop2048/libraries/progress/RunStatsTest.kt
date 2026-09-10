package com.dangerfield.drop2048.libraries.progress

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPEC 15, hand-counted.
 *
 * Every number here was worked out from the four runs in [threeRuns] on paper
 * before the fold was written. That is the point of the file: the fold is the
 * only thing in the app that decides what a stat means, so the numbers it
 * produces are checked against arithmetic done somewhere else.
 */
class RunStatsTest {

    @Test
    fun noRuns_isAllZeroes() {
        assertEquals(RunStats.Empty, statsFrom(emptyList()))
    }

    /**
     * Decision D19, on the stats page: a Daily run feeds every lifetime total on
     * it and cannot own the best score.
     *
     * The two halves are the same 40,000-point run recorded under two modes, so
     * the only variable is the mode. Without the second half this would pass on a
     * fold that had dropped the row entirely, which would take `runsPlayed` and
     * the whole Lifetime card with it.
     */
    @Test
    fun bestScore_countsEndlessRunsOnly() {
        val endless = threeRuns.first().copy(score = 1_000, mode = GameMode.ENDLESS)
        val daily = threeRuns.first().copy(score = 40_000, mode = GameMode.DAILY)

        val withDaily = statsFrom(listOf(endless, daily))
        assertEquals(1_000, withDaily.bestScore)
        assertEquals(2, withDaily.runsPlayed, "the Daily still counts everywhere else")
        assertEquals(20_500, withDaily.averageScore)

        assertEquals(
            40_000,
            statsFrom(listOf(endless, daily.copy(mode = GameMode.ENDLESS))).bestScore,
            "the same score in Endless does take the best",
        )
    }

    /** A player whose only runs are Dailies has no best score, not a Daily one. */
    @Test
    fun bestScore_isZeroWhenEveryRunIsADaily() {
        val stats = statsFrom(listOf(threeRuns.first().copy(score = 40_000, mode = GameMode.DAILY)))

        assertEquals(0, stats.bestScore)
        assertEquals(1, stats.runsPlayed)
    }

    @Test
    fun everyStatMatchesAHandCount() {
        val stats = statsFrom(threeRuns)

        assertEquals(4, stats.runsPlayed)
        assertEquals(9_000, stats.bestScore)
        // 1200 + 9000 + 4400 + 400 = 15000, over four runs.
        assertEquals(3_750, stats.averageScore)
        assertEquals(2048, stats.highestTier)
        assertEquals(120, stats.totalMerges)
        assertEquals(305, stats.totalBlocksPlaced)
        assertEquals(7, stats.longestCascade)
        assertEquals(3, stats.mostBurstsInARun)
        assertEquals(4, stats.lifetimeBursts)
        assertEquals(1_800_000, stats.totalPlaytimeMs)
    }

    /**
     * The average truncates rather than rounds. 15,001 over four runs is
     * 3,750.25 and reads as 3,750.
     */
    @Test
    fun average_truncates() {
        val runs = threeRuns.mapIndexed { index, run ->
            if (index == 0) run.copy(score = run.score + 1) else run
        }
        assertEquals(3_750, statsFrom(runs).averageScore)
    }

    /**
     * Decision D7 lands here as much as anywhere: the run that reached 2048 is
     * the one whose board was emptied by the burst, and it is still the run that
     * sets the highest tier.
     */
    @Test
    fun highestTier_comesFromTheRunThatReachedIt() {
        assertEquals(2048, statsFrom(threeRuns).highestTier)
        assertEquals(512, statsFrom(threeRuns.filterNot { it.highestTier == 2048 }).highestTier)
    }

    @Test
    fun recentScores_areNewestFirstAndCapped() {
        val many = (1..RunStats.RecentRunCount + 5).map { run(score = it.toLong(), endedAt = it.toLong()) }
        val recent = statsFrom(many).recentScores

        assertEquals(RunStats.RecentRunCount, recent.size)
        assertEquals(15, recent.first())
        assertEquals(6, recent.last())
    }

    private companion object {
        val threeRuns = listOf(
            run(
                score = 1_200,
                endedAt = 1,
                merges = 20,
                blocksPlaced = 40,
                longestCascade = 3,
                bursts = 0,
                highestTier = 128,
                durationMs = 120_000,
            ),
            run(
                score = 9_000,
                endedAt = 2,
                merges = 60,
                blocksPlaced = 150,
                longestCascade = 7,
                bursts = 3,
                highestTier = 2048,
                durationMs = 900_000,
            ),
            run(
                score = 4_400,
                endedAt = 3,
                merges = 35,
                blocksPlaced = 100,
                longestCascade = 5,
                bursts = 1,
                highestTier = 512,
                durationMs = 700_000,
            ),
            run(
                score = 400,
                endedAt = 4,
                merges = 5,
                blocksPlaced = 15,
                longestCascade = 1,
                bursts = 0,
                highestTier = 32,
                durationMs = 80_000,
            ),
        )

        @Suppress("LongParameterList")
        fun run(
            score: Long,
            endedAt: Long,
            merges: Int = 0,
            blocksPlaced: Int = 0,
            longestCascade: Int = 0,
            bursts: Int = 0,
            highestTier: Int = 0,
            durationMs: Long = 0,
        ) = RunRecord(
            endedAt = endedAt,
            score = score,
            level = 1,
            blocksPlaced = blocksPlaced,
            durationMs = durationMs,
            highestTier = highestTier,
            cause = "ROW_ZERO_OCCUPIED",
            longestCascade = longestCascade,
            bursts = bursts,
            merges = merges,
            mode = GameMode.ENDLESS,
            seed = 1,
        )
    }
}
