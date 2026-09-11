package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.features.debug.PresetBoard
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.blockOf
import com.dangerfield.drop2048.libraries.cascade.numberValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A preset that does not do what its name says is worse than no preset, because
 * the whole value of a named board is that "One away from 2048" means the same
 * thing to two people on a call.
 *
 * So these are tested by *playing* them rather than by counting cells: the
 * cascade preset has to cascade, the burst preset has to burst, and the danger
 * preset has to put the run in danger the moment it starts.
 */
class PresetBoardTest {

    private val config = EngineConfig.Default

    @Test
    fun `every preset fits the board it was asked for`() {
        listOf(5 to 8, 5 to 7, 6 to 10).forEach { (cols, rows) ->
            PresetBoard.entries.forEach { preset ->
                val board = preset.boardFor(cols, rows)
                assertEquals(cols, board.cols, "$preset at ${cols}x$rows")
                assertEquals(rows, board.rows, "$preset at ${cols}x$rows")
            }
        }
    }

    /**
     * `board.rows` is a remote key (SPEC 10). A preset written against eight rows
     * would silently become a different scenario the first time it moved, so
     * every one is checked at a shape the config can actually express.
     */
    @Test
    fun `the spawn cell is free on every preset at every shape`() {
        listOf(5 to 8, 5 to 7, 6 to 10).forEach { (cols, rows) ->
            val spawn = Cell(cols / 2, 0)
            PresetBoard.entries.forEach { preset ->
                assertTrue(
                    preset.boardFor(cols, rows)[spawn] == null,
                    "$preset blocks the spawn cell at ${cols}x$rows",
                )
            }
        }
    }

    /**
     * Nothing may be floating: the engine only settles under gravity during a
     * resolution, so a preset with a hole beneath it sits there looking like a
     * rendering bug until the next merge.
     */
    @Test
    fun `no preset leaves a block hanging over an empty cell`() {
        PresetBoard.entries.forEach { preset ->
            val board = preset.boardFor(config.cols, config.rows)
            board.occupiedCells.forEach { cell ->
                if (cell.row + 1 < board.rows) {
                    assertTrue(
                        board[Cell(cell.col, cell.row + 1)] != null,
                        "$preset floats a block at $cell",
                    )
                }
            }
        }
    }

    @Test
    fun `the danger preset starts the run in danger`() {
        val board = PresetBoard.Danger.boardFor(config.cols, config.rows)
        assertTrue(board.isRowOccupied(1))
    }

    /**
     * The point of the burst preset: one hand-placed 1024 finishes the row and
     * the engine bursts it. If the preset ever stops setting that up, this fails
     * rather than a tester discovering it at 3am.
     */
    @Test
    fun `one away from 2048 bursts on the block it is one away from`() {
        val board = PresetBoard.OneAwayFrom2048.boardFor(config.cols, config.rows)
        val floor = config.rows - 1
        val start = Cascade.newGame(seed = 7, config = config).let { fresh ->
            fresh.copy(
                board = board,
                falling = fresh.falling?.copy(
                    block = blockOf(BlockValue.V1024),
                    cell = Cell(0, 0),
                ),
            )
        }

        // The two 512s merge into a 1024 next to the existing one, and that pair
        // makes the 2048 that clears the row.
        val afterFirst = Cascade.apply(start, Input.Lock)
        assertTrue(
            afterFirst.transcript.bursts.isNotEmpty(),
            "no burst; board was\n${afterFirst.state.board}",
        )
        assertEquals(floor, afterFirst.transcript.bursts.single().row)
    }

    /**
     * The point of the chain preset: a 2 dropped on it merges up through the
     * tiers, one cascade step per rung. Anything less and "force an N-step
     * cascade" is a name for a board that does not cascade.
     */
    @Test
    fun `the cascade chain actually chains`() {
        val board = PresetBoard.CascadeChain.boardFor(config.cols, config.rows)
        val start = Cascade.newGame(seed = 11, config = config).let { fresh ->
            fresh.copy(
                board = board,
                falling = fresh.falling?.copy(
                    block = blockOf(BlockValue.V2),
                    cell = Cell(config.spawnColumn, 0),
                ),
            )
        }

        val transcript = Cascade.apply(start, Input.Lock).transcript

        assertTrue(transcript.depth >= MinimumChain, "depth was ${transcript.depth}")
        assertTrue(transcript.merges.size >= MinimumChain, "merges were ${transcript.merges.size}")
    }

    @Test
    fun `nearly full leaves exactly the two rows it promises`() {
        val board = PresetBoard.NearlyFull.boardFor(config.cols, config.rows)
        assertTrue((0 until config.cols).all { board[Cell(it, 0)] == null })
        assertTrue((0 until config.cols).all { board[Cell(it, 1)] == null })
        assertTrue(board.isRowOccupied(2))
    }

    @Test
    fun `stone heavy is mostly stones`() {
        val board = PresetBoard.StoneHeavy.boardFor(config.cols, config.rows)
        val stones = board.occupiedCells.count { board[it]?.numberValue == null }
        assertTrue(stones > board.occupiedCells.size / 2, "only $stones of ${board.occupiedCells.size}")
    }

    private companion object {
        /**
         * Four rungs is enough to be a chain rather than a merge, and is what the
         * shortest board the config can express still fits.
         */
        const val MinimumChain = 4
    }
}
