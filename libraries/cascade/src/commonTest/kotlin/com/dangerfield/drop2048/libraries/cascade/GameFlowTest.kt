package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The input alphabet and the state around a drop: movement, ticking, the hold
 * slot, the preview refilling, level advancement, and the danger state.
 *
 * NOT covered here: the drop timer, the lock delay and transcript playback.
 * Those are the ViewModel's (C3) — the engine has no idea time exists.
 */
class GameFlowTest {

    @Test
    fun aFreshRunStartsAtLevelOneWithAFullPreview() {
        val state = Cascade.newGame(seed = 1)

        assertEquals(1, state.level)
        assertEquals(0L, state.score)
        assertEquals(EngineConfig.DEFAULT_PREVIEW, state.preview.size)
        assertEquals(Cell(2, 0), state.falling?.cell)
        assertTrue(state.board.isClear)
    }

    @Test
    fun tickingMovesTheBlockDownOneRowAndStopsWhenItLands() {
        var state = Cascade.newGame(seed = 2)
        repeat(EngineConfig.DEFAULT_ROWS) { state = Cascade.apply(state, Input.Tick).state }

        assertEquals(EngineConfig.DEFAULT_ROWS - 1, state.falling?.cell?.row)

        val settled = Cascade.apply(state, Input.Tick)
        assertEquals(state, settled.state, "a tick at the floor changes nothing")
    }

    @Test
    fun movingRecordsTheLastInputDirectionAndIsRefusedAtTheWall() {
        val start = Cascade.newGame(seed = 3)
        val moved = Cascade.apply(start, Input.MoveLeft).state

        assertEquals(Direction.LEFT, moved.falling?.lastDirection)
        assertEquals(Cell(1, 0), moved.falling?.cell)

        val atWall = Cascade.apply(Cascade.apply(moved, Input.MoveLeft).state, Input.MoveLeft)
        assertTrue(atWall.isRejected)
        assertTrue(atWall.events.contains(GameEvent.Rejected(RejectionReason.MOVE_BLOCKED)))
    }

    @Test
    fun holdSwapsTheFallingBlockAndResetsOnTheNextDrop() {
        val start = Cascade.newGame(seed = 4)
        val first = start.falling?.block

        val held = Cascade.apply(start, Input.Hold).state
        val second = held.falling?.block
        val dropped = Cascade.apply(held, Input.HardDrop).state

        assertEquals(first, dropped.hold)
        assertEquals(false, dropped.holdUsedThisDrop, "the swap allowance resets each drop")

        val swapped = Cascade.apply(dropped, Input.Hold).state
        assertEquals(first, swapped.falling?.block, "the stashed block comes back out")
        assertEquals(dropped.falling?.block, swapped.hold)
        assertNotNull(second)
    }

    @Test
    fun holdCanBeTurnedOffEntirely() {
        val purist = Cascade.newGame(seed = 5, EngineConfig.Default.copy(holdEnabled = false))
        val refused = Cascade.apply(purist, Input.Hold)

        assertTrue(refused.events.contains(GameEvent.Rejected(RejectionReason.HOLD_DISABLED)))
        assertEquals(purist, refused.state)
    }

    @Test
    fun theLevelIsStoredAndAdvancesEveryTwentyDrops() {
        var state = stateOf(Board.empty(5, 8))
        val levelAfter = (1..40).map {
            state = drop(state, SpecialBlock(Special.BOMB), col = 2).state
            state.level
        }

        assertEquals(1, levelAfter[18], "still level 1 after 19 drops")
        assertEquals(2, levelAfter[19], "the 20th drop is the boundary")
        assertEquals(3, levelAfter[39])
        assertEquals(40, state.blocksDropped)
    }

    @Test
    fun theDangerStateFollowsRowOneAndIsAnnouncedOnItsEdges() {
        val nearlyFull = boardOf(
            """
            . . 2 . .
            4 8 16 32 64
            2 4 8 16 32
            4 8 16 32 64
            2 4 8 16 32
            4 8 16 32 64
            2 4 8 16 32
            """
        )
        val transition = drop(stateOf(nearlyFull), value(1024), col = 0)

        assertTrue(transition.state.inDanger, "\n${transition.state.board}")
        assertTrue(transition.events.contains(GameEvent.DangerEntered))
    }

    @Test
    fun onceTheRunIsOverEveryInputIsRefused() {
        val over = stateOf(Board.empty(5, 8)).copy(status = RunStatus.STACKED_OUT)

        listOf(Input.Tick, Input.MoveLeft, Input.HardDrop, Input.Hold, Input.Lock).forEach { input ->
            val transition = Cascade.apply(over, input)
            assertEquals(over, transition.state)
            assertTrue(transition.events.contains(GameEvent.Rejected(RejectionReason.RUN_OVER)))
        }
    }

    @Test
    fun theGhostOutlineReportsTheExactLandingCell() {
        val board = boardOf(
            """
            . . 8 . .
            . . 8 . .
            """
        )
        val state = stateOf(board, FallingBlock(value(2), Cell(2, 0)))

        assertEquals(Cell(2, 5), state.landingCell)

        val dropped = Cascade.apply(state, Input.HardDrop).state
        assertNull(dropped.board[Cell(2, 4)])
    }

    @Test
    fun lockingNeverLeavesABlockFloating() {
        val state = stateOf(boardOf("S S S S S"), FallingBlock(value(2), Cell(1, 2)))
        val locked = Cascade.apply(state, Input.Lock).state

        assertEquals(tier(2), locked.board[Cell(1, 6)]?.numberValue)
        assertNull(locked.board[Cell(1, 2)])
    }
}
