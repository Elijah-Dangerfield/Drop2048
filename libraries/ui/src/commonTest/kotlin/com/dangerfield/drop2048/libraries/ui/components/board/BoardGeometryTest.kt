package com.dangerfield.drop2048.libraries.ui.components.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which cell a finger is on.
 *
 * The gesture that reads this cannot be unit tested, and every answer that
 * matters here is an edge: the gutter, the far side of the last row, a finger
 * that has left the board mid-stroke. Wrong by one and a drag moves the block
 * into the column beside the one under the thumb, which is the kind of bug that
 * reads as the board being possessed.
 */
class BoardGeometryTest {

    /** Four cells of 10px with a 2px gutter: 46px across in total. */
    private val square = BoardGeometry(cols = 4, rows = 4, cellPx = 10f, gapPx = 2f)

    /** The shape the game actually plays on (SPEC 3): five wide, eight tall. */
    private val board = BoardGeometry(cols = 5, rows = 8, cellPx = 10f, gapPx = 2f)

    @Test
    fun aPointInTheMiddleOfACellIsThatCell() {
        assertEquals(0, square.cellAt(x = 5f, y = 5f))
        assertEquals(5, square.cellAt(x = 17f, y = 17f))
        assertEquals(15, square.cellAt(x = 41f, y = 41f))
    }

    @Test
    fun theCellsAreNumberedRowMajor() {
        assertEquals(3, square.cellAt(x = 41f, y = 5f), "the top right cell")
        assertEquals(12, square.cellAt(x = 5f, y = 41f), "the bottom left cell")
    }

    @Test
    fun bothEdgesOfACellBelongToIt() {
        assertEquals(0, square.cellAt(x = 0f, y = 0f))
        assertEquals(0, square.cellAt(x = 10f, y = 10f))
    }

    /**
     * The gap between two cells is neither of them. Rounding it to the nearer one
     * would let a stroke travelling down a grid line move through a whole column
     * it never touched.
     */
    @Test
    fun theGutterIsNoCellAtAll() {
        assertNull(square.cellAt(x = 11f, y = 5f))
        assertNull(square.cellAt(x = 5f, y = 11f))
    }

    /**
     * A stroke that leaves the board keeps receiving events — the board captured
     * the pointer. Clamping to the nearest cell would move the block along the
     * outer edge while the finger was somewhere else entirely.
     */
    @Test
    fun aFingerPastTheLastCellIsOffTheBoard() {
        assertNull(square.cellAt(x = 48f, y = 5f))
        assertNull(square.cellAt(x = 5f, y = 48f))
        assertEquals(3, square.cellAt(x = 46f, y = 5f), "the last cell's own far edge")
    }

    @Test
    fun aFingerAboveOrLeftOfTheBoardIsOffItToo() {
        assertNull(square.cellAt(x = -1f, y = 5f))
        assertNull(square.cellAt(x = 5f, y = -1f))
    }

    /**
     * The reason this is not Sodogku's square-only geometry. A board five wide
     * and eight tall runs off the right at x=60 and keeps going to y=96, and a
     * geometry holding one `size` would answer both with the same number.
     */
    @Test
    fun theTwoAxesAreBoundedIndependently() {
        assertNull(board.cellAt(x = 60f, y = 5f), "there is no sixth column")
        assertEquals(35, board.cellAt(x = 5f, y = 89f), "but there is an eighth row")
        assertNull(board.cellAt(x = 5f, y = 96f))
        assertEquals(39, board.cellAt(x = 53f, y = 89f), "the bottom right cell")
    }

    /**
     * The falling block only ever needs a column: a drag moves it sideways and
     * how far down the finger is says nothing.
     */
    @Test
    fun aColumnCanBeReadWithoutARow() {
        assertEquals(0, board.columnAt(x = 5f))
        assertEquals(4, board.columnAt(x = 53f))
        assertNull(board.columnAt(x = 11f), "the gutter is no column either")
        assertNull(board.columnAt(x = 60f))
    }
}
