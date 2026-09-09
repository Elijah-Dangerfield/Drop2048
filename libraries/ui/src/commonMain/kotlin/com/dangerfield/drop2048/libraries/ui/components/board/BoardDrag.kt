package com.dangerfield.drop2048.libraries.ui.components.board

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp

/**
 * Where each cell of the board sits, in pixels.
 *
 * Cells are numbered row-major, and [cols] and [rows] are separate because this
 * board is not square: SPEC 3 says five columns by eight rows, and a geometry
 * that took one `size` would be wrong on every axis at once the moment somebody
 * used it.
 *
 * A value type with the arithmetic on it, rather than the arithmetic inlined in
 * the gesture, because the gesture cannot be unit tested and this can: every
 * interesting answer here is an edge — the gutter, the last row, a finger that
 * has left the board — and those are the answers that decide whether a drag moves
 * the block to the column under the thumb or the one next to it.
 */
@Immutable
data class BoardGeometry(
    val cols: Int,
    val rows: Int,
    val cellPx: Float,
    val gapPx: Float,
) {
    private val pitch: Float get() = cellPx + gapPx

    /** The cell at [x], [y], or null for the gutter and for off the board. */
    fun cellAt(x: Float, y: Float): Int? {
        val row = trackAt(y, rows) ?: return null
        val column = trackAt(x, cols) ?: return null
        return row * cols + column
    }

    /** The column at [x], ignoring how far down the board the finger is. */
    fun columnAt(x: Float): Int? = trackAt(x, cols)

    /**
     * Which row or column a coordinate falls in, or null for neither.
     *
     * The gutter belongs to no cell rather than to the nearer one. Rounding it
     * would let a stroke travelling down a grid line move through a column it
     * never actually touched, and the gutter is only a couple of dp wide — a
     * finger that meant a cell is already on one.
     */
    private fun trackAt(position: Float, tracks: Int): Int? {
        if (position < 0f) return null
        val index = (position / pitch).toInt()
        if (index >= tracks) return null
        return index.takeIf { position - it * pitch <= cellPx }
    }
}

/**
 * A drag across the board, reported cell by cell.
 *
 * The gesture lives on the container rather than on each cell, because a pointer
 * that has left a cell stops being that cell's business — the run of cells a
 * stroke crosses is only knowable from above.
 *
 * ### Why it waits for a second cell
 *
 * Nothing is reported, and nothing is consumed, until the stroke reaches a
 * *different* cell from the one it started on. Until then the children own the
 * gesture exactly as they did before this existed: a tap is a tap, however much
 * the thumb rolls while making it. A drag detector that claimed the gesture at
 * touch slop would turn every slightly sloppy tap into a one-cell drag.
 *
 * The cost is that a one-cell drag does nothing. That is the right trade: a
 * one-cell drag is a tap, and a tap already does something.
 *
 * Once the stroke does claim the gesture it consumes every change, which cancels
 * the tap the starting cell was still holding — so the cell under the thumb when
 * the finger went down is reported by [onDragStart] and never also tapped.
 *
 * ### What the loop guards
 *
 * `requireUnconsumed = false` on the down: the cell under the thumb has already
 * consumed it for its own tap detector, and is meant to keep it. Watching a
 * gesture is not the same as claiming it. The matching guard is the break when
 * somebody else consumes a change before this became a drag — nothing has been
 * reported yet, so there is nothing to undo.
 *
 * Touch slop as well as a change of cell, for a thumb that goes down a pixel from
 * a boundary: without it the smallest wobble on such a cell would be a drag
 * rather than the tap it was.
 *
 * `last` is null when the thumb went down in the gutter, where there is no cell
 * to have started on. The first cell the stroke reaches becomes the start
 * instead.
 *
 * @param cellSize the side of one cell, as laid out.
 * @param gap the gutter between two cells.
 * @param onDragStart the cell the stroke began on, reported once the stroke has
 *   committed to being a drag.
 * @param onDragEnter every further cell the stroke crosses, in order, once each
 *   per entry.
 */
@Suppress("LongParameterList")
fun Modifier.dragAcrossCells(
    cols: Int,
    rows: Int,
    cellSize: Dp,
    gap: Dp,
    enabled: Boolean = true,
    onDragStart: (cell: Int) -> Unit,
    onDragEnter: (cell: Int) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(cols, rows, cellSize, gap, enabled) {
    if (!enabled) return@pointerInput
    val grid = BoardGeometry(cols, rows, cellSize.toPx(), gap.toPx())
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var last = grid.cellAt(down.position.x, down.position.y)
        var travelled = 0f
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUpIgnoreConsumed()) break
            if (!dragging && change.isConsumed) break

            travelled += change.positionChange().getDistance()
            val cell = grid.cellAt(change.position.x, change.position.y)
            if (cell != null && cell != last && travelled >= slop) {
                if (dragging) {
                    onDragEnter(cell)
                } else {
                    dragging = true
                    onDragStart(last ?: cell)
                    if (last != null) onDragEnter(cell)
                }
                last = cell
            }
            if (dragging) change.consume()
        }
        if (dragging) onDragEnd()
    }
}
