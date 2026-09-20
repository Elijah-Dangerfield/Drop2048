package com.dangerfield.drop2048.libraries.achievements

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fold, one shape of counter at a time.
 *
 * Three shapes, and each has a failure mode of its own: an accumulator that
 * silently maxes instead of adding, a high-water mark that a bad run can lower,
 * and a truncated total that loses everything under a minute.
 */
class AchievementCountersTest {

    @Test
    fun aRunThatDidNothingMovesNothing() {
        val counters = foldAll(outcome())

        Stat.entries
            .filterNot { it == Stat.HighestTier || it == Stat.HighestLevel }
            .forEach { assertEquals(0L, counters[it], "$it moved on an empty run") }
    }

    @Test
    fun mergesAndBurstsAccumulateAcrossRuns() {
        val counters = foldAll(
            outcome(merges = 12, bursts = 1, endedAt = 1),
            outcome(merges = 30, bursts = 2, endedAt = 2),
        )

        assertEquals(42L, counters[Stat.TotalMerges])
        assertEquals(3L, counters[Stat.TotalBursts])
    }

    @Test
    fun theBestOfEachRunIsKeptAndABadRunCannotLowerIt() {
        val counters = foldAll(
            outcome(score = 90_000, level = 22, blocksPlaced = 440, longestCascade = 6, endedAt = 1),
            outcome(score = 200, level = 2, blocksPlaced = 30, longestCascade = 1, endedAt = 2),
        )

        assertEquals(90_000L, counters[Stat.BestScore])
        assertEquals(22L, counters[Stat.HighestLevel])
        assertEquals(440L, counters[Stat.MostBlocksInARun])
        assertEquals(6L, counters[Stat.LongestCascade])
    }

    /**
     * The best score is a high-water mark, so a worse run after a good one
     * cannot lower it. D19 used to carve Daily runs out of this counter; D27
     * removed the mode, so every run is eligible and the carve-out is gone.
     */
    @Test
    fun theBestScoreIsAHighWaterMarkAcrossRuns() {
        val counters = foldAll(
            outcome(score = 500_000, merges = 40, endedAt = 1),
            outcome(score = 12, merges = 2, endedAt = 2),
        )

        assertEquals(500_000L, counters[Stat.BestScore])
        assertEquals(42L, counters[Stat.TotalMerges])
    }

    /**
     * Playtime is truncated for display, so the milliseconds have to be carried
     * at full precision: two half-minute runs are a minute, and a fold that
     * truncated per run would report nothing for either.
     */
    @Test
    fun playtimeAccumulatesBeforeItIsTruncated() {
        val counters = foldAll(
            outcome(durationMs = 30_000, endedAt = 1),
            outcome(durationMs = 30_000, endedAt = 2),
            outcome(durationMs = 30_000, endedAt = 3),
        )

        assertEquals(1L, counters[Stat.MinutesPlayed])
    }

    @Test
    fun theRareFactsComeOffTheRunTheyHappenedIn() {
        val counters = foldAll(
            outcome(
                facts = RunFacts(
                    boardsCleared = 1,
                    stoneBursts = 1,
                    wildcardBursts = 2,
                    longestDangerRun = 14,
                ),
                endedAt = 1,
            ),
            outcome(facts = RunFacts(boardsCleared = 2, longestDangerRun = 3), endedAt = 2),
        )

        assertEquals(3L, counters[Stat.BoardsCleared])
        assertEquals(1L, counters[Stat.StoneBursts])
        assertEquals(2L, counters[Stat.WildcardBursts])
        assertEquals(14L, counters[Stat.LongestDangerRun])
    }
}
