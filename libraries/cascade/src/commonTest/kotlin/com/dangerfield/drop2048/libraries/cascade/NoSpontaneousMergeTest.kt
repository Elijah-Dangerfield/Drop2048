package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * SPEC 4.3: *only a block that just moved or was just created by a merge may
 * initiate. Two blocks sitting untouched never spontaneously combine.*
 *
 * This is the rule that keeps the board legible — the player is the cause of
 * every change — and it is the easiest one to break in a later refactor,
 * because breaking it looks like a harmless "also check the neighbours".
 *
 * The property: for every merge in a transcript, the initiator must have been
 * eligible. At step 1 that means the cell that just landed. At step *n + 1* it
 * means a cell that step *n* either created by a merge or moved by gravity.
 * The set is rebuilt from the transcript alone, so the assertion does not
 * borrow the resolver's own bookkeeping.
 *
 * Wildcards are deliberately excluded from the generated boards. SPEC 5.2 gives
 * a resting Wildcard an explicit exemption — it is re-evaluated whenever an
 * adjacent cell changes — and `SpecialsTest` pins that exemption separately.
 */
class NoSpontaneousMergeTest {

    @Test
    fun noBlockEverInitiatesAMergeWithoutHavingMovedOrJustBeenCreated() {
        var rng = Rng(0xB10C_C0DEL)

        repeat(4_000) {
            rng = rng.next()
            val fill = 3 + rng.valueIn(28)
            val built = build(rng, fill)
            rng = built.rng

            val resolution = resolve(built.board, built.landed, built.direction)
            var eligible = setOf(built.landed)

            resolution.steps.groupBy { it.step }.entries.sortedBy { it.key }.forEach { (index, steps) ->
                steps.filterIsInstance<ResolutionStep.Merge>().forEach { merge ->
                    assertTrue(
                        merge.initiator in eligible,
                        "step $index: ${merge.initiator} merged without moving on\n${built.board}",
                    )
                }
                val moves = steps.filterIsInstance<ResolutionStep.Gravity>()
                    .flatMap { it.moves }
                    .associate { it.from to it.to }
                val created = steps.filterIsInstance<ResolutionStep.Merge>()
                    .map { moves[it.into] ?: it.into }
                eligible = (created + moves.values).toSet()
            }
        }
    }

    private class Built(val board: Board, val landed: Cell, val direction: Direction?, val rng: Rng)

    private fun build(start: Rng, fill: Int): Built {
        var rng = start
        val cols = EngineConfig.DEFAULT_COLS
        val rows = EngineConfig.DEFAULT_ROWS
        val columns = MutableList(cols) { mutableListOf<Block>() }

        repeat(fill) {
            rng = rng.next()
            val col = rng.valueIn(cols)
            rng = rng.next()
            val block = if (rng.valueIn(10) == 0) {
                SpecialBlock(Special.STONE)
            } else {
                NumberBlock(BlockValue.entries[rng.valueIn(5)])
            }
            if (columns[col].size < rows - 1) columns[col] += block
        }

        rng = rng.next()
        val landingCol = rng.valueIn(cols)
        rng = rng.next()
        val landed = NumberBlock(BlockValue.entries[rng.valueIn(5)])
        columns[landingCol] += landed

        val cells = MutableList<Block?>(cols * rows) { null }
        columns.forEachIndexed { col, stack ->
            stack.forEachIndexed { depth, block ->
                cells[(rows - 1 - depth) * cols + col] = block
            }
        }
        val landedCell = Cell(landingCol, rows - columns[landingCol].size)

        rng = rng.next()
        val direction = when (rng.valueIn(3)) {
            0 -> Direction.LEFT
            1 -> Direction.RIGHT
            else -> null
        }
        return Built(Board(cols, rows, cells), landedCell, direction, rng)
    }
}
