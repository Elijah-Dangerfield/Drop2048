package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The resolution loop of SPEC 4.3 across more than one step: cascade numbering,
 * gravity feeding the next step's queue, the burst phase, and the 100-step cap
 * being reported rather than swallowed.
 *
 * NOT covered here: the point values themselves (`ScoringTest`) and the
 * individual priority clauses (`MergeRulesTest`).
 */
class CascadeLoopTest {

    @Test
    fun eachMergeInAChainCarriesTheNextCascadeStepNumber() {
        val board = boardOf(
            """
            2 . . . .
            2 . . . .
            4 . . . .
            8 . . . .
            """
        )
        val resolution = resolve(board, Cell(0, 4), lastDirection = null)
        val merges = resolution.steps.filterIsInstance<ResolutionStep.Merge>()

        assertEquals(listOf(1, 2, 3), merges.map { it.step })
        assertEquals(listOf(tier(4), tier(8), tier(16)), merges.map { it.result })
        assertEquals(tier(16), resolution.board[Cell(0, 7)]?.numberValue)
        assertEquals(1, resolution.board.blockCount)
    }

    @Test
    fun gravityFeedsTheNextStepsQueueAndIsRecordedForTheAnimation() {
        val board = boardOf(
            """
            . 8 . . .
            . 2 . . .
            2 2 . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        val gravity = resolution.steps.filterIsInstance<ResolutionStep.Gravity>().first()
        assertEquals(1, gravity.step)
        assertTrue(
            gravity.moves.any { it.from == Cell(1, 5) && it.to == Cell(1, 6) },
            "the 8 above the merge should settle into the hole it left: ${gravity.moves}",
        )
    }

    @Test
    fun aBlockMovedByGravityUsesDownLeftRightWithNoLastInputDirection() {
        val board = boardOf(
            """
            . 4 . . .
            . 2 . . .
            4 2 . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        val second = resolution.steps
            .filterIsInstance<ResolutionStep.Merge>()
            .single { it.step == 2 }
        assertEquals(Cell(1, 7), second.initiator)
        assertEquals(Cell(0, 7), second.partner, "the settled 4 reaches left, not right")
        assertEquals(tier(8), second.result)
    }

    @Test
    fun creatingATwoThousandAndFortyEightBurstsItsRowAndKeepsCascading() {
        val board = boardOf(
            """
            1024 . . . .
            1024 S S S S
            """
        )
        val resolution = resolve(board, Cell(0, 6), lastDirection = null)

        val burst = resolution.steps.filterIsInstance<ResolutionStep.Burst>().single()
        assertEquals(7, burst.row)
        assertEquals(5, burst.cleared.size, "the whole row goes, Stones included")
        assertTrue(resolution.board.isClear, "nothing should survive:\n${resolution.board}")
    }

    @Test
    fun theCascadeMultiplierDoesNotResetAcrossABurst() {
        val board = boardOf(
            """
            . . 2 . .
            . . 2 . .
            1024 . 4 . .
            1024 S 4 . .
            """
        )
        val resolution = Resolver.resolve(
            board,
            listOf(Seed(Cell(0, 6), null), Seed(Cell(2, 4), null)),
            EngineConfig.Default,
        )

        val afterBurst = resolution.steps
            .filterIsInstance<ResolutionStep.Merge>()
            .single { it.step == 2 }
        assertEquals(
            afterBurst.result.points * 2,
            afterBurst.points,
            "step 2 still multiplies by 2 after a row burst",
        )
    }

    @Test
    fun hittingTheStepCapIsReportedAsAFault() {
        val board = boardOf(
            """
            . . . . .
            2 . . . .
            2 . . . .
            4 . . . .
            """
        )
        val capped = EngineConfig.Default.copy(cascadeStepCap = 1)
        val resolution = Resolver.resolve(board, listOf(Seed(Cell(0, 5), null)), capped)

        assertEquals(Fault.CASCADE_STEP_CAP_EXCEEDED, resolution.fault)
        assertEquals(1, resolution.stepsTaken)
    }

    @Test
    fun aRealisticChainNeverGetsAnywhereNearTheCap() {
        val board = boardOf(
            """
            2 . . . .
            2 . . . .
            4 . . . .
            8 . . . .
            16 . . . .
            32 . . . .
            """
        )
        val resolution = resolve(board, Cell(0, 2), lastDirection = null)

        assertEquals(null, resolution.fault)
        assertTrue(resolution.stepsTaken < 10, "took ${resolution.stepsTaken} steps")
        assertEquals(tier(64), resolution.board[Cell(0, 7)]?.numberValue)
    }
}
