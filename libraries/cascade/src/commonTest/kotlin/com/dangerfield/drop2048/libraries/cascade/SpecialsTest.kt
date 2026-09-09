package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC 5.2's three specials on the board: which neighbours a Wildcard is
 * eligible against, how a Bomb clears, and that a Stone is inert to everything
 * but a Bomb and a burst.
 *
 * The edge cases SPEC 18 names (Wildcard beside Wildcard, Wildcard beside
 * Stone, Bomb with no neighbours, burst clears a Stone) each have their own
 * named test in `EdgeCaseTest`; this file covers the behaviour those edges are
 * carved out of.
 */
class SpecialsTest {

    @Test
    fun aWildcardTakesItsFirstEligibleNeighboursValueDoubled() {
        val board = boardOf(
            """
            . W 8 . .
            . S S . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        val merge = resolution.steps.filterIsInstance<ResolutionStep.Merge>().single()
        assertEquals(MergeKind.WILDCARD, merge.kind)
        assertEquals(tier(16), merge.result)
        assertEquals(Cell(1, 6), merge.into)
    }

    @Test
    fun aWildcardFollowsTheSamePriorityOrderAsAValueBlock() {
        val board = boardOf(
            """
            4 W 8 . .
            . 2 . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = Direction.RIGHT)

        val merge = resolution.steps.filterIsInstance<ResolutionStep.Merge>().first()
        assertEquals(Cell(1, 7), merge.partner, "down still outranks the last input direction")
        assertEquals(tier(4), merge.result)
    }

    @Test
    fun aValueBlockLandingBesideARestingWildcardMergesWithItToo() {
        val board = boardOf(
            """
            W . . . .
            S S . . .
            """
        )
        val resolution = resolve(board.with(Cell(1, 6), value(8)), Cell(1, 6), lastDirection = null)

        val merge = resolution.steps.filterIsInstance<ResolutionStep.Merge>().single()
        assertEquals(MergeKind.WILDCARD, merge.kind)
        assertEquals(tier(16), merge.result)
        assertEquals(Cell(1, 6), merge.into, "the value block initiated, so it keeps its cell")
    }

    @Test
    fun aWildcardBesideA1024MakesA2048AndBurstsTheRow() {
        val board = boardOf(
            """
            W 1024 . . .
            S S S S S
            """
        )
        val resolution = resolve(board, Cell(0, 6), lastDirection = Direction.RIGHT)

        val burst = resolution.steps.filterIsInstance<ResolutionStep.Burst>().single()
        assertEquals(6, burst.row)
        assertTrue(resolution.board.rowCells(6).all { resolution.board[it] == null })
    }

    @Test
    fun anInertWildcardIsReEvaluatedWhenAnAdjacentCellChanges() {
        val board = boardOf(
            """
            . 2 . . .
            W 2 . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)
        val merges = resolution.steps.filterIsInstance<ResolutionStep.Merge>()

        assertEquals(2, merges.size, "the wildcard never moved, so only re-evaluation can reach it")
        assertEquals(MergeKind.WILDCARD, merges[1].kind)
        assertEquals(2, merges[1].step)
        assertEquals(tier(8), merges[1].result)
        assertEquals(tier(8), resolution.board[Cell(0, 7)]?.numberValue)
    }

    @Test
    fun aBombDestroysItselfAndItsFourOrthogonalNeighbours() {
        val board = boardOf(
            """
            . 4 . . .
            4 B 4 . .
            S S S S S
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        val detonation = resolution.steps.filterIsInstance<ResolutionStep.Detonation>().single()
        assertEquals(4, detonation.destroyed.size)
        assertTrue(resolution.board[Cell(1, 6)] == null)
        assertTrue(resolution.board[Cell(0, 6)] == null)
        assertTrue(resolution.board[Cell(2, 6)] == null)
        assertTrue(resolution.board[Cell(1, 5)] == null)
    }

    @Test
    fun aBombNeverMerges() {
        val board = boardOf(
            """
            . B . . .
            . B . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        assertTrue(resolution.steps.none { it is ResolutionStep.Merge })
        assertEquals(1, resolution.steps.filterIsInstance<ResolutionStep.Detonation>().size)
    }

    @Test
    fun aStoneSurvivesEverythingExceptABombAndABurst() {
        val untouched = resolve(boardOf("4 S 4 . ."), Cell(0, 7), lastDirection = null)
        assertEquals(SpecialBlock(Special.STONE), untouched.board[Cell(1, 7)])

        val bombed = resolve(
            boardOf(
                """
                . B . . .
                . S . . .
                """
            ),
            Cell(1, 6),
        )
        assertTrue(bombed.board.isClear, "\n${bombed.board}")
    }
}
