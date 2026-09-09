package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC 7's table, row by row, plus the invariant that makes the table
 * trustworthy: **the transcript is the only channel points travel down**, so
 * `next.score == previous.score + transcript.points` after every transition.
 * If a future change awards points somewhere else, that assertion fails rather
 * than the score quietly disagreeing with the numbers floating over the board.
 */
class ScoringTest {

    private val flatBottom = boardOf("S S S S S")

    @Test
    fun aMergeIsWorthItsResultTimesTheCascadeStep() {
        val board = boardOf(
            """
            2 . . . .
            2 . . . .
            4 . . . .
            8 . . . .
            """
        )
        val merges = resolve(board, Cell(0, 4)).steps.filterIsInstance<ResolutionStep.Merge>()

        assertEquals(listOf(4 * 1, 8 * 2, 16 * 3), merges.map { it.points })
    }

    @Test
    fun theCascadeMultiplierIsCapped() {
        val board = boardOf(
            """
            2 . . . .
            2 . . . .
            4 . . . .
            8 . . . .
            """
        )
        val capped = EngineConfig.Default.copy(cascadeMultiplierCap = 2)
        val merges = Resolver.resolve(board, listOf(Seed(Cell(0, 4), null)), capped)
            .steps
            .filterIsInstance<ResolutionStep.Merge>()

        assertEquals(listOf(4 * 1, 8 * 2, 16 * 2), merges.map { it.points })
    }

    /**
     * Decision D11's scoring ruling, asserted rather than described. Hard drop's
     * `2 x rowsSkipped` is gone and the ▼ nudge did not inherit it, so no input
     * puts a point on the board any more. See `Scoring`'s KDoc for the argument.
     */
    @Test
    fun noInputScoresAnything() {
        var state = stateOf(flatBottom, FallingBlock(value(2), Cell(0, 0)))

        listOf(Input.MoveRight, Input.MoveLeft, Input.Tick, Input.Nudge, Input.Nudge).forEach { input ->
            val transition = Cascade.apply(state, input)
            assertTrue(
                transition.transcript.isEmpty,
                "$input produced a transcript step: ${transition.transcript.steps}",
            )
            assertEquals(0L, transition.state.score, "$input scored")
            state = transition.state
        }
    }

    /**
     * The only steps a lock can add outside a cascade. Pinned as a set so a
     * future award that pays for an input has to change this test on the way in.
     */
    @Test
    fun aDropOnAnEmptyBoardScoresSurvivalAndNothingElse() {
        val transition = drop(stateOf(flatBottom, level = 3), value(2), col = 0)

        assertEquals(
            listOf(ResolutionStep.Survival(level = 3, points = 30)),
            transition.transcript.steps,
        )
    }

    @Test
    fun aRowBurstIsFiveThousandPlusTwoFiftyPerBlockCleared() {
        val board = boardOf(
            """
            1024 . . . .
            1024 S S S S
            """
        )
        val burst = resolve(board, Cell(0, 6)).steps.filterIsInstance<ResolutionStep.Burst>().single()

        assertEquals(5000 + 250 * 5, burst.points)
    }

    @Test
    fun aBombIsWorthFiftyPerBlockDestroyed() {
        val board = boardOf(
            """
            8 B . . .
            8 8 . . .
            """
        )
        val fired = resolve(board, Cell(1, 6))
            .steps
            .filterIsInstance<ResolutionStep.Detonation>()
            .single()

        assertEquals(2, fired.destroyed.size)
        assertEquals(100, fired.points)
    }

    @Test
    fun levellingUpIsWorthOneHundredTimesTheNewLevel() {
        val state = stateOf(flatBottom, blocksDropped = 19, level = 1)
        val transition = drop(state, value(2), col = 0)

        val levelUp = transition.transcript.steps.filterIsInstance<ResolutionStep.LevelUp>().single()
        assertEquals(2, levelUp.level)
        assertEquals(200, levelUp.points)
        assertEquals(2, transition.state.level)
        assertTrue(transition.events.contains(GameEvent.LevelReached(2)))
    }

    @Test
    fun survivingADropIsWorthTenTimesTheLevel() {
        val state = stateOf(flatBottom, level = 7)
        val transition = drop(state, value(2), col = 0)

        val survival = transition.transcript.steps.filterIsInstance<ResolutionStep.Survival>().single()
        assertEquals(70, survival.points)
    }

    @Test
    fun clearingTheBoardIsWorthOneThousand() {
        val cleared = drop(stateOf(boardOf("1024 . . . .")), value(1024), col = 0)

        assertTrue(cleared.state.board.isClear, "\n${cleared.state.board}")
        assertEquals(
            1000,
            cleared.transcript.steps.filterIsInstance<ResolutionStep.BoardCleared>().single().points,
        )
        assertTrue(cleared.events.contains(GameEvent.BoardCleared))
    }

    @Test
    fun theScoreOnlyEverMovesByTheTranscriptTotal() {
        var state = Cascade.newGame(seed = 4_815_162_342L)
        val script = listOf(Input.MoveLeft, Input.Nudge, Input.Lock, Input.MoveRight, Input.Nudge, Input.Lock)

        repeat(60) { round ->
            val input = script[round % script.size]
            val before = state.score
            val transition = Cascade.apply(state, input)
            assertEquals(
                before + transition.transcript.points,
                transition.state.score,
                "score drifted from the transcript on round $round",
            )
            state = transition.state
            if (state.isOver) return
        }
    }
}
