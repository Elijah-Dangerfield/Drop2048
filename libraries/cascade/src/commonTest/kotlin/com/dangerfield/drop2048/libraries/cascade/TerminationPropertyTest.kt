package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC 18.14: total block count strictly decreases per merge, so a cascade must
 * terminate. The 100-step cap is a backstop, and hitting it is a bug.
 *
 * Two properties over thousands of random boards:
 *
 * 1. The cap is never approached, let alone hit.
 * 2. Every cascade step that merged, burst or detonated ends with strictly
 *    fewer blocks than it started with — and the transcript's own arithmetic
 *    agrees with the board. That second half is the useful part: it makes the
 *    transcript a *verified* account of what happened rather than a parallel
 *    story the UI is asked to trust.
 */
class TerminationPropertyTest {

    @Test
    fun everyCascadeTerminatesWellInsideTheStepCap() {
        var deepest = 0
        forEachRandomBoard(boards = 4_000) { board, seed ->
            val resolution = resolve(board, seed.cell, seed.lastDirection)

            assertEquals(
                null,
                resolution.fault,
                "faulted after ${resolution.stepsTaken} steps on\n$board",
            )
            assertTrue(
                resolution.stepsTaken <= EngineConfig.DEFAULT_STEP_CAP,
                "took ${resolution.stepsTaken} steps on\n$board",
            )
            if (resolution.stepsTaken > deepest) deepest = resolution.stepsTaken
        }
        assertTrue(deepest < 20, "deepest cascade seen was $deepest, which is close enough to 100 to worry")
    }

    @Test
    fun everyDestructiveStepStrictlyReducesTheBlockCountAndTheTranscriptAgrees() {
        forEachRandomBoard(boards = 4_000) { board, seed ->
            val resolution = resolve(board, seed.cell, seed.lastDirection)

            resolution.steps.groupBy { it.step }.forEach { (index, steps) ->
                val delta = steps.sumOf(::blockDelta)
                val destructive = steps.any {
                    it is ResolutionStep.Merge ||
                        it is ResolutionStep.Burst ||
                        it is ResolutionStep.Detonation
                }
                if (destructive) {
                    assertTrue(delta < 0, "step $index did not shrink the board on\n$board")
                }
            }

            assertEquals(
                board.blockCount + resolution.steps.sumOf(::blockDelta),
                resolution.board.blockCount,
                "the transcript does not account for the board it produced:\n$board\nbecame\n${resolution.board}",
            )
        }
    }

    private fun blockDelta(step: ResolutionStep): Int = when (step) {
        is ResolutionStep.Merge -> -1
        is ResolutionStep.Burst -> -step.cleared.size
        is ResolutionStep.Detonation -> -(step.destroyed.size + 1)
        else -> 0
    }

    private fun forEachRandomBoard(boards: Int, check: (Board, Seed) -> Unit) {
        var rng = Rng(0x0D20_2048L)
        repeat(boards) {
            rng = rng.next()
            val fill = 2 + rng.valueIn(30)
            val builder = RandomBoard(rng, fill)
            rng = builder.rng
            check(builder.board, builder.seed)
        }
    }

    /**
     * A settled board of plausible junk plus one cell nominated as the thing that
     * just landed. Values are drawn from the low tiers so matches are common —
     * a board of unique tiers would never cascade and would prove nothing.
     */
    private class RandomBoard(start: Rng, fill: Int) {
        var rng = start
        val board: Board
        val seed: Seed

        init {
            val cols = EngineConfig.DEFAULT_COLS
            val rows = EngineConfig.DEFAULT_ROWS
            val columns = MutableList(cols) { mutableListOf<Block>() }
            repeat(fill) {
                rng = rng.next()
                val col = rng.valueIn(cols)
                rng = rng.next()
                val roll = rng.valueIn(10)
                rng = rng.next()
                val block = when {
                    roll < 7 -> NumberBlock(BlockValue.entries[rng.valueIn(4)])
                    roll < 9 -> NumberBlock(BlockValue.entries[rng.valueIn(TIERS_AT_REST)])
                    else -> SpecialBlock(if (rng.valueIn(2) == 0) Special.STONE else Special.WILDCARD)
                }
                if (columns[col].size < rows) columns[col] += block
            }
            val cells = MutableList<Block?>(cols * rows) { null }
            columns.forEachIndexed { col, stack ->
                stack.forEachIndexed { depth, block ->
                    cells[(rows - 1 - depth) * cols + col] = block
                }
            }
            board = Board(cols, rows, cells)

            val occupied = board.occupiedCells
            rng = rng.next()
            val cell = if (occupied.isEmpty()) Cell(0, rows - 1) else occupied[rng.valueIn(occupied.size)]
            rng = rng.next()
            val direction = when (rng.valueIn(3)) {
                0 -> Direction.LEFT
                1 -> Direction.RIGHT
                else -> null
            }
            seed = Seed(cell, direction)
        }

        private companion object {
            /** 2048 is terminal and cannot exist at rest, so it is never generated. */
            val TIERS_AT_REST = BlockValue.entries.size - 1
        }
    }
}
