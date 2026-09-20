package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The input alphabet and the state around a drop: movement, ticking, the ▼
 * hard drop, level advancement, and the danger state.
 *
 * NOT covered here: the drop timer, the lock delay and transcript playback.
 * Those are the ViewModel's (C3) — the engine has no idea time exists.
 */
class GameFlowTest {

    @Test
    fun aFreshRunStartsAtLevelOneWithOneBlockDrawnAndNothingQueued() {
        val state = Cascade.newGame(seed = 1)

        assertEquals(1, state.level)
        assertEquals(0L, state.score)
        assertEquals(1, state.drawsMade, "exactly one draw, and it is the block in flight")
        assertEquals(0, state.falling?.cell?.row, "a block always enters at the top")
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
        val start = stateOf(Board.empty(EngineConfig.DEFAULT_COLS, EngineConfig.DEFAULT_ROWS), FallingBlock(value(2), Cell(2, 0)))
        val moved = Cascade.apply(start, Input.MoveLeft).state

        assertEquals(Direction.LEFT, moved.falling?.lastDirection)
        assertEquals(Cell(1, 0), moved.falling?.cell)

        val atWall = Cascade.apply(Cascade.apply(moved, Input.MoveLeft).state, Input.MoveLeft)
        assertTrue(atWall.isRejected)
        assertTrue(atWall.events.contains(GameEvent.Rejected(RejectionReason.MOVE_BLOCKED)))
    }

    /** Decision D21: ▼ sends the block to the floor of its column and locks it. */
    @Test
    fun aHardDropFromTheSpawnRowLandsOnTheFloorAndEndsTheDrop() {
        val start = Cascade.newGame(seed = 4)
        val enteredIn = start.falling!!.cell.col
        val dropped = Cascade.apply(start, Input.Lock)

        assertEquals(
            start.falling?.block,
            dropped.state.board[Cell(enteredIn, EngineConfig.DEFAULT_ROWS - 1)],
            "the block the player committed is on the floor of the column it fell in",
        )
        assertEquals(start.blocksDropped + 1, dropped.state.blocksDropped)
        assertEquals(0, dropped.state.falling?.cell?.row, "and the next block has spawned at the top")
    }

    /**
     * A block already resting on the stack skips no rows, so the same input that
     * pays a bonus from row 0 pays nothing here. That is what keeps the bonus
     * attached to the *decision* rather than to the lock delay expiring.
     */
    @Test
    fun lockingARestingBlockPaysNoHardDropBonus() {
        val state = stateOf(boardOf("S S S S S"), FallingBlock(value(2), Cell(2, 6)))

        val locked = Cascade.apply(state, Input.Lock)

        assertEquals(
            emptyList(),
            locked.transcript.steps.filterIsInstance<ResolutionStep.HardDropBonus>(),
            "a block with nowhere left to fall was paid for falling",
        )
    }

    /**
     * Read off `blocksPerLevel` rather than off the number 20, which is what it
     * was until the 2026-09-20 pacing ruling moved it to 15. The rule SPEC 5.5
     * states is "every `blocksPerLevel` blocks", and a test that names the
     * current value instead fails on the next tune without anything being
     * broken.
     */
    @Test
    fun theLevelIsStoredAndAdvancesEveryBlocksPerLevelDrops() {
        val perLevel = EngineConfig.DEFAULT_BLOCKS_PER_LEVEL
        var state = stateOf(Board.empty(5, 8))
        val levelAfter = (1..perLevel * 2).map {
            state = drop(state, SpecialBlock(Special.BOMB), col = 2).state
            state.level
        }

        assertEquals(1, levelAfter[perLevel - 2], "still level 1 one drop short of the boundary")
        assertEquals(2, levelAfter[perLevel - 1], "the boundary drop advances the level")
        assertEquals(3, levelAfter[perLevel * 2 - 1])
        assertEquals(perLevel * 2, state.blocksDropped)
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

        listOf(Input.Tick, Input.MoveLeft, Input.MoveRight, Input.Lock).forEach { input ->
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

        val dropped = Cascade.apply(state, Input.Lock).state
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
