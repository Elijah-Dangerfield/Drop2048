package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SPEC 4.3's worked example: *a 4 lands between two 4s with an 8 below the left
 * one*. It produces an 8 and an untouched 4, then cascades to a 16 in the lower
 * cell.
 *
 * That chain only exists because the merged block lands in the **partner's**
 * cell, which is what pulls the new 8 over the 8 already sitting under the left
 * 4. Both arrangements are pinned here on purpose. The first is the canonical
 * example and must cascade; the second puts the pre-existing 8 under the landing
 * cell instead and must *stop* after one merge. Between them they fail loudly if
 * the position rule moves in either direction, rather than letting gameplay
 * change quietly.
 */
class PriorityOrderTest {

    @Test
    fun aFourLandingBetweenTwoFoursWithAnEightBelowTheLeftOneCascadesToSixteen() {
        val board = boardOf(
            """
            . 4 4 4 .
            . 8 S S .
            """
        )
        val resolution = resolve(board, Cell(2, 6), lastDirection = null)
        val merges = resolution.steps.filterIsInstance<ResolutionStep.Merge>()

        assertEquals(2, merges.size, "expected exactly two merges:\n${resolution.board}")

        val first = merges[0]
        assertEquals(1, first.step)
        assertEquals(Cell(1, 6), first.partner, "the left 4 is the first match, never the right one")
        assertEquals(Cell(1, 6), first.into, "the new 8 lands in the partner's cell")
        assertEquals(tier(8), first.result)

        val second = merges[1]
        assertEquals(2, second.step)
        assertEquals(Cell(1, 7), second.into, "the vertical merge resolves into the lower cell")
        assertEquals(tier(16), second.result)

        assertEquals(tier(16), resolution.board[Cell(1, 7)]?.numberValue)
        assertEquals(tier(4), resolution.board[Cell(3, 6)]?.numberValue, "the right 4 is untouched")
        assertNull(resolution.board[Cell(2, 6)], "the landing cell is emptied")
    }

    @Test
    fun withTheEightBelowTheLandingCellTheChainStopsAfterOneMerge() {
        val board = boardOf(
            """
            . 4 4 4 .
            . S 8 S .
            """
        )
        val resolution = resolve(board, Cell(2, 6), lastDirection = null)
        val merges = resolution.steps.filterIsInstance<ResolutionStep.Merge>()

        assertEquals(
            1,
            merges.size,
            "the new 8 goes to the partner's cell, out of reach of the 8 under the landing cell",
        )
        assertEquals(tier(8), resolution.board[Cell(1, 6)]?.numberValue)
        assertEquals(tier(8), resolution.board[Cell(2, 7)]?.numberValue)
        assertEquals(tier(4), resolution.board[Cell(3, 6)]?.numberValue)
    }
}
