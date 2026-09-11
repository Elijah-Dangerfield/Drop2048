package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.DROPS_PER_SAMPLE
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC 4.4's requirement, as a test: the harness's clutter and the live
 * client's clutter come from **one function in `:libraries:cascade`**, sampled
 * on one cadence.
 *
 * The whole value of the metric is that a number off a dashboard and a number
 * off a `./gradlew :tools:balance:run` can be put on the same axis. Two
 * implementations of "blocks with no matching partner anywhere" would agree the
 * day they were written and diverge on the first edit to either, and nothing
 * anywhere would say so — the numbers keep arriving, they just quietly stop
 * being about the same thing.
 *
 * This is the harness end of the assertion: the histogram [Harness.play]
 * reports is rebuilt here from `GameState.clutter` at `GameState.isSampleDrop`,
 * with no reference to the harness's own accumulation. Reimplement clutter
 * inside `Harness` and this goes red. The client end is
 * `RunSampleTest.theClutterOnASampleIsTheEnginesOwnReading` in
 * `:features:game:impl`, which compares the emitted attribute against the same
 * property on the engine state that the emitting lock wrote to disk.
 */
class ClutterParityTest {

    private val config = EngineConfig.Default

    @Test
    fun theHarnessHistogramIsTheEnginesOwnClutterOnTheEnginesOwnCadence() {
        listOf(3L, 17L, 404L).forEach { seed ->
            val reported = Harness.play(seed, Policy.Greedy, config).clutter
            assertEquals(
                replayClutter(seed).toList(),
                reported.toList(),
                "seed $seed: the harness is not reading GameState.clutter on isSampleDrop",
            )
            assertTrue(reported.sum() > 0, "seed $seed sampled nothing at all")
        }
    }

    /**
     * The cadence constant is the engine's, not a copy. A `10` that drifted to
     * `5` in one module would halve one side of the comparison while both sides
     * kept looking healthy.
     */
    @Test
    fun theSamplingPeriodIsTheEngines() {
        assertEquals(DROPS_PER_SAMPLE, Harness.CLUTTER_SAMPLE_EVERY)
    }

    /**
     * `Harness.play`'s clock-free loop, rebuilt from the engine's own pieces so
     * the comparison above is against something independent rather than against
     * the code it is checking.
     */
    private fun replayClutter(seed: Long): IntArray {
        val random = Random(seed)
        val histogram = IntArray(config.cols * config.rows + 1)
        var state = Cascade.newGame(seed, config)
        while (state.blocksDropped < Harness.MAX_DROPS) {
            val transition = Harness.drop(state, Policy.Greedy.column(state, random, config))
            state = transition.state
            if (state.isSampleDrop) histogram[state.clutter]++
            if (transition.faults.isNotEmpty() || state.isOver) break
        }
        return histogram
    }
}
