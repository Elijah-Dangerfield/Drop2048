package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four landing previews, which are three rulings and one design.
 *
 * The goldens show what each looks like; this pins what each *means*, because the
 * failure mode for the three undesigned ones is not that they look wrong, it is
 * that they promise something the engine will not do. A bright ghost is the game
 * telling the player "this is the move", and the only thing worse than not
 * drawing it is drawing it on a landing that merges nothing.
 */
class LandingPreviewTest {

    private val cols = EngineConfig.DEFAULT_COLS
    private val rows = EngineConfig.DEFAULT_ROWS
    private val bottomMiddle = Cell(2, rows - 1)
    private val aboveBottomMiddle = Cell(2, rows - 2)

    @Test
    fun aValueBlockOverItsTwin_isBrightAndSaysWhatItBecomes() {
        val board = boardOf("", cols, rows).with(bottomMiddle, NumberBlock(BlockValue.V8))
        val preview = landingPreview(board, NumberBlock(BlockValue.V8), aboveBottomMiddle)

        assertEquals(listOf(GhostCell(aboveBottomMiddle, bright = true, label = "×2")), preview)
    }

    @Test
    fun aValueBlockOverSomethingElse_isPlain() {
        val board = boardOf("", cols, rows).with(bottomMiddle, NumberBlock(BlockValue.V16))
        val preview = landingPreview(board, NumberBlock(BlockValue.V8), aboveBottomMiddle)

        assertEquals(listOf(GhostCell(aboveBottomMiddle, bright = false, label = null)), preview)
    }

    /** Decision D6's symmetry: the obvious move works, so the ghost has to say so. */
    @Test
    fun aValueBlockOverARestingWildcard_isBright() {
        val board = boardOf("", cols, rows).with(bottomMiddle, SpecialBlock(Special.WILDCARD))
        val preview = landingPreview(board, NumberBlock(BlockValue.V8), aboveBottomMiddle)

        assertTrue(preview.single().bright)
    }

    /** A 2048 cannot double, so two of them touching is not a merge and is not drawn as one. */
    @Test
    fun aTopTierBlock_isNeverBright() {
        val board = boardOf("", cols, rows).with(bottomMiddle, NumberBlock(BlockValue.V2048))
        val preview = landingPreview(board, NumberBlock(BlockValue.V2048), aboveBottomMiddle)

        assertTrue(!preview.single().bright)
    }

    @Test
    fun aStone_isAlwaysPlainEvenBesideItsOwnKind() {
        val board = boardOf("", cols, rows)
            .with(bottomMiddle, SpecialBlock(Special.STONE))
            .with(Cell(1, rows - 2), NumberBlock(BlockValue.V8))
        val preview = landingPreview(board, SpecialBlock(Special.STONE), aboveBottomMiddle)

        assertEquals(listOf(GhostCell(aboveBottomMiddle, bright = false)), preview)
    }

    /** The cell that *changes* is the neighbour's, so the neighbour is what is marked. */
    @Test
    fun aWildcard_marksTheNeighbourItWillDouble() {
        val board = boardOf("", cols, rows).with(bottomMiddle, NumberBlock(BlockValue.V8))
        val preview = landingPreview(board, SpecialBlock(Special.WILDCARD), aboveBottomMiddle)

        assertEquals(GhostCell(aboveBottomMiddle, bright = false), preview.first())
        assertEquals(GhostCell(bottomMiddle, bright = true, label = "×2"), preview.last())
    }

    @Test
    fun aWildcardWithNoValueNeighbour_isPlain() {
        val board = boardOf("", cols, rows).with(bottomMiddle, SpecialBlock(Special.STONE))
        val preview = landingPreview(board, SpecialBlock(Special.WILDCARD), aboveBottomMiddle)

        assertEquals(listOf(GhostCell(aboveBottomMiddle, bright = false)), preview)
    }

    /**
     * Only the neighbours that are actually there. An empty cell does not visibly
     * clear and scores nothing (SPEC 18.4), so outlining it would promise a bang
     * that never happens.
     */
    @Test
    fun aBomb_outlinesItselfAndOnlyItsOccupiedNeighbours() {
        val board = boardOf("", cols, rows)
            .with(bottomMiddle, NumberBlock(BlockValue.V8))
            .with(Cell(1, rows - 2), NumberBlock(BlockValue.V4))
        val preview = landingPreview(board, SpecialBlock(Special.BOMB), aboveBottomMiddle)

        assertEquals(
            listOf(aboveBottomMiddle, Cell(1, rows - 2), bottomMiddle).toSet(),
            preview.map { it.cell }.toSet(),
        )
        assertTrue(preview.all { it.bright }, "the footprint is the message")
        assertTrue(preview.all { it.label == null }, "a bomb multiplies nothing")
    }
}
