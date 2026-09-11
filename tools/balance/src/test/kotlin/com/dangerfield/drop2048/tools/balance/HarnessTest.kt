package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.SpeedCurve
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The harness is a measuring instrument, and a broken one looks exactly like a
 * game that has nothing wrong with it. These tests check the instrument, not the
 * balance: a policy that always answered column 0, a depth histogram that was
 * always zero, or a run loop that stopped one drop early would all print a
 * plausible report.
 *
 * The numbers themselves are deliberately not pinned. They are supposed to move
 * when the spawn table is retuned; that is what `BUILD-PLAN.md`'s C1a outcome is
 * for.
 */
class HarnessTest {

    private val config = EngineConfig.Default

    @Test
    fun everyPolicyEndsItsRunsRatherThanHittingTheDropCap() {
        Policy.All.forEach { policy ->
            (1L..20L).forEach { seed ->
                val outcome = Harness.play(seed, policy, config)
                assertTrue(
                    outcome.death != Death.DROP_CAP,
                    "${policy.name} seed $seed survived ${Harness.MAX_DROPS} drops",
                )
                assertTrue(outcome.death != Death.ENGINE_FAULT, "${policy.name} seed $seed faulted the engine")
            }
        }
    }

    @Test
    fun aSeedReplaysToTheSameRun() {
        Policy.All.forEach { policy ->
            val first = Harness.play(7, policy, config)
            val second = Harness.play(7, policy, config)
            assertEquals(first.score, second.score, policy.name)
            assertEquals(first.blocksDropped, second.blocksDropped, policy.name)
            assertEquals(first.level, second.level, policy.name)
        }
    }

    @Test
    fun theMergeSeekingPoliciesActuallyOutplayTheRandomOne() {
        val random = median(Policy.Random)
        val greedy = median(Policy.Greedy)
        val lookahead = median(Policy.Lookahead1)

        assertTrue(greedy > random, "greedy $greedy did not beat random $random")
        assertTrue(lookahead >= greedy, "lookahead $lookahead did not match greedy $greedy")
    }

    @Test
    fun everyColumnGetsUsed() {
        val used = (1L..30L).flatMap { seed ->
            var state = Cascade.newGame(seed, config)
            val random = Random(seed)
            val columns = mutableListOf<Int>()
            while (!state.isOver && state.blocksDropped < 60) {
                val col = Policy.Greedy.column(state, random, config)
                columns += col
                state = Harness.drop(state, col).state
            }
            columns
        }.toSet()

        assertEquals((0 until config.cols).toSet(), used, "a policy that ignores columns measures nothing")
    }

    @Test
    fun cascadesOfDepthTwoOrMoreHappenOftenEnoughToMeasure() {
        val depths = (1L..40L).map { Harness.play(it, Policy.Greedy, config).depths }
        val deep = depths.sumOf { row -> row.drop(2).sum().toLong() }
        val total = depths.sumOf { row -> row.sum().toLong() }

        assertTrue(deep > 0, "no cascade ever reached depth 2")
        assertTrue(deep * 100 / total in 1..99, "depth>=2 share was ${deep * 100 / total}%")
    }

    @Test
    fun clutterIsSampledEveryTenthDropAndComesFromTheEngine() {
        val outcome = Harness.play(3, Policy.Greedy, config)
        val samples = outcome.clutter.sum()

        assertEquals(outcome.blocksDropped / Harness.CLUTTER_SAMPLE_EVERY, samples)
        assertTrue(outcome.clutter.indexOfLast { it > 0 } <= config.cols * config.rows)
    }

    /**
     * [Policy.Random] is deliberately not in here. Measured, it plays *better*
     * with a clock in the loop, because "columns the block can still be steered
     * into" is partly "columns with room in them" — a tall column on the way
     * blocks the path. A random player that re-picks among reachable columns is
     * therefore a slightly less random player. The two policies that were already
     * aiming at something have no such back door.
     */
    @Test
    fun theClockCostsTheMergeSeekingPoliciesSomething() {
        listOf(Policy.Greedy, Policy.Lookahead1).forEach { policy ->
            val ceiling = (1L..40L).map { Harness.play(it, policy, config).level }.average()
            val clocked = (1L..40L).map { Harness.play(it, policy, config, PlayerProfile.Average).level }.average()

            assertTrue(
                clocked <= ceiling,
                "${policy.name} played better with a clock in the loop ($clocked) than without one ($ceiling)",
            )
        }
    }

    @Test
    fun theTimerCanActuallyBeatAPlayerToTheLock() {
        val outcomes = (1L..30L).map { Harness.play(it, Policy.Greedy, config, PlayerProfile.Deliberate) }
        val beaten = outcomes.sumOf { it.clock?.timerPlaced ?: 0 }

        assertTrue(beaten > 0, "no drop in 30 runs ever locked before the player got where it was going")
    }

    @Test
    fun aSlowerPlayerReachesItsPreferredColumnLessOften() {
        val quick = offTargetShare(PlayerProfile.Quick)
        val deliberate = offTargetShare(PlayerProfile.Deliberate)

        assertTrue(
            deliberate >= quick,
            "a 400ms/180ms player missed its column less often ($deliberate) than a 150ms/70ms one ($quick)",
        )
    }

