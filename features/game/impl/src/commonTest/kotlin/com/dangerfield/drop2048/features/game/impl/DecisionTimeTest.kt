package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **The instrument L41 has been waiting for.**
 *
 * `tools/balance` models the player as `decisionMillis` — the beat between a
 * block appearing and the first steer — and `tapMillis`, the gap between one
 * column step and the next. Swept across a plausible range, `decisionMillis`
 * moves Greedy's median level by five and its 1024 rate by a factor of three,
 * which is more than the drop clock and the entire spawn table put together.
 * Both numbers are guesses, and every clocked balance figure in this project is
 * conditional on them.
 *
 * So these tests are not about `DecisionTimer`. That class has no behaviour
 * worth asserting on its own — it is arithmetic. They are about the **game**
 * measuring the player: each one drives the real `GameViewModel`, moves the
 * injected clock by a known amount, and reads what came out of the planted log
 * tree. Delete `decisions.columnStep(...)` from `move` or `steerTo` and these
 * go red; nothing else in the repo does.
 */
class DecisionTimeTest : CoroutineTest() {

    /**
     * The headline number. Two hundred and fifty milliseconds pass between the
     * block becoming the player's and the first sideways step, and that is what
     * `run.end` reports — the live reading of the constant the harness guesses.
     */
    @Test
    fun theGapBetweenABlockArrivingAndTheFirstSteerIsWhatRunEndReports() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(2, 0)) {
                clock.advance(by = 250)
                act(GameAction.MoveLeft)
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(275L, end["steer_ms_p50"], "the 250-300ms bucket's midpoint")
                assertEquals(1, end["drops_steered"])
                assertEquals(0, end["drops_unsteered"])
            }
        }
    }

    /**
     * The second number: the gap between consecutive column steps, which is the
     * harness's `tapMillis`.
     */
    @Test
    fun theGapBetweenConsecutiveColumnStepsIsReportedSeparately() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(4, 0)) {
                clock.advance(by = 300)
                act(GameAction.MoveLeft)
                clock.advance(by = 120)
                act(GameAction.MoveLeft)
                clock.advance(by = 120)
                act(GameAction.MoveLeft)
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(125L, end["tap_gap_ms_p50"], "the 100-150ms bucket's midpoint")
                assertEquals(3, end["steps"], "three accepted column steps")
            }
        }
    }

    /**
     * A drag is not one tap.
     *
     * `DropClock` charges the modelled player `tapMillis` for **every column
     * step**, so a drag that carries the block three columns costs three taps
     * there and has to cost three here. Counting gestures instead would make
     * the live tap rate look three times slower than the number it is compared
     * against, on exactly the control most players use.
     */
    @Test
    fun aDragAcrossThreeColumnsCountsAsThreeSteps() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(4, 0)) {
                clock.advance(by = 200)
                act(GameAction.SteerTo(col = 1))
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(3, end["steps"], "a drag from column 4 to column 1")
            }
        }
    }

    /**
     * The censoring case, and the one that would quietly ruin the number.
     *
     * A block that spawns where the player wants it takes no input at all.
     * Recording that as a 0ms decision would make the population read three
     * times faster than it is; dropping it silently would throw away every easy
     * board. It is counted, separately, and contributes no time.
     */
    @Test
    fun aDropThePlayerNeverSteeredIsCountedAsUnsteeredRatherThanAsZero() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(2, 0)) {
                clock.advance(by = 900)
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(1, end["drops_unsteered"])
                assertEquals(0, end["drops_steered"])
                assertFalse("steer_ms_p50" in end, "an unsteered run reported a decision time")
            }
        }
    }

    /**
     * A drop the player walked away from is not a measurement.
     *
     * Without this, one backgrounding puts however long the phone was in a
     * pocket into the tail of the decision histogram, and it stays there for
     * the life of the dashboard.
     */
    @Test
    fun aDropInterruptedByAPauseIsDiscardedRatherThanMeasured() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(2, 0)) {
                background()
                clock.advance(by = 8L * 60 * 60 * 1000)
                act(GameAction.Resume)
                clock.advance(by = 200)
                act(GameAction.MoveLeft)
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(225L, end["steer_ms_p50"], "the eight-hour pause reached the histogram")
            }
        }
    }

    /**
     * SPEC 13's guided run has a frozen clock, a scripted board and a coach
     * mark asking the player to read. None of that is the quantity
     * `decisionMillis` models, and six records of it per install would be
     * enough to move the median.
     */
    @Test
    fun theTutorialIsNotMeasured() = runUnitTest {
        recordingEvents { events ->
            playing(teach = true, fallingAt = Cell(2, 0)) {
                act(GameAction.TutorialAdvance)
                clock.advance(by = 400)
                act(GameAction.MoveLeft)

                assertTrue(events.named("run.sample").isEmpty(), "a tutorial drop was sampled")
            }
        }
    }

    private companion object {
        /**
         * Every row but row zero, in values no spawned block can merge with, so
         * a lock in **any** column ends the run. `RunEndReportingTest`'s
         * one-tall column would let a test that steers sideways land safely and
         * then assert on a `run.end` that never fired.
         */
        val BrimmingBoard = """
            4  8  4  8  4
            8  4  8  4  8
            4  8  4  8  4
            8  4  8  4  8
            4  8  4  8  4
            8  4  8  4  8
            4  8  4  8  4
        """
    }
}
