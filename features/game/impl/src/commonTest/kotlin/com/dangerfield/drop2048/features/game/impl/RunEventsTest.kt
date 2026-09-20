package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.SCENARIO_SEED
import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SPEC 17's run lifecycle, asserted at the call site rather than at the API.
 *
 * `BUILD-PLAN.md`'s C8 section names the failure these exist to prevent:
 * Sodogku shipped telemetry events that nothing emitted, and an event with no
 * call site is worse than no event because it looks like coverage. C9 proved
 * the technique (L55) — delete the line and check that the *caller's* test goes
 * red while the API's own test stays green. Every assertion here reads the
 * planted tree after driving the real `GameViewModel`; `AppEventsTest` in
 * `:libraries:core` stays green whatever happens to these.
 */
class RunEventsTest : CoroutineTest() {

    @Test
    fun startingARunReportsItsSeedAndLevel() = runUnitTest {
        recordingEvents { events ->
            playing(level = 4) {
                val start = assertNotNull(events.named("run.start").lastOrNull())
                assertEquals(4, start["level"])
                assertEquals(SCENARIO_SEED, start["seed"])
            }
        }
    }

    @Test
    fun aFinishedRunReportsEverythingSectionSeventeenAsksFor() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(2, 0)) {
                clock.advance(by = 30_000)
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(state.score, end["score"])
                assertEquals(state.level, end["level"])
                assertEquals(1, end["blocks"])
                assertEquals(30_000L, end["duration_ms"])
                assertEquals("ROW_ZERO_OCCUPIED", end["cause"])
                assertEquals(0, end["bursts"])
                assertEquals(true, end["recorded"])
                assertTrue("cascades_1" in end, "no cascade-depth histogram")
                assertTrue("highest_tier" in end)
            }
        }
    }

    /**
     * SPEC 17 asks for the seed, and it is the number that makes a reported run
     * replayable. It used to be omitted on a Daily run, because that seed was
     * the board the whole world was playing that day; D27 removed the mode, so
     * every run carries it.
     */
    @Test
    fun aFinishedRunCarriesItsSeed() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(2, 0)) {
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(SCENARIO_SEED, end["seed"])
            }
        }
    }

    /**
     * L63's other half. `debug_session` itself is stamped on every record by
     * `GrafanaLogTree` — one place, so no call site can forget it — and this is
     * the narrower flag the dashboards need beside it: whether the three writes
     * that claim a player did something actually happened. Without it, "no
     * `run_record` row was written" and "the row was written and never reached
     * us" are the same shape downstream.
     */
    @Test
    fun aDebugSessionsRunEndSaysNothingWasRecorded() = runUnitTest {
        recordingEvents { events ->
            playing(
                picture = BrimmingBoard,
                fallingAt = Cell(2, 0),
                debug = FakeDebugController(),
            ) {
                land()
                waitOutResolution()
                declineContinue()

                val end = assertNotNull(events.named("run.end").singleOrNull())
                assertEquals(false, end["recorded"], "a debug run claimed it was recorded")
                assertTrue(progress.recorded.isEmpty(), "and it should not have been")
            }
        }
    }

    @Test
    fun theFirstRunEverFinishedFiresTheFunnelEventExactlyOnce() = runUnitTest {
        recordingEvents { events ->
            playing(picture = BrimmingBoard, fallingAt = Cell(2, 0)) {
                land()
                waitOutResolution()
                declineContinue()
                assertEquals(1, events.named("funnel.first_run_completed").size)

                act(GameAction.Restart)
                land()
                waitOutResolution()
                declineContinue()
                assertEquals(
                    1,
                    events.named("funnel.first_run_completed").size,
                    "the second run claimed to be the first",
                )
            }
        }
    }

    /**
     * SPEC 17's tutorial funnel wants "step reached" as well as completed and
     * skipped. Without the first one, a player who quit on lesson one and a
     * player who never opened the app are the same row.
     */
    @Test
    fun eachTutorialBeatReportsItselfReached() = runUnitTest {
        recordingEvents { events ->
            playing(teach = true) {
                val reachedAtStart = events.named("tutorial.step_reached").size
                assertTrue(reachedAtStart >= 1, "arriving at lesson one reported nothing")

                act(GameAction.TutorialSkip)
                assertEquals(1, events.named("tutorial.skipped").size)
            }
        }
    }

    private companion object {
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