    @Test
    fun theClockedRunAccountsForEveryDropInWallTime() {
        val outcome = Harness.play(11, Policy.Greedy, config, PlayerProfile.Average)
        val clock = requireNotNull(outcome.clock)

        assertTrue(clock.elapsedMillis > 0, "a clocked run took no time at all")
        assertTrue(
            clock.elapsedMillis / outcome.blocksDropped in MIN_DROP_MILLIS..MAX_DROP_MILLIS,
            "mean drop took ${clock.elapsedMillis / outcome.blocksDropped}ms",
        )
        assertEquals(
            minOf(outcome.blocksDropped, Harness.EARLY_DROPS),
            clock.earlyDrops.sum(),
            "the per-level early histogram lost drops",
        )
    }

    @Test
    fun aClockFreeRunCarriesNoClockStatsAtAll() {
        assertEquals(null, Harness.play(5, Policy.Greedy, config).clock)
    }

    @Test
    fun theOpeningLevelsStillLeaveThePlayerWaiting() {
        val outcome = Harness.play(9, Policy.Greedy, config, PlayerProfile.Average)
        val clock = requireNotNull(outcome.clock)
        val slackAtLevelOne = clock.earlySlackMillis[1] / clock.earlyDrops[1]

        assertTrue(slackAtLevelOne > MIN_LEVEL_ONE_SLACK_MILLIS, "level 1 slack was only ${slackAtLevelOne}ms")
    }

    @Test
    fun aFasterOpeningCurveCutsThatWait() {
        val before = levelOneSlack(Curves.Original)
        val after = levelOneSlack(Curves.Fast500)

        assertTrue(after < before, "the 500ms curve left as much slack ($after) as the 700ms one ($before)")
    }

    /**
     * The single most useful thing C1e measured, and the one most likely to be
     * broken by accident later.
     *
     * A drop control decides *when* a block locks, never *where*: `Input.Lock`
     * places at the landing cell, so hard-dropping to the floor and letting the
     * timer do it produce the same board. Measured over 10,000 runs the two
     * profiles agree to the digit on every outcome column, which is what makes
     * the whole pacing argument separable from the difficulty one.
     *
     * **The score is the one column that is now allowed to differ**, because
     * decision D21 reinstated SPEC 7's hard drop bonus and that is the point of
     * it: the same board, played decisively, is worth a little more. It is
     * asserted as an inequality rather than dropped, so a bonus that ever went
     * *negative* would still be caught.
     *
     * If the rest of this ever fails, the ▼ control has started changing
     * outcomes, and every "pure pacing dial" claim in `BUILD-PLAN.md`'s C1e
     * section is void.
     */
    @Test
    fun theDropControlChangesTheWallClockAndNothingElse() {
        (1L..40L).forEach { seed ->
            val dropping = Harness.play(seed, Policy.Greedy, config, PlayerProfile.Average)
            val patient = Harness.play(seed, Policy.Greedy, config, PlayerProfile.Patient)

            assertEquals(patient.level, dropping.level, "seed $seed level")
            assertEquals(patient.blocksDropped, dropping.blocksDropped, "seed $seed drops")
            assertEquals(patient.highestTier, dropping.highestTier, "seed $seed highest tier")
            assertTrue(
                dropping.clock!!.elapsedMillis < patient.clock!!.elapsedMillis,
                "seed $seed: hard-dropping took no less wall clock than never touching the control",
            )
            assertTrue(
                dropping.score >= patient.score,
                "seed $seed: the same board scored less with the bonus than without it",
            )
        }
    }

    /**
     * A level-1 drop costs a decision and a press, and nothing else.
     *
     * C1c measured the hard-drop column at 0.54s a drop at level 1 against 4.15s
     * patient, and decision D21 returns the game to it. The bound is loose on
     * purpose: it is guarding the *order of magnitude*, which is what would move
     * if the finish model quietly started charging for gravity again.
     */
    @Test
    fun aHardDroppingPlayerSpendsUnderASecondOnALevelOneDrop() {
        val clocks = (1L..30L).mapNotNull { Harness.play(it, Policy.Greedy, config, PlayerProfile.Average).clock }
        val perDrop = clocks.sumOf { it.earlyElapsedMillis[1] } / clocks.sumOf { it.earlyDrops[1] }

        assertTrue(perDrop < MAX_LEVEL_ONE_DROPPED_MILLIS, "a level-1 drop took ${perDrop}ms while hard-dropping")
    }

    private fun levelOneSlack(curve: SpeedCurve): Long {
        val clocks = (1L..20L).mapNotNull {
            Harness.play(it, Policy.Greedy, config.copy(speed = curve), PlayerProfile.Average).clock
        }
        return clocks.sumOf { it.earlySlackMillis[1] } / clocks.sumOf { it.earlyDrops[1] }
    }

    private fun offTargetShare(profile: PlayerProfile): Double {
        val outcomes = (1L..30L).map { Harness.play(it, Policy.Greedy, config, profile) }
        val off = outcomes.sumOf { it.clock?.offTarget ?: 0 }.toDouble()
        return off / outcomes.sumOf { it.blocksDropped }
    }

    private fun median(policy: Policy): Int =
        (1L..60L).map { Harness.play(it, policy, config).level }.sorted()[30]

    private companion object {
        const val MIN_DROP_MILLIS = 200L
        const val MAX_DROP_MILLIS = 5_000L
        const val MIN_LEVEL_ONE_SLACK_MILLIS = 1_000L
        const val MAX_LEVEL_ONE_DROPPED_MILLIS = 1_200L
    }
}
