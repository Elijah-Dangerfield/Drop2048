package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

/**
 * A grid coordinate. `(0, 0)` is top-left and [row] increases downward, so
 * gravity pulls toward increasing [row] (SPEC 3.2).
 */
@Serializable
data class Cell(val col: Int, val row: Int) {
    operator fun plus(direction: Direction): Cell = Cell(col + direction.dCol, row + direction.dRow)

    override fun toString(): String = "($col,$row)"
}

/**
 * The three directions a merge may resolve in. There is deliberately no `UP`:
 * SPEC 4.3's priority order is "first match only, **never up**".
 */
@Serializable
enum class Direction(val dCol: Int, val dRow: Int) {
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
}

/**
 * The playfield. Immutable; every mutation returns a new [Board].
 *
 * Cells are stored row-major (`row * cols + col`). Row 0 is the top and is a
 * normal cell during play — the stacked-out check reads it only after a
 * resolution has fully completed (SPEC 3.1).
 */
@Serializable
data class Board(
    val cols: Int,
    val rows: Int,
    val cells: List<Block?>,
) {
    init {
        require(cols > 0 && rows > 0) { "Board must be at least 1x1, was ${cols}x$rows" }
        require(cells.size == cols * rows) {
            "Expected ${cols * rows} cells for a ${cols}x$rows board, got ${cells.size}"
        }
    }

    operator fun get(cell: Cell): Block? = if (contains(cell)) cells[index(cell)] else null

    operator fun get(col: Int, row: Int): Block? = get(Cell(col, row))

    fun contains(cell: Cell): Boolean = cell.col in 0 until cols && cell.row in 0 until rows

    fun isEmpty(cell: Cell): Boolean = contains(cell) && cells[index(cell)] == null

    fun with(cell: Cell, block: Block?): Board {
        require(contains(cell)) { "$cell is outside a ${cols}x$rows board" }
        val next = cells.toMutableList()
        next[index(cell)] = block
        return copy(cells = next)
    }

    fun withAll(changes: Map<Cell, Block?>): Board {
        if (changes.isEmpty()) return this
        val next = cells.toMutableList()
        changes.forEach { (cell, block) ->
            require(contains(cell)) { "$cell is outside a ${cols}x$rows board" }
            next[index(cell)] = block
        }
        return copy(cells = next)
    }

    val blockCount: Int get() = cells.count { it != null }

    val isClear: Boolean get() = cells.all { it == null }

    val occupiedCells: List<Cell>
        get() = cells.indices.filter { cells[it] != null }.map { cellAt(it) }

    fun rowCells(row: Int): List<Cell> = (0 until cols).map { Cell(it, row) }

    fun isRowOccupied(row: Int): Boolean = rowCells(row).any { get(it) != null }

    /** The highest value tier resting on the board, or null if there is none. */
    fun highestValue(): BlockValue? = cells.mapNotNull { it?.numberValue }.maxByOrNull { it.ordinal }

    /**
     * The lowest row in [col] a block at [from] can fall to without passing
     * through anything. Returns [from] when the cell directly below is occupied.
     */
    fun landingRow(col: Int, from: Int = 0): Int {
        var row = from
        while (row + 1 < rows && get(col, row + 1) == null) row++
        return row
    }

    /** The up-to-four orthogonally adjacent in-bounds cells. */
    fun neighborsOf(cell: Cell): List<Cell> =
        ORTHOGONAL.map { Cell(cell.col + it.first, cell.row + it.second) }.filter { contains(it) }

    override fun toString(): String = (0 until rows).joinToString("\n") { row ->
        (0 until cols).joinToString(" ") { col -> render(get(col, row)) }
    }

    private fun index(cell: Cell): Int = cell.row * cols + cell.col

    private fun cellAt(index: Int): Cell = Cell(index % cols, index / cols)

    companion object {
        fun empty(cols: Int, rows: Int): Board = Board(cols, rows, List(cols * rows) { null })

        private val ORTHOGONAL = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)

        private fun render(block: Block?): String = when (block) {
            null -> "."
            is NumberBlock -> block.value.points.toString()
            is SpecialBlock -> when (block.special) {
                Special.WILDCARD -> "W"
                Special.BOMB -> "B"
                Special.STONE -> "S"
            }
        }
    }
}
