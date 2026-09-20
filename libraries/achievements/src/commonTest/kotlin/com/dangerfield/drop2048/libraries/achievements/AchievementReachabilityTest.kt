package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.Special
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whether the *shipped engine* can produce the numbers the catalog asks for.
 *
 * `AchievementEngineTest` proves each target is reachable by *some* history,
 * which is a statement about the fold and nothing else — it would happily bless
 * a 400,000-point badge on a game that tops out at 200,000, or an eleven-step
 * cascade on a board that cannot produce one. This file asks `:libraries:cascade`
 * instead, and it is the test that catches a badge whose condition the game can
 * never satisfy. A badge nobody can earn is worse than no badge, and it looks
 * exactly like a working one: a tile with a progress bar stuck near zero.
 *
 * Every [Stat] answers in one of three ways, and the `when` has no `else`, so a
 * new stat cannot be added without somebody deciding which:
 *
 * - [Reach.WithinOneRun] — the value is a high-water mark over a single run, so
 *   the target has to be inside what one run can do. The bound is measured, by
 *   playing the engine, or witnessed, by posing a board and locking a block into
 *   it.
 * - [Reach.OverManyRuns] — the value accumulates across a lifetime. There is
 *   nothing to bound; what has to be true is that the engine can produce the
 *   thing *at all*, and the witness is one run that does.
 * - [Reach.OffTheBoard] — a wall clock decides it. The engine has no opinion and
 *   neither does this file.
 *
 * The measured numbers come from forty greedy runs, which is small next to
 * `tools/balance`'s ten thousand and is not trying to be a distribution: it only
 * has to demonstrate a ceiling, and it is fixed-seed so it demonstrates the same
 * one on every machine.
 */
class AchievementReachabilityTest {

    @Test
    fun everyAchievementInTheCatalogIsActuallyEarnable() {
        Achievements.catalog.forEach { achievement ->
            when (val reach = reachOf(achievement.stat)) {
                is Reach.WithinOneRun -> assertTrue(
                    achievement.target <= reach.best,
                    "${achievement.id} wants ${achievement.stat} at ${achievement.target}, " +
                        "and the best one run has been shown to do is ${reach.best} (${reach.how})",
                )

                is Reach.OverManyRuns -> assertTrue(
                    reach.perRun >= 1,
                    "${achievement.id} counts ${achievement.stat} over a lifetime, and no run " +
                        "has ever been shown to produce one (${reach.how})",
                )

                Reach.OffTheBoard -> Unit
            }
        }
    }

    /**
     * Five rungs derived from five separate `floorScore` calls could come out in
     * any order if a level or a coefficient were swapped, and a ladder that
     * reads 32,000 then 14,000 unlocks backwards without failing anything else
     * here.
     */
    @Test
    fun theScoreLadderClimbs() {
        val targets = Achievements.catalog.filter { it.stat == Stat.BestScore }.map { it.target }

        assertEquals(targets.sorted(), targets, "the score badges are out of order: $targets")
        assertEquals(targets.distinct(), targets, "two score badges share a rung: $targets")
    }

    /**
     * The point of a derived ladder is that it moves with the coefficients. The
     * point of this is that it stays inside what somebody actually plays: a rung
     * above the best of forty greedy runs would be a badge that only exists in
     * principle.
     */
    @Test
    fun theTopScoreRungIsAGoodRunRatherThanAFreakOne() {
        val top = Achievements.catalog.filter { it.stat == Stat.BestScore }.maxOf { it.target }

        assertTrue(
            top <= bestRun.score,
            "the top score badge wants $top and the best measured greedy run banked ${bestRun.score}",
        )
    }

    /**
     * The five posed witnesses, named one at a time as well as folded into
     * [reachOf], because when one of them breaks the useful failure is which
     * one.
     */
    @Test
    fun theEngineStillProducesEveryRareMomentTheCatalogAsksFor() {
        assertEquals(1, clearedBoard.boardsCleared, "no drop clears the board any more")
        assertEquals(1, stoneBurst.stoneBursts, "a burst no longer takes three Stones with it")
        assertEquals(1, wildcardTwentyFortyEight.wildcardBursts, "a Wildcard no longer makes a 2048")
        assertEquals(DeepestWitnessedCascade, deepCascade, "the ten-step cascade board stopped cascading")
        assertEquals(2, doubleBurst, "one drop no longer bursts two rows")
    }

