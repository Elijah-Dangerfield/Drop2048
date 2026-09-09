package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times

/**
 * The one number the whole board is drawn from.
 *
 * The design sets the board's `font-size` to 17px and then writes every
 * dimension on it in `em` — the gutter, the tile's hard shadow, the ghost's
 * dashes, the toast, the numerals. Change that one number and the board scales
 * as a unit, with nothing drifting out of proportion.
 *
 * That is worth reproducing rather than flattening into dp constants, and the
 * reason is a specific failure it prevents. Written as dp, a board on a small
 * phone gets smaller cells and a full-size gutter, so the gutter eats the tiles;
 * on a tablet it gets huge cells and a hairline gutter, so the tiles smear
 * together. Both look like a layout bug and neither is one — they are the
 * consequence of two numbers that were meant to move together being written
 * down separately.
 *
 * @param em the board's own type scale.
 * @param cell the edge of one square cell, which the board's *width* sets, not
 *   [em]. Genuinely a second number: a five-wide board on a narrow phone has a
 *   small cell and a normal em.
 */
@Immutable
data class BoardScale(val em: Dp = DefaultEm, val cell: Dp = DefaultCell) {

    /** The gutter around each cell. Half of it on each side of a shared edge. */
    val gutter: Dp get() = 0.16f * em

    /** How far a tile's hard shadow drops below its face. */
    val tileDepth: Dp get() = 0.26f * em

    /** The tile's inset top highlight. */
    val tileHighlight: Dp get() = 0.12f * em

    /** The landing ghost's dashed border. */
    val ghostStroke: Dp get() = 0.14f * em

    /** The `×2` on a will-merge ghost. */
    val ghostLabel: Dp get() = 1.1f * em

    /** `CHAIN ×3`, `ROW BUST!`, `LEVEL 4`. */
    val toast: Dp get() = 2.1f * em

    /**
     * The numeral on a tile holding [value].
     *
     * Shrinks with digit count so a 1024 and a 2 read as the same family of
     * object rather than as two components. The four steps are the handoff's.
     */
    fun numeral(value: Int): Dp = when (value.toString().length) {
        1, 2 -> 1.9f * em
        3 -> 1.5f * em
        4 -> 1.2f * em
        else -> 0.95f * em
    }
}

/**
 * Defaults to the design's own scale, so a preview, a screenshot test or a
 * single tile in a catalog page needs to provide nothing.
 */
val LocalBoardScale = staticCompositionLocalOf { BoardScale() }

/** The handoff's `font-size: 17px` on the board. */
private val DefaultEm: Dp = 17.dp

/**
 * A five-wide board inside the design's 370px maximum, minus its 8px well
 * padding. Only a default: the real value comes from measuring the board.
 */
private val DefaultCell: Dp = 70.dp
