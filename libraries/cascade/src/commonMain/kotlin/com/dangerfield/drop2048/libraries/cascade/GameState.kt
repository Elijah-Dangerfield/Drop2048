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
 *
 * ### There is no next-block queue
 *
 * There used to be one — a `preview` list plus a `hold` slot — and decision D11
 * cut both. Nothing replaced the queue, because nothing needed to: a draw is a
 * pure function of [rng], [level], [board] and [drawsMade], all of which are
 * here, so drawing at the moment a block spawns is exactly as reproducible as
 * drawing two drops early and holding the result. Keeping an invisible queue
 * would only have preserved *when* the draw happened, and that timing is the one
 * thing D11 wanted to change: the board-aware cap in [Spawn] now reads the board
 * the block will actually land on rather than the board of two drops ago.
 */
@Serializable
data class GameState(
    val config: EngineConfig = EngineConfig.Default,
    val board: Board,
    val falling: FallingBlock?,
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

    /** The cell the falling block will come to rest in, for the ghost outline. */
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

    /**
     * Whether this state sits on the every-[DROPS_PER_SAMPLE]th drop boundary
     * SPEC 4.4 and SPEC 17 both sample on.
     *
     * Here rather than at either call site because the whole value of the
     * clutter metric is that `tools/balance` and the live client report the
     * same number on the same cadence. Two `% 10` expressions in two modules
     * are two things that can drift apart silently, and a drifted cadence is
     * invisible on a dashboard — the numbers still arrive, they just stop
     * meaning the same thing.
     */
    val isSampleDrop: Boolean get() = blocksDropped > 0 && blocksDropped % DROPS_PER_SAMPLE == 0
}

/**
 * The sampling period SPEC 4.4's harness and SPEC 17's per-drop telemetry both
 * use. One drop in ten: often enough to see a spawn floor go wrong within a
 * level, rare enough that a live run emits tens of records rather than
 * hundreds.
 */
const val DROPS_PER_SAMPLE = 10