    /**
     * How each stat is bounded, and by what.
     *
     * Measured for the four that a competent run simply reaches; witnessed for
     * the two that are far too rare to turn up in any affordable number of
     * played runs; counted-per-run for the accumulators; and refused for the one
     * a clock owns.
     */
    private fun reachOf(stat: Stat): Reach = when (stat) {
        Stat.BestScore -> Reach.WithinOneRun(bestRun.score, "the best of $Runs greedy runs")
        Stat.HighestLevel -> Reach.WithinOneRun(runs.maxOf { it.level }.toLong(), "greedy runs")
        Stat.MostBlocksInARun ->
            Reach.WithinOneRun(runs.maxOf { it.blocksDropped }.toLong(), "greedy runs")

        Stat.HighestTier -> Reach.WithinOneRun(runs.maxOf { it.highestTier }.toLong(), "greedy runs")
        Stat.LongestDangerRun ->
            Reach.WithinOneRun(runs.maxOf { it.facts.longestDangerRun }.toLong(), "greedy runs")

        Stat.LongestCascade -> Reach.WithinOneRun(deepCascade.toLong(), "a posed seven-tier column")
        Stat.MostBurstsInARun -> Reach.WithinOneRun(doubleBurst.toLong(), "a posed double-burst board")

        Stat.TotalMerges -> Reach.OverManyRuns(bestRun.merges.toLong(), "greedy runs")
        Stat.TotalBursts -> Reach.OverManyRuns(doubleBurst.toLong(), "a posed board")
        Stat.BoardsCleared ->
            Reach.OverManyRuns(clearedBoard.boardsCleared.toLong(), "a posed single-column board")

        Stat.StoneBursts ->
            Reach.OverManyRuns(stoneBurst.stoneBursts.toLong(), "a posed row of three Stones")

        Stat.WildcardBursts -> Reach.OverManyRuns(
            wildcardTwentyFortyEight.wildcardBursts.toLong(),
            "a Wildcard dropped beside a 1024",
        )

        Stat.MinutesPlayed -> Reach.OffTheBoard
    }

    /**
     * A chain seven tiers deep, whose 2048 bursts the bottom row and drops two
     * more merges out of the columns beside it. Ten steps exactly, which is the
     * deepest the catalog asks for.
     */
    private val deepCascade: Int by lazy {
        EngineWitness.dropInto(
            state = EngineWitness.stateOf(
                EngineWitness.boardOf(
                    """
                    . . 16 . .
                    . . 32 . .
                    . . 64 . .
                    . . 128 32 .
                    . . 256 16 .
                    . . 512 8 8
                    . . 1024 4 4
                    """
                )
            ),
            block = EngineWitness.value(16),
            col = 2,
        ).transcript.depth
    }

    /**
     * One drop, two bursts. The first clears row 7, gravity drops a pair of
     * 1024s into it, and they make a second 2048 on the next step.
     */
    private val doubleBurst: Int by lazy {
        EngineWitness.dropInto(
            state = EngineWitness.stateOf(
                EngineWitness.boardOf(
                    """
                    . . 16 . .
                    . . 32 . .
                    . . 64 . .
                    . . 128 . .
                    . . 256 1024 .
                    . . 512 1024 .
                    . . 1024 4 .
                    """
                )
            ),
            block = EngineWitness.value(16),
            col = 2,
        ).transcript.bursts.size
    }

    /** The whole board is one column, so bursting it leaves nothing behind. */
    private val clearedBoard: RunFacts by lazy {
        RunFacts.Empty.fold(
            EngineWitness.dropInto(
                state = EngineWitness.stateOf(
                    EngineWitness.boardOf(
                        """
                        . . 16 . .
                        . . 32 . .
                        . . 64 . .
                        . . 128 . .
                        . . 256 . .
                        . . 512 . .
                        . . 1024 . .
                        """
                    )
                ),
                block = EngineWitness.value(16),
                col = 2,
            )
        )
    }

    /**
     * Three Stones resting in the row a 2048 lands in.
     *
     * The one the brief flagged as non-obvious, and it holds for a reason worth
     * naming: SPEC 18.5 has a burst clear Stones along with everything else, and
     * a five-wide board has four cells left over once the 2048 has taken one.
     * Take that rule away and this badge becomes unearnable without anything
     * else in the game visibly changing.
     */
    private val stoneBurst: RunFacts by lazy {
        RunFacts.Empty.fold(
            EngineWitness.dropInto(
                state = EngineWitness.stateOf(
                    EngineWitness.boardOf(
                        """
                        . . . 16 .
                        . . . 32 .
                        . . . 64 .
                        . . . 128 .
                        . . . 256 .
                        . . . 512 .
                        S S S 1024 .
                        """
                    )
                ),
                block = EngineWitness.value(16),
                col = 3,
            )
        )
    }

    /** SPEC 5.2's screenshot moment: a Wildcard landing beside a 1024. */
    private val wildcardTwentyFortyEight: RunFacts by lazy {
        RunFacts.Empty.fold(
            EngineWitness.dropInto(
                state = EngineWitness.stateOf(EngineWitness.boardOf(". . 1024 . .")),
                block = EngineWitness.special(Special.WILDCARD),
                col = 2,
            )
        )
    }

    private val runs: List<PlayedRun> by lazy {
        (1L..Runs).map { EngineWitness.playGreedily(it, EngineConfig.Default) }
    }

    private val bestRun: PlayedRun get() = runs.maxBy { it.score }

    private sealed interface Reach {
        /** A high-water mark over one run. [best] is the most one run has been shown to do. */
        data class WithinOneRun(val best: Long, val how: String) : Reach

        /** A lifetime tally. [perRun] is how many one run has been shown to produce. */
        data class OverManyRuns(val perRun: Long, val how: String) : Reach

        /** A wall clock bounds it, not the engine. */
        data object OffTheBoard : Reach
    }

    private companion object {
        /**
         * Enough to demonstrate a ceiling, not enough to be a distribution.
         * `tools/balance` is where the distribution lives; this only has to show
         * that the numbers the catalog asks for are inside what the engine does.
         */
        const val Runs = 40L

        /** What the posed board produces, pinned so a change to it is deliberate. */
        const val DeepestWitnessedCascade = 10
    }
}
