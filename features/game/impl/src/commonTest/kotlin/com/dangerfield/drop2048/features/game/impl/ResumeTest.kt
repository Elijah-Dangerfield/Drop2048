package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * SPEC 11 and SPEC 18.9: the run survives the app dying, and a cascade that was
 * on screen when it died finishes before the player gets the board back.
 *
 * The process is not really killed here — nothing in a unit test can do that.
 * What is simulated is the only thing that matters: **a second ViewModel is
 * built from nothing but what the first one wrote to the store.** If the first
 * one kept something in a field and never wrote it, the second one does not have
 * it, and that is exactly the bug this file exists to catch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeTest : CoroutineTest() {

    @Test
    fun aRunInProgress_isWrittenAsSoonAsItStarts() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            val saved = assertNotNull(savedRun(), "a live run must be resumable")
            assertEquals(GameScenario.SCENARIO_SEED, saved.seed)
            assertNull(saved.resolution, "nothing is resolving yet")
        }
    }

    @Test
    fun forceQuitMidRun_resumesTheSameBoardAndScore() = runUnitTest {
        val landed = playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            waitOutResolution()
            declineContinue()
            assertPhase(GamePhase.Playing)
            state.score to assertNotNull(savedRun())
        }

        playing(resume = landed.second) {
            assertPhase(GamePhase.Playing)
            assertEquals(landed.first, state.score, "score survived the resume")
            assertEquals(landed.second.state.board, state.board, "board survived the resume")
        }
    }

    /**
     * The tallies are the half of a run that is not in the engine, so they are
     * the half that a resume can silently lose. A resumed run that reported zero
     * merges would write a false `run_record` and nothing on screen would say so.
     */
    @Test
    fun forceQuitMidRun_keepsTheTalliesTheRunHadEarned() = runUnitTest {
        val saved = playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            waitOutResolution()
            declineContinue()
            assertNotNull(savedRun())
        }

        assertEquals(1, saved.tally.merges, "the 2 that merged with the 2")
        assertEquals(4, saved.tally.highestTier)

        playing(resume = saved) {
            assertEquals(4, state.biggestTier, "the resumed run still knows what it reached")
        }
    }

    /**
     * SPEC 18.9. The run is killed at the moment a cascade is on screen; the
     * cascade plays out on the next launch, and only then does the drop timer
     * start.
     */
    @Test
    fun forceQuitMidCascade_finishesTheCascadeBeforeAcceptingInput() = runUnitTest {
        val midCascade = playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
                .  .  4  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            assertPhase(GamePhase.Resolving)
            assertNotNull(savedRun())
        }

        val resolution = assertNotNull(midCascade.resolution, "the cascade went to disk")
        assertTrue(resolution.transcript.merges.size >= 2, "a two-step cascade was caught in flight")

        playing(resume = midCascade) {
            assertPhase(GamePhase.Resolving)
            assertNull(state.falling, "no block may be steered while the cascade replays")

            act(GameAction.MoveLeft)
            assertNull(state.falling, "input during resolution is still ignored")

            waitOutResolution()
            declineContinue()
            assertPhase(GamePhase.Playing)
            assertEquals(
                NumberBlock(BlockValue.V8),
                state.board[Cell(2, 7)],
                "the cascade the app died in the middle of finished",
            )
        }
    }

    /**
     * Backgrounding is a pause that also writes, and where playback got to goes
     * with it. Resuming from that snapshot must not replay the frames already
     * seen — a cascade that starts over on every app switch is the bug.
     */
    @Test
    fun backgroundingMidCascade_snapshotsWherePlaybackGotTo() = runUnitTest {
        playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
                .  .  4  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            advance(millis = 200)
            background()

            assertPhase(GamePhase.Paused)
            val resolution = assertNotNull(savedRun()?.resolution)
            assertTrue(resolution.frameIndex > 0, "playback had started")
            assertTrue(
                resolution.frameIndex < resolution.transcript.steps.size,
                "and had not finished",
            )
        }
    }

    /**
     * A run the player already lost is not a run to come back to. The save is
     * cleared at the end of the run, and a stale one that somehow survives is
     * dropped rather than restored onto a dead board.
     */
    @Test
    fun aFinishedRun_isNotResumed() = runUnitTest {
        val finished = playing(
            picture = """
                .  .  4  .  .
                .  .  8  .  .
                .  .  16 .  .
                .  .  32 .  .
                .  .  64 .  .
                .  .  128 . .
                .  .  256 . .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            waitOutResolution()
            declineContinue()
            assertPhase(GamePhase.StackedOut)
            assertNull(savedRun(), "the save is cleared when the run ends")
            savedRuns.stored
        }

        assertNull(finished)
    }

    /**
     * "Drop again" has to leave the sheet, and Restart has to leave the pause
     * menu. Both are the same action, and it published a board without ever
     * publishing a phase — so the new run ran underneath a scrim that eats
     * input, which is indistinguishable from the game having frozen.
     */
    @Test
    fun restart_leavesWhateverOverlayItWasCalledFrom() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            act(GameAction.Pause)
            assertPhase(GamePhase.Paused)

            act(GameAction.Restart)
            assertPhase(GamePhase.Playing)
        }
    }

    /**
     * A force-quit must not cost the run the time since its last pause. Without
     * this the saved tally only ever banks time at a pause, and a player who
     * never pauses reports a fraction of the playtime they actually spent.
     */
    @Test
    fun playtimeSoFar_isBankedOnEverySave() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            clock.advance(by = 25_000)
            land()
            waitOutResolution()
            declineContinue()

            assertEquals(25_000, assertNotNull(savedRun()).tally.playedMs)
        }
    }

    /**
     * SPEC 11's duration, and the reason the clock is injected: a run left
     * paused does not accumulate playtime.
     */
    @Test
    fun duration_countsPlayAndNotPauses() = runUnitTest {
        playing(
            picture = """
                .  .  4  .  .
                .  .  8  .  .
                .  .  16 .  .
                .  .  32 .  .
                .  .  64 .  .
                .  .  128 . .
                .  .  256 . .
            """,
            fallingAt = Cell(2, 0),
        ) {
            clock.advance(by = 30_000)
            act(GameAction.Pause)
            clock.advance(by = 3_600_000)
            act(GameAction.Resume)
            clock.advance(by = 10_000)

            land()
            waitOutResolution()
            declineContinue()

            assertEquals(40_000, recordedRuns().single().durationMs)
        }
    }
}
