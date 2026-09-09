package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
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

    private fun median(policy: Policy): Int =
        (1L..60L).map { Harness.play(it, policy, config).level }.sorted()[30]
}
