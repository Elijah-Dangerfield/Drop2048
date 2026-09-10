package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.blockOf
import kotlinx.serialization.Serializable

/**
 * SPEC 19's six starting boards, built to whatever shape the run's
 * [com.dangerfield.drop2048.libraries.cascade.EngineConfig] says rather than to a
 * hardcoded 5x8.
 *
 * `board.rows` is a remote key (SPEC 10), so a preset that assumed eight rows
 * would silently become a different scenario the first time it moved — and the
 * whole value of a named preset is that "One Away From 2048" means the same thing
 * to two people on a call. Every preset here is expressed relative to
 * [Board.cols] and [Board.rows] and is therefore correct at any size the config
 * can express.
 *
 * These are *starting states*, not commands. The engine is a pure function of
 * state (SPEC 4.1), so "load a preset" is "start a run from this board" — there
 * is nothing to mutate and nothing that can half-apply.
 */
@Serializable
enum class PresetBoard {
    /** The board a run already starts on. Here so "clear the board" has a name. */
    Empty,

    /** Every row but the top two, filled with mismatched tiers. One drop from the edge. */
    NearlyFull,

    /** A 1024 with a 1024's partner one column over: the next merge bursts a row. */
    OneAwayFrom2048,

    /**
     * A stack rigged so a single 2 landing in the middle column merges up through
     * four tiers (SPEC 4.3's worked example, extended).
     */
    CascadeChain,

    /** Stones down two columns, which is what level 19+ actually feels like. */
    StoneHeavy,

    /** Row 1 occupied, so the danger state is on the moment the run starts. */
    Danger,
    ;

    fun boardFor(cols: Int, rows: Int): Board {
        val empty = Board.empty(cols, rows)
        return when (this) {
            Empty -> empty
            NearlyFull -> empty.withAll(nearlyFull(cols, rows))
            OneAwayFrom2048 -> empty.withAll(oneAway(cols, rows))
            CascadeChain -> empty.withAll(cascadeChain(cols, rows))
            StoneHeavy -> empty.withAll(stoneHeavy(cols, rows))
            Danger -> empty.withAll(danger(cols, rows))
        }
    }

    /**
     * Alternating tiers so nothing merges on its own, filling everything below
     * row 2. Two free rows rather than one, because a board with only row 0 free
     * ends the run on the first lock and never shows the state it was loaded to
     * show.
     */
    private fun nearlyFull(cols: Int, rows: Int): Map<Cell, Block?> = buildMap {
        val tiers = listOf(BlockValue.V2, BlockValue.V8, BlockValue.V32, BlockValue.V128)
        for (row in FIRST_FREE_ROWS until rows) {
            for (col in 0 until cols) {
                put(Cell(col, row), blockOf(tiers[(row * cols + col) % tiers.size]))
            }
        }
    }

    /**
     * Two 1024s side by side on the floor with nothing under the gap between
     * them, so a Wildcard or a hand-placed 1024 finishes the row.
     */
    private fun oneAway(cols: Int, rows: Int): Map<Cell, Block?> = buildMap {
        val floor = rows - 1
        put(Cell(0, floor), blockOf(BlockValue.V1024))
        put(Cell(1, floor), blockOf(BlockValue.V512))
        put(Cell(2, floor), blockOf(BlockValue.V512))
        for (col in 3 until cols) put(Cell(col, floor), blockOf(BlockValue.V64))
    }

    /**
     * SPEC 4.3's chain, made as long as the board allows.
     *
     * A column of doubling tiers under the spawn column: a 2 landing on the 2
     * makes a 4 in the partner's cell, which is above the 4, which makes an 8,
     * and so on down. The number of cascade steps is the number of rungs, which
     * is what "force an N-step cascade" needs to be adjustable by.
     */
    private fun cascadeChain(cols: Int, rows: Int): Map<Cell, Block?> = buildMap {
        val col = cols / 2
        var tier = BlockValue.V2
        for (row in rows - 1 downTo 1) {
            put(Cell(col, row), blockOf(tier))
            tier = tier.doubled ?: break
        }
    }

    /** Stones down the two outer columns from halfway, plus a floor of them. */
    private fun stoneHeavy(cols: Int, rows: Int): Map<Cell, Block?> = buildMap {
        for (row in rows / 2 until rows) {
            put(Cell(0, row), blockOf(Special.STONE))
            put(Cell(cols - 1, row), blockOf(Special.STONE))
        }
        for (col in 1 until cols - 1) put(Cell(col, rows - 1), blockOf(BlockValue.V16))
    }

    /**
     * Row 1 is the danger row the engine watches, so two columns stacked to it is
     * enough — a full board would be [NearlyFull] wearing a different name.
     *
     * Stacked to the floor rather than floated at row 1, because the engine only
     * settles under gravity during a resolution: a preset with a hole under it
     * would sit there looking like a rendering bug until the next merge.
     */
    private fun danger(cols: Int, rows: Int): Map<Cell, Block?> = buildMap {
        for (row in PRESET_DANGER_ROW until rows) {
            put(Cell(0, row), blockOf(BlockValue.V2))
            put(Cell(cols - 1, row), blockOf(BlockValue.V4))
        }
    }
}

/**
 * Top-level rather than in a `private companion object`, and not by preference.
 *
 * Read from inside the `buildMap` lambdas above, a private companion constant on
 * this enum makes Kotlin 2.x's JVM backend fail an assertion in
 * `SyntheticAccessorLowering` while lowering the field read. The Gradle build
 * survives it — the Kotlin daemon dies and the compile is retried out of process
 * — so it shows up as an alarming stack trace over a green build rather than as
 * a failure, which is exactly how it would have been ignored. Bisected to this
 * declaration.
 */
private const val FIRST_FREE_ROWS = 2
private const val PRESET_DANGER_ROW = 1
