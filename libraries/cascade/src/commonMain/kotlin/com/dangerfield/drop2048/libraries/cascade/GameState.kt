package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

@Serializable
enum class RunStatus { PLAYING, STACKED_OUT }

/** Why a run ended, recorded on `run_record` (SPEC 11). */
@Serializable
enum class DeathCause {
    /** Row 0 was still occupied once a resolution completed fully (SPEC 3.1). */
    ROW_ZERO_OCCUPIED,

    /** The spawn cell was occupied. SPEC 18.1 says this is impossible; if it happens it is a bug. */
    SPAWN_BLOCKED,
}

/**
 * The block in flight, its column, and the last direction the player pushed it
 * this drop.
 *
 * [lastDirection] is priority slot 2 in SPEC 4.3's merge order and is null
 * until the player moves the block, which is why the slot is "skipped if the
 * player never moved it" rather than defaulting to something.
 */
@Serializable
data class FallingBlock(
    val block: Block,
    val cell: Cell,
    val lastDirection: Direction? = null,
)

/**
 * The entire run, as one immutable value (SPEC 4.1).
 *
 * A `GameState` plus a sequence of [Input]s determines the run byte for byte on
 * every platform. Nothing here is derived from a clock, a coroutine or an
 * injected source of randomness — [rng] is carried in the state itself.
 *
 * The undo ring is deliberately *not* a field. It holds whole `GameState`s, and
 * nesting them would make serialising one run quadratic and would put eight
 * copies of the board into the `AppData` blob SPEC 11 overwrites on every drop.
 * It lives in [UndoRing] alongside the state instead.
 */
@Serializable
data class GameState(
    val config: EngineConfig = EngineConfig.Default,
    val board: Board,
    val falling: FallingBlock?,
    val preview: List<Block>,
    val hold: Block? = null,
    val holdUsedThisDrop: Boolean = false,
    val score: Long = 0,
    val level: Int = 1,
    val blocksDropped: Int = 0,
    val drawsMade: Int = 0,
    val rng: Rng,
    val lastDrawWasSpecial: Boolean = false,
    val status: RunStatus = RunStatus.PLAYING,
    val deathCause: DeathCause? = null,
    val inDanger: Boolean = false,
) {
    val isOver: Boolean get() = status != RunStatus.PLAYING

    /** The cell a hard drop would place the falling block in, for the ghost outline. */
    val landingCell: Cell?
        get() = falling?.let { Cell(it.cell.col, board.landingRow(it.cell.col, it.cell.row)) }

    val highestTier: BlockValue? get() = board.highestValue()

    /**
     * Blocks with no matching partner anywhere on the board. SPEC 4.4 and SPEC 17
     * both call this the early warning that the spawn floor is mistuned, and both
     * want the same number so offline and live figures are comparable.
     */
    val clutter: Int
        get() {
            val values = board.cells.mapNotNull { it?.numberValue }
            val counts = values.groupingBy { it }.eachCount()
            return values.count { counts.getValue(it) == 1 }
        }
}
