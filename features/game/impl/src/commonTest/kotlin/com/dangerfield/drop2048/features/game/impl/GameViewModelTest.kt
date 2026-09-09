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
            act(GameAction.HardDrop)
            assertPhase(GamePhase.Resolving)

            val boardMidCascade = state.board
            act(GameAction.MoveLeft, GameAction.MoveRight, GameAction.HardDrop, GameAction.Hold)

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
            act(GameAction.HardDrop)
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
            act(GameAction.HardDrop)
            act(GameAction.MoveLeft, GameAction.MoveLeft, GameAction.MoveRight)

            waitOutResolution()

            assertFallingAt(col = 3, row = 0)
        }
    }

    @Test
    fun playback_walksTheTranscriptRatherThanJumpingToTheEnd() = runUnitTest {
        playing(
            picture = """
                .  .  .  .  .
                .  .  2  .  .
            """,
            fallingAt = Cell(2, 0),
        ) {
            act(GameAction.HardDrop)

            assertPhase(GamePhase.Resolving)
            assertEquals(
                NumberBlock(BlockValue.V2),
                state.board[Cell(2, 7)],
                "the merged board was published before the merge was drawn",
            )
            assertEquals(NumberBlock(BlockValue.V2), state.board[Cell(2, 6)])

            waitOutResolution()
            assertPhase(GamePhase.Playing)
            assertEquals(NumberBlock(BlockValue.V4), state.board[Cell(2, 7)])
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
            act(GameAction.HardDrop)

            assertPhase(GamePhase.Resolving)

            waitOutResolution()
            assertPhase(GamePhase.StackedOut)
            assertTrue(cues.contains(Cue.StackedOut), "no stacked-out cue")
            assertTrue(state.best >= state.score, "best did not absorb the final score")
        }
    }

    @Test
    fun stackedOut_persistsTheBestScore() = runUnitTest {
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
            act(GameAction.HardDrop)
            waitOutResolution()

            assertEquals(GamePhase.StackedOut, state.phase)
            assertEquals(state.score, persistedBest())
        }
    }

    private companion object {
        /** Inside the 150ms window, so a pending lock is still pending. */
        const val ALMOST_LOCK_DELAY = 130L

        /** Past it from any point inside the window. */
        const val PAST_LOCK_DELAY = 160L

        /** Far enough to prove the fall accelerated, far short of the block landing. */
        const val SoftDropRows = 3
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
            act(GameAction.HardDrop)
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

    @Test
    fun hold_swapsOncePerDrop() = runUnitTest {
        playing(
            falling = NumberBlock(BlockValue.V2),
            preview = listOf(NumberBlock(BlockValue.V16), NumberBlock(BlockValue.V32)),
        ) {
            act(GameAction.Hold)

            assertEquals(NumberBlock(BlockValue.V2), state.hold)
            assertEquals(NumberBlock(BlockValue.V16), state.falling?.block)
            assertEquals(false, state.canHold)

            act(GameAction.Hold)
            assertEquals(NumberBlock(BlockValue.V16), state.falling?.block, "hold was used twice")
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
}
