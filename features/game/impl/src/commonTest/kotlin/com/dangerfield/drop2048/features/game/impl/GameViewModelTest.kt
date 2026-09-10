package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.ui.system.Cue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The clock, the lock delay and transcript playback — the three things
 * `:features:game` owns that `:libraries:cascade` does not.
 *
 * **Not covered here, on purpose:** every merge, burst, cascade and scoring rule.
 * Those are `:libraries:cascade`'s, tested against SPEC 4.3 and SPEC 18 one case
 * each, and re-asserting them through a ViewModel would only make them harder to
 * debug. What this file asserts is *when* the engine is called and what the
 * player can do while a resolution is on screen.
 *
 * Board pictures and the verbs come from [GameScenario].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest : CoroutineTest() {

    @Test
    fun tick_advancesTheBlockOneRow() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            assertFallingAt(col = 2, row = 0)
            tick()
            assertFallingAt(col = 2, row = 1)
            tick()
            assertFallingAt(col = 2, row = 2)
        }
    }

    @Test
    fun tick_atRest_doesNotLockBeforeTheLockDelay() = runUnitTest {
        playing(fallingAt = Cell(2, 7)) {
            tick()
            advance(ALMOST_LOCK_DELAY)

            assertPhase(GamePhase.Playing)
            assertFallingAt(col = 2, row = 7)
        }
    }

    @Test
    fun lockDelay_elapsing_locksTheBlock() = runUnitTest {
        playing(fallingAt = Cell(2, 7)) {
            tick()
            waitOutLockDelay()

            assertPhase(GamePhase.Resolving)
            waitOutResolution()
            assertState { assertEquals(NumberBlock(BlockValue.V2), it.board[Cell(2, 7)]) }
        }
    }

    /**
     * SPEC 6: one reset per drop, so it cannot be used to stall. The second move
     * is deliberately made after the first reset would have expired — if the
     * delay had restarted twice, the block would still be in flight.
     */
    @Test
    fun lockDelay_resetsExactlyOnce() = runUnitTest {
        playing(fallingAt = Cell(2, 7)) {
            tick()

            advance(ALMOST_LOCK_DELAY)
            act(GameAction.MoveLeft)
            assertTrue(!landedIn(row = 7), "locked before the first reset could apply")

            advance(ALMOST_LOCK_DELAY)
            assertTrue(!landedIn(row = 7), "the reset did not restart the delay")

            act(GameAction.MoveRight)
            advance(PAST_LOCK_DELAY)

            assertTrue(landedIn(row = 7), "a second reset was allowed and the block stalled")
        }
    }

    @Test
    fun inputDuringResolution_isDropped() = runUnitTest {
        playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            assertPhase(GamePhase.Resolving)

            val boardMidCascade = state.board
            act(GameAction.MoveLeft, GameAction.MoveRight, GameAction.Nudge)

            assertEquals(boardMidCascade, state.board, "board moved on input during resolution")
            assertNull(state.falling, "a block was accepted during resolution")
        }
    }

    /**
     * The move is still ignored *during* the cascade — the board does not move —
     * and it is applied to the block that spawns afterwards rather than thrown
     * away. See the KDoc on `bufferedMove`.
     */
    @Test
    fun moveDuringResolution_appliesToTheNextBlock() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            land()
            assertPhase(GamePhase.Resolving)

            act(GameAction.MoveLeft)
            assertNull(state.falling, "a block was accepted during resolution")

            waitOutResolution()

            assertPhase(GamePhase.Playing)
            assertFallingAt(col = 1, row = 0)
        }
    }

    @Test
    fun moveDuringResolution_keepsOnlyTheLastOne() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            land()
            act(GameAction.MoveLeft, GameAction.MoveLeft, GameAction.MoveRight)

            waitOutResolution()

            assertFallingAt(col = 3, row = 0)
        }
    }

    /**
     * A two-step cascade, stopped one frame in.
     *
     * It used to be a one-step merge asserted at the instant of the lock, which
     * worked because `HardDrop` locked without any virtual time passing. There is
     * no zero-time way to put a block down since decision D11 — [GameScenario.land]
     * has to wait out the lock delay — so the first frame has always been drawn by
     * the time the assertion runs. A deeper cascade is what restores the
     * distinction: one merge shown, the second still to come, and the resolved
     * board nowhere on screen yet.
     */
    @Test
    fun playback_walksTheTranscriptRatherThanJumpingToTheEnd() = runUnitTest {
        playing(
            picture = """
                .  .  2  .  .
                .  .  4  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()

            assertPhase(GamePhase.Resolving)
            assertEquals(
                NumberBlock(BlockValue.V4),
                state.board[Cell(2, 6)],
                "the first merge was drawn",
            )
            assertEquals(
                NumberBlock(BlockValue.V4),
                state.board[Cell(2, 7)],
                "and the second one had not been, so the resolved board was not published early",
            )

            waitOutResolution()
            assertPhase(GamePhase.Playing)
            assertEquals(NumberBlock(BlockValue.V8), state.board[Cell(2, 7)])
            assertTrue(
                cues.contains(Cue.Merge(step = 1)),
                "expected a step-1 merge cue during playback, got $cues",
            )
        }
    }

    @Test
    fun run_endsOnTheFrameTheLastCascadeFinishes() = runUnitTest {
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
            config = EngineConfig.Default.copy(rows = 8),
        ) {
            land()

            assertPhase(GamePhase.Resolving)

            waitOutResolution()
            assertPhase(GamePhase.StackedOut)
            assertTrue(cues.contains(Cue.StackedOut), "no stacked-out cue")
            assertTrue(state.best >= state.score, "best did not absorb the final score")
        }
    }

    /**
     * The header's BEST is the record the run is being played *against*, and it
     * only moves when the run store says it has.
     *
     * It used to be `maxOf(best, score)` on every publish (L44), so a player two
     * hundred points into a run they were never going to win watched the header
     * congratulate them on a record that was really just their own live score
     * being echoed back one line lower. It also cost the "new best!" comparison
     * its baseline, which is why `bestBeforeRun` had to exist beside it.
     */
    @Test
    fun best_isThePreRunRecordUntilTheRunEnds() = runUnitTest {
        playing(
            picture = """
                .  .  2  .  .
            """,
            fallingAt = Cell(2, 0),
            best = PreviousBest,
        ) {
            assertEquals(PreviousBest, state.best)

            land()
            waitOutResolution()

            assertTrue(state.score > 0, "the run scored something")
            assertTrue(state.score < PreviousBest, "and it is nowhere near the record")
            assertEquals(PreviousBest, state.best, "so the header still shows the record")
        }
    }

    /**
     * The other side of it: a run that really does beat the record says so, and
     * says so on the frame the run is recorded rather than while it is in flight.
     */
    @Test
    fun best_movesOnceTheRunThatBeatItIsRecorded() = runUnitTest {
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
            config = EngineConfig.Default.copy(rows = 8),
            best = 1L,
        ) {
            assertEquals(1L, state.best)

            land()
            waitOutResolution()

            assertPhase(GamePhase.StackedOut)
            assertEquals(state.score, state.best, "the record is the run that just set it")
            assertTrue(state.newBest, "and the sheet says so")
        }
    }

    @Test
    fun stackedOut_writesTheRunRecord() = runUnitTest {
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
            land()
            waitOutResolution()

            assertEquals(GamePhase.StackedOut, state.phase)
            val recorded = recordedRuns().single()
            assertEquals(state.score, recorded.score)
            assertEquals(state.score, state.best)
            assertNull(savedRun(), "a finished run must not be resumable")
        }
    }

    private companion object {
        /** Inside the 150ms window, so a pending lock is still pending. */
        const val ALMOST_LOCK_DELAY = 130L

        /** Past it from any point inside the window. */
        const val PAST_LOCK_DELAY = 160L

        /** Far enough to prove the fall accelerated, far short of the block landing. */
        const val SoftDropRows = 3

        /** A record no single scripted merge in these fixtures can get near. */
        const val PreviousBest = 50_000L
    }

    @Test
    fun pause_stopsTheClockAndResumeStartsItAgain() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            act(GameAction.Pause)
            assertPhase(GamePhase.Paused)

            tick(times = 4)
            assertFallingAt(col = 2, row = 0)

            act(GameAction.Resume)
            tick()
            assertFallingAt(col = 2, row = 1)
        }
    }

    /** SPEC 18.9: the cascade completes on resume, before input is accepted again. */
    @Test
    fun pause_midCascade_resumesTheCascadeRatherThanTheClock() = runUnitTest {
        playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            act(GameAction.Pause)
            assertPhase(GamePhase.Paused)

            act(GameAction.Resume)
            assertPhase(GamePhase.Resolving)

            waitOutResolution()
            assertPhase(GamePhase.Playing)
        }
    }

    /**
     * Advances three soft-drop rows, not one drop tick.
     *
     * A whole tick is twelve soft-drop rows at level 1, which is the block
     * landing, locking, resolving and the *next* one falling — the assertion then
     * passes for a reason that has nothing to do with soft drop, and breaks the
     * moment the level-1 interval moves. It did, in C1c.
     */
    @Test
    fun softDrop_shortensTheInterval() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            val softDropMillis = EngineConfig.Default.speed.softDropMsPerRow.toLong()
            act(GameAction.SoftDropStart)
            advance(softDropMillis * SoftDropRows + 1)

            assertTrue(
                state.falling!!.cell.row >= SoftDropRows,
                "soft drop did not accelerate the fall",
            )
        }
    }

    /**
     * Decision D11's ▼: two rows, no lock, no resolution.
     *
     * Deliberately asserted **without** advancing a drop tick. L31 is the warning:
     * `tick()` moves a whole drop interval, and at level 1 that is enough for a
     * nudged block to land, lock, resolve and be replaced — so a nudge test built
     * on `tick()` would be asserting about the next block, and would pass or fail
     * on the level-1 interval rather than on the nudge.
     */
    @Test
    fun nudge_advancesTwoRowsWithoutLocking() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            act(GameAction.Nudge)

            assertFallingAt(col = 2, row = EngineConfig.DEFAULT_NUDGE_ROWS)
            assertPhase(GamePhase.Playing)

            act(GameAction.Nudge)
            assertFallingAt(col = 2, row = EngineConfig.DEFAULT_NUDGE_ROWS * 2)
            assertPhase(GamePhase.Playing)
        }
    }

    /**
     * The nudge is not a hard drop, and the assertion that says so is the *floor*:
     * a block two rows above the stack takes one press, not a press and a landing.
     */
    @Test
    fun nudge_intoTheStack_stopsShortAndThenLocksOnTheDelay() = runUnitTest {
        playing(
            picture = """
                .  .  8  .  .
            """,
            fallingAt = Cell(2, 4),
        ) {
            act(GameAction.Nudge)

            assertFallingAt(col = 2, row = 6)
            assertPhase(GamePhase.Playing)

            act(GameAction.Nudge)
            assertFallingAt(col = 2, row = 6)
            assertPhase(GamePhase.Playing)

            waitOutLockDelay()
            assertPhase(GamePhase.Resolving)
        }
    }

    /**
     * SPEC 6's buffer covers sideways moves only. A ▼ replayed onto the block
     * that spawns after a cascade would drop it two rows into a board the player
     * has not seen settle.
     */
    @Test
    fun nudgeDuringResolution_isNotBuffered() = runUnitTest {
        playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            land()
            assertPhase(GamePhase.Resolving)

            act(GameAction.Nudge)
            waitOutResolution()

            assertFallingAt(col = 2, row = 0)
        }
    }

    @Test
    fun ghost_marksTheLandingCell() = runUnitTest {
        playing(
            picture = """
                .  .  8  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            assertEquals(Cell(2, 6), state.ghost)
        }
    }

    @Test
    fun aFreshRun_waitsOnTheStartOverlayAndDoesNotTickUnderIt() = runUnitTest {
        playing(fallingAt = Cell(2, 0), pressPlay = false) {
            assertPhase(GamePhase.Ready)
            tick(times = 3)
            assertFallingAt(col = 2, row = 0)

            act(GameAction.Start)
            assertPhase(GamePhase.Playing)
            tick()
            assertFallingAt(col = 2, row = 1)
        }
    }

    /**
     * The load-bearing half of decision D11's steering: the target column is
     * absolute, so asking for the same column twice is idempotent and asking for
     * the original column undoes the drag exactly.
     */
    @Test
    fun steerTo_movesToTheTargetColumnAndBack() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            act(GameAction.SteerTo(0))
            assertFallingAt(col = 0, row = 0)

            act(GameAction.SteerTo(0))
            assertFallingAt(col = 0, row = 0)

            act(GameAction.SteerTo(2))
            assertFallingAt(col = 2, row = 0)
        }
    }

    @Test
    fun steerTo_clampsToTheBoard() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            act(GameAction.SteerTo(99))
            assertFallingAt(col = 4, row = 0)
        }
    }

    /**
     * "Blocked by a placed tile at the tile's current row" — the block parks
     * against the obstruction rather than teleporting past it, which is the same
     * rule the arrow buttons obey because it is the same engine call.
     */
    @Test
    fun steerTo_stopsAtTheFirstBlockedColumn() = runUnitTest {
        playing(
            picture = """
                .  8  .  .  .
                .  .  .  .  .
                .  .  .  .  .
                .  .  .  .  .
                .  .  .  .  .
                .  .  .  .  .
                .  .  .  .  .
                .  .  .  .  .
            """,
            fallingAt = Cell(3, 0),
        ) {
            act(GameAction.SteerTo(0))
            assertFallingAt(col = 2, row = 0)
        }
    }

    @Test
    fun steerTo_duringAResolution_isIgnored() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            land()
            assertPhase(GamePhase.Resolving)
            act(GameAction.SteerTo(0))
            assertNull(state.falling)
        }
    }

    /**
     * The downward flick is the ▼ control by another gesture, so it has to be
     * the same action — the screen maps it to [GameAction.Nudge] rather than to
     * anything of its own. Asserting that here rather than in a gesture test is
     * deliberate: the recogniser's thresholds are the handoff's and are pinned in
     * `GameBoard`, but "a flick advances two rows and does not lock" is a rule.
     */
    @Test
    fun flick_advancesTwoRowsLikeTheNudgeControl() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            act(GameAction.Nudge)
            assertFallingAt(col = 2, row = 2)
            assertPhase(GamePhase.Playing)
        }
    }

    @Test
    fun aCascade_raisesACalloutAndBumpsItsNonce() = runUnitTest {
        playing(
            picture = """
                .  .  2  .  .
                .  .  4  .  .
            """,
            falling = NumberBlock(BlockValue.V2),
            fallingAt = Cell(2, 0),
        ) {
            val before = state.calloutNonce
            land()
            waitOutResolution()
            assertEquals(GameCallout.Chain(step = 2), state.callout)
            assertTrue(state.calloutNonce > before, "expected the callout nonce to move")
        }
    }
}
