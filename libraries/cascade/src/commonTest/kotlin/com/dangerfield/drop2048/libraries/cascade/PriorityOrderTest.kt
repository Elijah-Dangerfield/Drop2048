package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The worked example the build plan names: *a 4 lands between two 4s with an 8
 * below*. It has to produce an 8 and an untouched 4, then cascade to a 16 in
 * the lower cell.
 *
 * **The build plan writes it as "an 8 below the left one", and that phrasing is
 * not reachable under SPEC 4.3 as written.** SPEC 4.3 puts the new block in
 * "the initiating block's cell on a horizontal one", so the 8 that the merge
 * produces appears where the *falling* block landed, not where the left 4 was.
 * For the cascade to continue, the pre-existing 8 therefore has to be below the
 * landing cell, not below the left 4. Both arrangements are pinned here: the
 * first is the case the plan describes the outcome of, the second is what the
 * literal wording produces. If the position rule is ever changed to "the
 * partner's cell", the second test is the one that will fail and say so.
 */
class PriorityOrderTest {

    @Test
    fun aFourLandingBetweenTwoFoursWithAnEightBelowCascadesToSixteen() {
        val board = boardOf(
            """
            . 4 4 4 .
            . S 8 S .
            """
        )
        val resolution = resolve(board, Cell(2, 6), lastDirection = null)
        val merges = resolution.steps.filterIsInstance<ResolutionStep.Merge>()

        assertEquals(2, merges.size, "expected exactly two merges:\n${resolution.board}")

        val first = merges[0]
        assertEquals(1, first.step)
        assertEquals(Cell(1, 6), first.partner, "the left 4 is the first match, never the right one")
        assertEquals(Cell(2, 6), first.into)
        assertEquals(tier(8), first.result)

        val second = merges[1]
        assertEquals(2, second.step)
        assertEquals(Cell(2, 7), second.into, "the vertical merge resolves into the lower cell")
        assertEquals(tier(16), second.result)

        assertEquals(tier(16), resolution.board[Cell(2, 7)]?.numberValue)
        assertEquals(tier(4), resolution.board[Cell(3, 6)]?.numberValue, "the right 4 is untouched")
        assertNull(resolution.board[Cell(1, 6)], "the left 4 was consumed")
    }

    @Test
    fun withTheEightBelowTheLeftFourTheChainStopsAfterOneMerge() {
        val board = boardOf(
            """
            . 4 4 4 .
            . 8 S S .
            """
        )
        val resolution = resolve(board, Cell(2, 6), lastDirection = null)
        val merges = resolution.steps.filterIsInstance<ResolutionStep.Merge>()

        assertEquals(1, merges.size, "SPEC 4.3 puts the 8 on the landing cell, out of reach of the other 8")
        assertEquals(tier(8), resolution.board[Cell(2, 6)]?.numberValue)
        assertEquals(tier(8), resolution.board[Cell(1, 7)]?.numberValue)
        assertEquals(tier(4), resolution.board[Cell(3, 6)]?.numberValue)
    }
}
