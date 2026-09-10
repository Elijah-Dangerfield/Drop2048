package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fold that turns runs into badges.
 *
 * Its whole job is "announce a badge exactly once", so most of this file is
 * about the second time something happens.
 */
class AchievementEngineTest {

    @Test
    fun aBadgeIsAnnouncedOnceAndNeverAgain() {
        val first = AchievementEngine.apply(AchievementState.Empty, outcome(merges = 1, endedAt = 1))
        val second = AchievementEngine.apply(first.state, outcome(merges = 1, endedAt = 2))

        assertTrue(AchievementId.FirstMerge in first.newlyUnlocked.map { it.id })
        assertTrue(second.newlyUnlocked.isEmpty(), "a badge was announced twice")
    }

    @Test
    fun theUnlockIsStampedWithTheRunThatEarnedItRatherThanWithNow() {
        val update = AchievementEngine.apply(
            AchievementState.Empty,
            outcome(merges = 1, endedAt = 1_700_000_000_000),
        )

        assertEquals(1_700_000_000_000, update.state.unlocked.getValue(AchievementId.FirstMerge))
    }

    @Test
    fun severalBadgesCanCrossOnOneRunAndArriveInCatalogOrder() {
        val update = AchievementEngine.apply(
            AchievementState.Empty,
            outcome(merges = 40, highestTier = 64, longestCascade = 5, score = 100_000),
        )
        val ids = update.newlyUnlocked.map { it.id }

        assertTrue(AchievementId.FirstMerge in ids)
        assertTrue(AchievementId.SixtyFour in ids)
        assertTrue(AchievementId.ChainOfFive in ids)
        assertEquals(ids.sortedBy { id -> Achievements.catalog.indexOfFirst { it.id == id } }, ids)
    }

    /**
     * A history replayed from the fact log has to produce the badges it produced
     * live, which is what lets a badge added in a later release back-fill instead
     * of starting everybody at zero.
     */
    @Test
    fun replayingAHistoryProducesTheSameStateAsFoldingItLive() {
        val history = listOf(
            outcome(merges = 3, endedAt = 1),
            outcome(merges = 20, highestTier = 64, endedAt = 2),
            outcome(mode = GameMode.DAILY, dailyStreakDays = 7, endedAt = 3),
        )
        val live = history.fold(AchievementState.Empty) { state, run ->
            AchievementEngine.apply(state, run).state
        }

        assertEquals(live, AchievementEngine.replay(history))
    }

    /**
     * The complement of `AchievementReachabilityTest`: every target is reachable
     * by *some* history, which says the fold can reach it. That the *game* can is
     * a different question and a different file.
     */
    @Test
    fun everyTargetInTheCatalogIsReachableByTheFold() {
        Achievements.catalog.forEach { achievement ->
            val counters = historyFor(achievement.stat, achievement.target)
                .fold(AchievementCounters.Empty) { acc, run -> acc.fold(run) }

            assertTrue(
                achievement.isMet(counters),
                "${achievement.id} was not met by a history built to meet it: " +
                    "${achievement.stat} reached ${counters[achievement.stat]} of ${achievement.target}",
            )
        }
    }

    @Test
    fun theCatalogAndItsSectionsHoldTheSameBadges() {
        assertEquals(
            Achievements.sections.sumOf { it.achievements.size },
            Achievements.catalog.size,
        )
        assertEquals(
            Achievements.catalog.map { it.id }.toSet(),
            AchievementId.entries.toSet(),
            "an AchievementId is missing from the catalog, or the catalog has one twice",
        )
    }

    /**
     * A history that drives [stat] to [target], and moves as little else as it
     * can. The `when` is exhaustive on purpose: a new [Stat] cannot be added
     * without deciding here how a player would actually earn it.
     */
    private fun historyFor(stat: Stat, target: Long): List<RunOutcome> = when (stat) {
        Stat.TotalMerges -> listOf(outcome(merges = target.toInt()))
        Stat.TotalBursts -> listOf(outcome(bursts = target.toInt()))
        Stat.BoardsCleared -> listOf(outcome(facts = RunFacts(boardsCleared = target.toInt())))
        Stat.StoneBursts -> listOf(outcome(facts = RunFacts(stoneBursts = target.toInt())))
        Stat.WildcardBursts -> listOf(outcome(facts = RunFacts(wildcardBursts = target.toInt())))
        Stat.MostBurstsInARun -> listOf(outcome(bursts = target.toInt()))
        Stat.LongestCascade -> listOf(outcome(longestCascade = target.toInt()))
        Stat.HighestTier -> listOf(outcome(highestTier = target.toInt()))
        Stat.HighestLevel -> listOf(outcome(level = target.toInt()))
        Stat.MostBlocksInARun -> listOf(outcome(blocksPlaced = target.toInt()))
        Stat.LongestDangerRun -> listOf(outcome(facts = RunFacts(longestDangerRun = target.toInt())))
        Stat.BestScore -> listOf(outcome(score = target))
        Stat.BestDailyStreak ->
            listOf(outcome(mode = GameMode.DAILY, dailyStreakDays = target.toInt()))

        Stat.MinutesPlayed -> listOf(outcome(durationMs = target * MinuteMs))
    }

    private companion object {
        const val MinuteMs = 60_000L
    }
}
