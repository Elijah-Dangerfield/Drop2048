package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.features.debug.describe
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.MergeKind
import com.dangerfield.drop2048.libraries.cascade.ResolutionStep
import com.dangerfield.drop2048.libraries.cascade.Transcript
import com.dangerfield.drop2048.libraries.cascade.blockOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The resolution log, which is the half of the debug menu that earns it.
 *
 * Unit-tested rather than left to the golden, because a screenshot can only say
 * the panel looked the same and the question this answers is whether it said the
 * right thing. It is a pure function over the engine's own transcript, so it
 * costs nothing to test properly.
 */
class TranscriptLogTest {

    @Test
    fun `a merge names both cells and the direction it went`() {
        val lines = Transcript(
            listOf(
                ResolutionStep.Merge(
                    step = 1,
                    initiator = Cell(3, 6),
                    partner = Cell(2, 6),
                    into = Cell(2, 6),
                    result = BlockValue.V8,
                    kind = MergeKind.VALUE,
                    points = 8,
                ),
            )
        ).describe()

        assertEquals(1, lines.single().step)
        assertEquals("merge (3,6) < (2,6) = 8", lines.single().text)
        assertEquals(8, lines.single().points)
    }

    @Test
    fun `a wildcard merge says so`() {
        val lines = Transcript(
            listOf(
                ResolutionStep.Merge(
                    step = 2,
                    initiator = Cell(1, 5),
                    partner = Cell(1, 6),
                    into = Cell(1, 6),
                    result = BlockValue.V64,
                    kind = MergeKind.WILDCARD,
                    points = 64,
                ),
            )
        ).describe()

        assertTrue(lines.single().text.startsWith("wildcard"))
        assertTrue(lines.single().text.contains(" v "))
    }

    /**
     * SPEC 4.3's priority order has no `UP`, so a merge that claims to have gone
     * upward is a resolver bug. The log says `?` rather than picking a plausible
     * arrow: a debugging tool that makes a wrong answer look right is worse than
     * one that says nothing.
     */
    @Test
    fun `a merge that could not have happened is not given a plausible arrow`() {
        val lines = Transcript(
            listOf(
                ResolutionStep.Merge(
                    step = 1,
                    initiator = Cell(2, 6),
                    partner = Cell(2, 5),
                    into = Cell(2, 5),
                    result = BlockValue.V8,
                    kind = MergeKind.VALUE,
                    points = 8,
                ),
            )
        ).describe()

        assertTrue(lines.single().text.contains(" ? "), lines.single().text)
    }

    @Test
    fun `a burst counts its stones`() {
        val lines = Transcript(
            listOf(
                ResolutionStep.Burst(
                    step = 3,
                    row = 7,
                    cleared = (0 until 5).map { Cell(it, 7) },
                    points = 6250,
                    stones = 1,
                ),
            )
        ).describe()

        assertEquals("burst row 7, 5 cells, 1 stone", lines.single().text)
    }

    /**
     * The whole point is that this describes what the engine actually did, so the
     * strongest test hands it a real resolution rather than a hand-built one.
     *
     * A 4 dropped onto a 4 with an 8 under it is SPEC 4.3's worked example: two
     * merges, and the second one is the chain the game is pitched on.
     */
    @Test
    fun `it describes a real cascade the engine produced`() {
        val config = EngineConfig.Default
        val board = Board.empty(config.cols, config.rows)
            .with(Cell(1, config.rows - 1), blockOf(BlockValue.V8))
            .with(Cell(1, config.rows - 2), blockOf(BlockValue.V4))
        val start = Cascade.newGame(seed = 1, config = config).let { fresh ->
            fresh.copy(
                board = board,
                falling = fresh.falling?.copy(
                    block = blockOf(BlockValue.V4),
                    cell = Cell(1, 0),
                ),
            )
        }

        val transcript = Cascade.apply(start, Input.Lock).transcript
        val lines = transcript.describe()

        assertEquals(transcript.steps.size, lines.size)
        assertTrue(lines.any { it.text.startsWith("merge") }, lines.joinToString("\n") { it.text })
        assertEquals(transcript.points, lines.sumOf { it.points })
    }
}
