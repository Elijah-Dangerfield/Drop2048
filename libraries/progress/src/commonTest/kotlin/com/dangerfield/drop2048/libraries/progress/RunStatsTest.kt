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
     * The best score is a max over every row, and the rest of the page counts
     * every row too. D19 used to carve Daily runs out of the best; D27 removed
     * the mode, so there is nothing left to carve.
     */
    @Test
    fun bestScore_isTheHighestOfEveryRun() {
        val modest = threeRuns.first().copy(score = 1_000)
        val big = threeRuns.first().copy(score = 40_000, endedAt = 2)

        val stats = statsFrom(listOf(modest, big))

        assertEquals(40_000, stats.bestScore)
        assertEquals(2, stats.runsPlayed)
        assertEquals(20_500, stats.averageScore)
    }

    @Test
    fun everyStatMatchesAHandCount() {
        val stats = statsFrom(threeRuns)

        assertEquals(4, stats.runsPlayed)
        assertEquals(9_000, stats.bestScore)
        // 1200 + 9000 + 4400 + 400 = 15000, over four runs.
        assertEquals(3_750, stats.averageScore)
        assertEquals(12, stats.highestLevel)
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

    /**
     * The owner asked for this one on 2026-09-20, and the pair of assertions is
     * the reason it is not the same number as [RunStats.highestTier].
     *
     * Levels advance on blocks dropped, so the furthest level is a survival
     * record. The run that reached level 12 here is the one that made a 2048,
     * but the run that reached level 9 never got past 512 — drop the 2048 run
     * and the two figures come apart, which is what a single "best run" stat
     * would hide.
     */
    @Test
    fun highestLevel_isTheFurthestAnyRunGot() {
        assertEquals(12, statsFrom(threeRuns).highestLevel)

        val withoutTheBestRun = threeRuns.filterNot { it.highestTier == 2048 }
        assertEquals(9, statsFrom(withoutTheBestRun).highestLevel)
        assertEquals(512, statsFrom(withoutTheBestRun).highestTier)
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
                level = 4,
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
                level = 12,
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
                level = 9,
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
                level = 2,
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
            level: Int = 1,
            merges: Int = 0,
            blocksPlaced: Int = 0,
            longestCascade: Int = 0,
            bursts: Int = 0,
            highestTier: Int = 0,
            durationMs: Long = 0,
        ) = RunRecord(
            endedAt = endedAt,
            score = score,
            level = level,
            blocksPlaced = blocksPlaced,
            durationMs = durationMs,
            highestTier = highestTier,
            cause = "ROW_ZERO_OCCUPIED",
            longestCascade = longestCascade,
            bursts = bursts,
            merges = merges,
            seed = 1,
        )
    }
}
