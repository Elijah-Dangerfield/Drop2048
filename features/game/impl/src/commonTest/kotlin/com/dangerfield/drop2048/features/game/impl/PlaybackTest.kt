package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.BlockMove
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.MergeKind
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.ResolutionStep
import com.dangerfield.drop2048.libraries.cascade.Rng
import com.dangerfield.drop2048.libraries.cascade.Transcript
import com.dangerfield.drop2048.libraries.ui.system.Cue
import com.dangerfield.drop2048.libraries.ui.system.Motion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The re-derivation of the boards between transcript steps.
 *
 * The property that matters is the last one: **the final frame equals the board
 * the engine returned.** Everything else here is a specific way the replay could
 * be wrong, and that assertion is the one that catches the ways nobody thought
 * of — if the frames and the engine ever disagree, the player watches a cascade
 * that ends somewhere the game does not.
 */
class PlaybackTest {

    private val cols = EngineConfig.DEFAULT_COLS
    private val rows = EngineConfig.DEFAULT_ROWS

    @Test
    fun merge_clearsBothCellsAndPlacesTheResultInThePartnersCell() {
        val before = boardOf(
            """
            .  .  2  2  .
            """,
            cols, rows,
        )
        val transcript = Transcript(
            listOf(
                ResolutionStep.Merge(
                    step = 1,
                    initiator = Cell(3, 7),
                    partner = Cell(2, 7),
                    into = Cell(2, 7),
                    result = BlockValue.V4,
                    kind = MergeKind.VALUE,
                    points = 4,
                ),
            )
        )

        val frames = framesFor(before, scoreBefore = 0, transcript = transcript)

        assertEquals(1, frames.size)
        assertEquals(NumberBlock(BlockValue.V4), frames.single().board[Cell(2, 7)])
        assertNull(frames.single().board[Cell(3, 7)])
        assertEquals(4L, frames.single().score)
    }

    /**
     * A column falling two rows at once. Applied one move at a time, the block
     * that lands first would overwrite the cell the block above it is still
     * being read from.
     */
    @Test
    fun gravity_appliesEveryMoveAtOnce() {
        val before = boardOf(
            """
            .  .  2  .  .
            .  .  4  .  .
            .  .  .  .  .
            .  .  .  .  .
            """,
            cols, rows,
        )
        val transcript = Transcript(
            listOf(
                ResolutionStep.Gravity(
                    step = 1,
                    moves = listOf(
                        BlockMove(from = Cell(2, 5), to = Cell(2, 7)),
                        BlockMove(from = Cell(2, 4), to = Cell(2, 6)),
                    ),
                ),
            )
        )

        val board = framesFor(before, scoreBefore = 0, transcript).single().board

        assertEquals(NumberBlock(BlockValue.V4), board[Cell(2, 7)])
        assertEquals(NumberBlock(BlockValue.V2), board[Cell(2, 6)])
        assertNull(board[Cell(2, 4)])
        assertNull(board[Cell(2, 5)])
    }

    @Test
    fun cues_pitchClimbWithTheCascadeStep() {
        val transcript = Transcript(
            (1..4).map { step ->
                ResolutionStep.Merge(
                    step = step,
                    initiator = Cell(0, 7),
                    partner = Cell(1, 7),
                    into = Cell(1, 7),
                    result = BlockValue.V4,
                    kind = MergeKind.VALUE,
                    points = 4 * step,
                )
            }
        )

        val pitches = framesFor(boardOf("", cols, rows), 0, transcript).map { it.cue?.pitchSteps }

        assertEquals(listOf(0, 1, 2, 3), pitches)
    }

    @Test
    fun cues_bigMergeGetsTheHeavierPairing() {
        val transcript = Transcript(
            listOf(
                ResolutionStep.Merge(
                    step = 1,
                    initiator = Cell(0, 7),
                    partner = Cell(1, 7),
                    into = Cell(1, 7),
                    result = BlockValue.V256,
                    kind = MergeKind.VALUE,
                    points = 256,
                ),
            )
        )

        assertEquals(
            Cue.Merge(step = 1, big = true),
            framesFor(boardOf("", cols, rows), 0, transcript).single().cue,
        )
    }

