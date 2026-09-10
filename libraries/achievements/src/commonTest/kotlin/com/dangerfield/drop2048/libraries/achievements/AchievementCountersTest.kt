package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.progress.GameMode
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
     * Decision D19: a Daily score is set on a seed everybody else also played, so
     * it cannot own the headline number. Everything else about the run still
     * counts, because a merge is a merge.
     */
    @Test
    fun aDailyRunFeedsEveryCounterExceptTheBestScore() {
        val counters = foldAll(outcome(mode = GameMode.DAILY, score = 500_000, merges = 40))

        assertEquals(0L, counters[Stat.BestScore])
        assertEquals(40L, counters[Stat.TotalMerges])
    }

    @Test
    fun theDailyStreakIsAHighWaterMarkAndABrokenStreakDoesNotLowerIt() {
        val counters = foldAll(
            outcome(mode = GameMode.DAILY, dailyStreakDays = 9, endedAt = 1),
            outcome(mode = GameMode.DAILY, dailyStreakDays = 1, endedAt = 2),
        )

        assertEquals(9L, counters[Stat.BestDailyStreak])
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
