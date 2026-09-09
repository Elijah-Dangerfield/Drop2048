package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The grid primitives everything else is written against: coordinates, bounds,
 * landing rows, and the derived readings (`highestValue`, `clutter`) that the
 * spawn cap and the balance harness depend on.
 */
class BoardTest {

    @Test
    fun rowZeroIsTheTopAndGravityPullsTowardIncreasingRow() {
        val board = boardOf("2 . . . .")

        assertEquals(tier(2), board[Cell(0, 7)]?.numberValue)
        assertNull(board[Cell(0, 0)])
        assertEquals(Cell(0, 1), Cell(0, 0) + Direction.DOWN)
    }

    @Test
    fun outOfBoundsReadsAreNullRatherThanAThrow() {
        val board = Board.empty(5, 8)

        assertNull(board[Cell(-1, 0)])
        assertNull(board[Cell(5, 0)])
        assertNull(board[Cell(0, 8)])
        assertFalse(board.contains(Cell(0, -1)))
    }

    @Test
    fun aWriteOutsideTheBoardIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> {
            Board.empty(5, 8).with(Cell(9, 9), value(2))
        }
    }

    @Test
    fun theLandingRowIsTheLowestCellReachableWithoutPassingThroughAnything() {
        val board = boardOf(
            """
            . . 8 . .
            . . . . .
            . . 8 . .
            """
        )

        assertEquals(7, board.landingRow(col = 0, from = 0))
        assertEquals(4, board.landingRow(col = 2, from = 0), "blocked by the higher 8, not the lower one")
        assertEquals(6, board.landingRow(col = 2, from = 6))
    }

    @Test
    fun theHighestValueIgnoresSpecials() {
        val board = boardOf("S 2 W 16 B")

        assertEquals(BlockValue.V16, board.highestValue())
        assertNull(Board.empty(5, 8).highestValue())
    }

    @Test
    fun clutterCountsBlocksWithNoMatchingPartnerAnywhere() {
        val board = boardOf(
            """
            2 4 8 . .
            2 16 S . .
            """
        )

        assertEquals(3, stateOf(board).clutter, "the two 2s pair up; 4, 8 and 16 do not")
    }

    @Test
    fun aBoardRendersAsAPictureThatRoundTripsThroughTheFixture() {
        val picture = """
            . 2 . . .
            4 8 16 S W
        """.trimIndent()

        assertTrue(boardOf(picture).toString().endsWith(picture))
    }
}