    @Test
    fun chain_isTaggedFromStepTwoUpward() {
        val transcript = Transcript(
            (1..3).map { step ->
                ResolutionStep.Merge(
                    step = step,
                    initiator = Cell(0, 7),
                    partner = Cell(1, 7),
                    into = Cell(1, 7),
                    result = BlockValue.V4,
                    kind = MergeKind.VALUE,
                    points = 4,
                )
            }
        )

        assertEquals(
            listOf(false, true, true),
            framesFor(boardOf("", cols, rows), 0, transcript).map { it.chained },
        )
    }

    /**
     * The pacing decision C3a made by watching a cascade on a device, pinned so
     * that flattening it back to one number has to be a decision.
     *
     * Every step used to be held for the same 195ms. On a real three-step chain
     * that put `CHAIN x2` on screen and replaced it with `CHAIN x3` inside 200ms,
     * so the number the player was being congratulated on was never legible, and
     * the run read as one flash rather than as three things in order.
     */
    @Test
    fun holds_giveAChainedMergeLongerThanAPlainOne() {
        val holds = framesFor(
            boardOf("", cols, rows),
            scoreBefore = 0,
            transcript = Transcript(
                (1..3).map { step -> merge(step = step, result = BlockValue.V8) }
            ),
        ).map { it.holdMillis }

        assertEquals(Motion.MergeHoldMillis, holds.first(), "a step-one merge is not a chain")
        assertEquals(
            listOf(Motion.CascadeStepMillis, Motion.CascadeStepMillis),
            holds.drop(1),
            "and every chained step gets long enough for its callout to be read",
        )
    }

    /**
     * 2048 is terminal (SPEC 5.1): it bursts its own row, so it is the one tile
     * in the game that can never be looked at. On device it was on screen for
     * four tenths of a second between the merge that made it and the burst that
     * took it away — the tile the game is named after, and SPEC 9's "do not
     * undersell it" applied to the frame before the burst rather than the burst.
     */
    @Test
    fun holds_giveTheTerminalTierAndItsBurstTheirOwnBeats() {
        val holds = framesFor(
            boardOf("", cols, rows),
            scoreBefore = 0,
            transcript = Transcript(
                listOf(
                    merge(step = 1, result = BlockValue.V2048),
                    ResolutionStep.Burst(step = 1, row = 7, cleared = listOf(Cell(1, 7)), points = 5_250),
                )
            ),
        ).map { it.holdMillis }

        assertEquals(listOf(Motion.TerminalMergeMillis, Motion.RowBurstMillis), holds)
        assertTrue(
            Motion.TerminalMergeMillis > Motion.CascadeStepMillis &&
                Motion.RowBurstMillis > Motion.CascadeStepMillis,
            "the burst and the tile that causes it outrank every other step",
        )
    }

    private fun merge(step: Int, result: BlockValue) = ResolutionStep.Merge(
        step = step,
        initiator = Cell(0, 7),
        partner = Cell(1, 7),
        into = Cell(1, 7),
        result = result,
        kind = MergeKind.VALUE,
        points = result.points,
    )

    /**
     * The invariant the whole design rests on. `Cascade` is asked to resolve a
     * real drop, and the replay has to land on exactly the board it returned.
     */
    @Test
    fun lastFrame_isTheBoardTheEngineReturned() {
        val before = boardOf(
            """
            .  4  4  4  .
            .  8  S  S  .
            """,
            cols, rows,
        )
        val state = GameState(
            config = EngineConfig.Default,
            board = before,
            falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(2, 0)),
            rng = Rng(1),
            drawsMade = EngineConfig.DEFAULT_SUPPRESSED_DRAWS,
        )

        val transition = Cascade.apply(state, Input.Lock)
        val placed = before.with(Cell(2, 5), NumberBlock(BlockValue.V4))
        val frames = framesFor(placed, state.score, transition.transcript)

        assertTrue(frames.isNotEmpty(), "a lock always produces at least a survival frame")
        assertEquals(transition.state.board, frames.last().board)
        assertEquals(transition.state.score, frames.last().score)
    }
}
