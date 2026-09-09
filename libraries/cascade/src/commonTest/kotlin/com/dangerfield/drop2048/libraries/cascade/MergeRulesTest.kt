package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SPEC 4.3's merge rules, one test per clause.
 *
 * Covered: the four-slot priority order and its dedup when the last input
 * direction is left or right, "never up", the partner's cell as the landing
 * cell in both orientations, which blocks may initiate, and the bottom-to-top /
 * left-to-right ordering of the merge queue.
 *
 * NOT covered here: multi-step cascades (`CascadeLoopTest`), scoring
 * (`ScoringTest`), Wildcard and Bomb eligibility (`SpecialsTest`), and the
 * property-level statement of "no spontaneous merges"
 * (`NoSpontaneousMergeTest`) — this file pins only the single case each of
 * those generalises.
 */
class MergeRulesTest {

    @Test
    fun downOutranksTheLastInputDirection() {
        val board = boardOf(
            """
            . 4 4 . .
            . S 4 S .
            """
        )
        val resolution = resolve(board, Cell(2, 6), lastDirection = Direction.LEFT)

        val merge = resolution.steps.filterIsInstance<ResolutionStep.Merge>().single()
        assertEquals(Cell(2, 7), merge.partner)
        assertEquals(Cell(2, 7), merge.into)
        assertEquals(tier(4), resolution.board[Cell(1, 6)]?.numberValue)
    }

    @Test
    fun theLastInputDirectionOutranksLeft() {
        val board = boardOf(
            """
            4 4 4 . .
            S S S . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = Direction.RIGHT)

        val merge = resolution.steps.filterIsInstance<ResolutionStep.Merge>().single()
        assertEquals(Cell(2, 6), merge.partner)
        assertEquals(Cell(2, 6), merge.into)
        assertEquals(tier(4), resolution.board[Cell(0, 6)]?.numberValue)
    }

    @Test
    fun leftOutranksRightWhenTheBlockWasNeverMoved() {
        val board = boardOf(
            """
            4 4 4 . .
            S S S . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        val merge = resolution.steps.filterIsInstance<ResolutionStep.Merge>().single()
        assertEquals(Cell(0, 6), merge.partner)
        assertEquals(tier(4), resolution.board[Cell(2, 6)]?.numberValue)
    }

    @Test
    fun aMergeNeverResolvesUpward() {
        val board = boardOf(
            """
            . 4 . . .
            . 4 . . .
            . S . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        assertTrue(resolution.steps.isEmpty(), "the lower block must not reach up:\n${resolution.board}")
    }

    @Test
    fun aVerticalMergeLandsInTheLowerCell() {
        val board = boardOf(
            """
            . . 4 . .
            . . 4 . .
            """
        )
        val resolution = resolve(board, Cell(2, 6), lastDirection = null)

        assertEquals(tier(8), resolution.board[Cell(2, 7)]?.numberValue)
        assertNull(resolution.board[Cell(2, 6)])
    }

    @Test
    fun aHorizontalMergeLandsInThePartnersCell() {
        val board = boardOf("4 4 . . .")
        val resolution = resolve(board, Cell(1, 7), lastDirection = null)

        assertEquals(tier(8), resolution.board[Cell(0, 7)]?.numberValue)
        assertNull(resolution.board[Cell(1, 7)])
    }

    @Test
    fun blocksThatWereNotTouchedDoNotCombine() {
        val board = boardOf(
            """
            . . 8 . .
            4 4 4 4 4
            """
        )
        val resolution = resolve(board, Cell(2, 6), lastDirection = null)

        assertTrue(resolution.steps.isEmpty(), "a row of matching 4s must sit still:\n${resolution.board}")
        assertEquals(board, resolution.board)
    }

    @Test
    fun aStoneNeverInitiatesAndIsNeverAPartner() {
        val board = boardOf(
            """
            . S . . .
            S S S . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        assertTrue(resolution.steps.isEmpty())
    }

    @Test
    fun theMergeQueueRunsBottomToTopThenLeftToRight() {
        val board = boardOf(
            """
            2 . . . .
            2 2 . . .
            """
        )
        val resolution = Resolver.resolve(
            board,
            listOf(Seed(Cell(0, 6), null), Seed(Cell(1, 7), null)),
            EngineConfig.Default,
        )

        val first = resolution.steps.filterIsInstance<ResolutionStep.Merge>().first()
        assertEquals(
            Cell(1, 7),
            first.initiator,
            "the lower seed resolves first:\n${resolution.board}",
        )
    }
}
