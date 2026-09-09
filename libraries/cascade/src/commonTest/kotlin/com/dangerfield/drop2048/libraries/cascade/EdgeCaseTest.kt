package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SPEC 18, one test per numbered case, named for the case.
 *
 * These are the fourteen things the spec decided in advance because every one
 * of them comes up. A failure here is a design regression, not a bug in a
 * helper, so each test asserts the *decision* rather than the implementation
 * detail that currently delivers it.
 */
class EdgeCaseTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun case01_spawningIntoAnOccupiedCellEndsTheRunAndReportsAFault() {
        val everyDrop = playUntilOver(seed = 20_260_909L)
        assertTrue(
            everyDrop.none { it.faults.contains(Fault.SPAWN_INTO_OCCUPIED_CELL) },
            "the stacked-out check should make this unreachable",
        )

        val occupiedTop = stateOf(
            boardOf("2 2 2 2 2", align = Align.TOP),
            config = EngineConfig.Default.copy(continueRowsCleared = 0),
        )
        val forced = Cascade.continueRun(occupiedTop)

        assertTrue(forced.faults.contains(Fault.SPAWN_INTO_OCCUPIED_CELL))
        assertEquals(RunStatus.STACKED_OUT, forced.state.status)
        assertEquals(DeathCause.SPAWN_BLOCKED, forced.state.deathCause)
    }

    @Test
    fun case02_aWildcardBesideAWildcardDoesNotMergeAndBothStayInert() {
        val board = boardOf(
            """
            W W . . .
            S S . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        assertTrue(resolution.steps.isEmpty(), "\n${resolution.board}")
        assertEquals(SpecialBlock(Special.WILDCARD), resolution.board[Cell(0, 6)])
        assertEquals(SpecialBlock(Special.WILDCARD), resolution.board[Cell(1, 6)])
    }

    @Test
    fun case03_aStoneIsNotEligibleForAWildcardAndIsSkippedInThePriorityOrder() {
        val board = boardOf(
            """
            4 W . . .
            S S . . .
            """
        )
        val resolution = resolve(board, Cell(1, 6), lastDirection = null)

        val merge = resolution.steps.filterIsInstance<ResolutionStep.Merge>().single()
        assertEquals(Cell(0, 6), merge.partner, "down was a Stone, so the order moved on to left")
        assertEquals(tier(8), merge.result)
    }

    @Test
    fun case04_aBombWithNoNeighboursDestroysOnlyItselfAndScoresNothing() {
        val board = boardOf("B . . . S")
        val resolution = resolve(board, Cell(0, 7), lastDirection = null)

        val detonation = resolution.steps.filterIsInstance<ResolutionStep.Detonation>().single()
        assertTrue(detonation.destroyed.isEmpty())
        assertEquals(0, detonation.points)
        assertNull(resolution.board[Cell(0, 7)])
        assertEquals(SpecialBlock(Special.STONE), resolution.board[Cell(4, 7)])
    }

    @Test
    fun case05_aBurstClearsAStone() {
        val board = boardOf(
            """
            1024 . . . .
            1024 S S S S
            """
        )
        val resolution = resolve(board, Cell(0, 6), lastDirection = null)

        assertTrue(resolution.board.isClear, "four Stones went with the burst:\n${resolution.board}")
        assertEquals(5, resolution.steps.filterIsInstance<ResolutionStep.Burst>().single().cleared.size)
    }

    @Test
    fun case06_twoTwoThousandAndFortyEightsInOneStepBurstBothRowsAndAwardBoth() {
        val board = boardOf(
            """
            . . 1024 . .
            . . 1024 . .
            1024 . S . .
            1024 . S . .
            """
        )
        val resolution = Resolver.resolve(
            board,
            listOf(Seed(Cell(0, 6), null), Seed(Cell(2, 4), null)),
            EngineConfig.Default,
        )
        val bursts = resolution.steps.filterIsInstance<ResolutionStep.Burst>()

        assertEquals(2, bursts.size, "two rows, two bonuses")
        assertEquals(listOf(5, 7), bursts.map { it.row }.sorted())
    }

    @Test
    fun case06_twoTwoThousandAndFortyEightsInTheSameRowBurstItOnce() {
        val board = boardOf(
            """
            1024 . 1024 . .
            1024 . 1024 . .
            """
        )
        val resolution = Resolver.resolve(
            board,
            listOf(Seed(Cell(0, 6), null), Seed(Cell(2, 6), null)),
            EngineConfig.Default,
        )
        val bursts = resolution.steps.filterIsInstance<ResolutionStep.Burst>()

        assertEquals(1, bursts.size)
        assertEquals(2, bursts.single().cleared.size)
    }

    @Test
    fun case07_undoAfterABurstRestoresTheWholeSnapshot() {
        val before = stateOf(boardOf("1024 . . . ."), score = 1234L)
        val ring = UndoRing().recording(before)
        val after = drop(before, value(1024), col = 0)

        assertTrue(after.transcript.bursts.isNotEmpty())
        assertTrue(after.state.score > before.score)

        val restored = ring.undo(after.state)
        assertEquals(UndoResult.Restored(before, UndoRing()), restored)
    }

    @Test
    fun case08_undoOnTheRunEndingDropIsRefused() {
        val ring = UndoRing().recording(stateOf(Board.empty(5, 8)))
        val over = stateOf(Board.empty(5, 8)).copy(
            status = RunStatus.STACKED_OUT,
            deathCause = DeathCause.ROW_ZERO_OCCUPIED,
        )

        assertEquals(UndoResult.Refused(UndoRefusal.RUN_OVER), ring.undo(over))
    }

    @Test
    fun case09_aCascadeInterruptedByBackgroundingSurvivesSerialization() {
        val start = Cascade.newGame(seed = 7L)
        val mid = Cascade.apply(start, Input.HardDrop)

        val round = json.decodeFromString<Transition>(json.encodeToString(mid))
        assertEquals(mid, round)

        val fromLive = Cascade.apply(mid.state, Input.HardDrop)
        val fromDisk = Cascade.apply(round.state, Input.HardDrop)
        assertEquals(json.encodeToString(fromLive), json.encodeToString(fromDisk))
    }

    @Test
    fun case10_continueClearsTheTopThreeRowsDropsTheLevelAndKeepsTheScore() {
        val board = boardOf(
            """
            2 2 2 2 2
            4 4 4 4 4
            8 8 8 8 8
            16 16 16 16 16
            """,
            align = Align.TOP,
        )
        val stacked = stateOf(board, level = 6, score = 9_000L).copy(
            status = RunStatus.STACKED_OUT,
            deathCause = DeathCause.ROW_ZERO_OCCUPIED,
        )

        val continued = Cascade.continueRun(stacked)

        assertEquals(RunStatus.PLAYING, continued.state.status)
        assertEquals(5, continued.state.level)
        assertEquals(9_000L, continued.state.score)
        assertTrue((0..2).none { continued.state.board.isRowOccupied(it) })
        assertEquals(tier(16), continued.state.board[Cell(0, 3)]?.numberValue)
        assertNotNull(continued.state.falling)
    }

    @Test
    fun case11_holdingOnTheFirstDropIsLegalAndPullsTheNextBlock() {
        val start = Cascade.newGame(seed = 99L)
        val firstBlock = start.falling?.block
        val nextUp = start.preview.first()

        val held = Cascade.apply(start, Input.Hold)

        assertFalse(held.isRejected)
        assertEquals(firstBlock, held.state.hold)
        assertEquals(nextUp, held.state.falling?.block)
        assertEquals(start.config.previewSize, held.state.preview.size)
        assertTrue(Cascade.apply(held.state, Input.Hold).isRejected, "one swap per drop")
    }

    @Test
    fun case12_aHardDropIntoAFullColumnLocksInRowZeroAndEndsTheRun() {
        val board = boardOf(
            """
            . . . . .
            2 4 2 4 2
            4 2 4 2 4
            2 4 2 4 2
            4 2 4 2 4
            2 4 2 4 2
            4 2 4 2 4
            2 4 2 4 2
            """
        )
        val transition = drop(stateOf(board), value(8), col = 2)

        assertEquals(tier(8), transition.state.board[Cell(2, 0)]?.numberValue)
        assertEquals(RunStatus.STACKED_OUT, transition.state.status)
        assertEquals(DeathCause.ROW_ZERO_OCCUPIED, transition.state.deathCause)
        assertTrue(transition.events.contains(GameEvent.StackedOut(DeathCause.ROW_ZERO_OCCUPIED)))
    }

    @Test
    fun case13_completelyEmptyingTheBoardAwardsABonusAndTheRunContinues() {
        val transition = drop(stateOf(boardOf("1024 . . . .")), value(1024), col = 0)

        assertTrue(transition.state.board.isClear)
        assertTrue(transition.events.contains(GameEvent.BoardCleared))
        assertEquals(RunStatus.PLAYING, transition.state.status)
        assertNotNull(transition.state.falling)
    }

    @Test
    fun case14_theCascadeCapIsNeverHitInPlayAndIsReportedWhenItIs() {
        val faults = playUntilOver(seed = 314_159L).flatMap { it.faults }
        assertTrue(faults.isEmpty(), "a real run should never fault: $faults")

        val board = boardOf(
            """
            2 . . . .
            2 . . . .
            4 . . . .
            """
        )
        val resolution = Resolver.resolve(
            board,
            listOf(Seed(Cell(0, 5), null)),
            EngineConfig.Default.copy(cascadeStepCap = 1),
        )
        assertEquals(Fault.CASCADE_STEP_CAP_EXCEEDED, resolution.fault)
    }

    private fun playUntilOver(seed: Long, maxInputs: Int = 4_000): List<Transition> {
        var state = Cascade.newGame(seed)
        var chooser = Rng(seed xor 0x5DEECE66DL)
        val transitions = mutableListOf<Transition>()
        repeat(maxInputs) {
            chooser = chooser.next()
            val input = when (chooser.valueIn(6)) {
                0 -> Input.MoveLeft
                1 -> Input.MoveRight
                2 -> Input.Hold
                3 -> Input.Tick
                else -> Input.HardDrop
            }
            val transition = Cascade.apply(state, input)
            transitions += transition
            state = transition.state
            if (state.isOver) return transitions
        }
        return transitions
    }
}
