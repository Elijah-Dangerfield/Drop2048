package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.DROPS_PER_SAMPLE
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SPEC 17's per-drop sample, asserted at its call site.
 *
 * The clutter half is the one that matters. SPEC 4.4 says the harness and the
 * live client must compute it from **the same function in `:libraries:cascade`**,
 * because the entire value of the metric is that the offline number and the
 * live number can be put on the same axis. Two implementations of "blocks with
 * no matching partner" would agree on the day they were written and diverge on
 * the first edit, and nothing on the dashboard would say so — the numbers keep
 * arriving, they just stop meaning the same thing.
 *
 * So [theClutterOnASampleIsTheEnginesOwnReading] compares the emitted attribute
 * against `GameState.clutter` read off the engine state that the same lock
 * wrote to disk. Its other half lives in `tools/balance`'s `ClutterParityTest`,
 * which pins the harness to the same property on the same cadence. Between
 * them the two ends of SPEC 4.4's requirement are nailed to one function.
 */
class RunSampleTest : CoroutineTest() {

    @Test
    fun aSampleFiresEveryTenthDropAndNotOnTheOnesBetween() = runUnitTest {
        recordingEvents { events ->
            playing(blocksDropped = DROPS_PER_SAMPLE - 3) {
                repeat(4) { dropOne() }

                val drops = events.named("run.sample").map { it["drop"] }
                assertEquals(listOf<Any?>(DROPS_PER_SAMPLE), drops, "sampled on the wrong drops")
            }
        }
    }

    /**
     * The parity assertion. `savedRun()` is written by the same `lock()` that
     * emitted the sample, from the same `engine` value, so its `state.clutter`
     * *is* the engine's own reading at the sampled drop.
     */
    @Test
    fun theClutterOnASampleIsTheEnginesOwnReading() = runUnitTest {
        recordingEvents { events ->
            playing(blocksDropped = DROPS_PER_SAMPLE - 1) {
                dropOne()

                val sample = assertNotNull(events.named("run.sample").singleOrNull())
                val engineState = assertNotNull(savedRun()).state
                assertEquals(engineState.clutter, sample["clutter"], "the live clutter is not the engine's")
                assertEquals(engineState.level, sample["level"])
                assertEquals(engineState.board.fillPercent, sample["fill_pct"])
            }
        }
    }

    /**
     * The volume claim, checked rather than asserted in prose. A run that goes
     * fifty drops must produce five sample records, not fifty — SPEC 17's
     * "every 10th drop" is a budget as much as a cadence, and an event that
     * fires per drop is the firehose `app-events.md` forbids.
     */
    @Test
    fun fiftyDropsProduceFiveRecordsRatherThanFifty() = runUnitTest {
        recordingEvents { events ->
            playing(blocksDropped = 0) {
                repeat(50) { dropOne() }

                assertTrue(
                    events.named("run.sample").size <= 5,
                    "fifty drops emitted ${events.named("run.sample").size} samples",
                )
            }
        }
    }
}

/** One whole drop: put the block down and let its cascade finish. */
private fun GameScenario.dropOne() {
    if (state.phase != GamePhase.Playing) return
    land()
    waitOutResolution()
}
