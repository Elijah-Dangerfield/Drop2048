package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.GameState

/**
 * Turns a fresh run into the run the debug menu asked for.
 *
 * Pure, and that is what makes the whole menu cheap: SPEC 4.1 says a `GameState`
 * plus a list of inputs is the entire run, so preparing a scenario is building
 * one value. There is no command to send, no half-applied state to recover from,
 * and the engine cannot tell a forced board from a played one.
 *
 * [DebugOverrides.tickIntervalMs], [DebugOverrides.freezeTimer] and
 * [DebugOverrides.invincible] are absent here on purpose — none of them is a
 * property of the state. They belong to the clock and the loop that owns it, and
 * keeping them out of this function is what stops a debug run's *state* from
 * being distinguishable from a real one.
 */
fun DebugOverrides.applyTo(state: GameState, forcedBlock: Block? = null): GameState {
    val config = state.config
    var board = preset?.boardFor(config.cols, config.rows) ?: state.board
    if (placements.isNotEmpty()) {
        board = board.withAll(placements.filter { board.contains(it.cell) }.associate { it.cell to it.block })
    }

    val spawn = Cell(config.centreColumn, 0)
    val falling = when {
        board[spawn] != null -> null
        forcedBlock != null -> FallingBlock(forcedBlock, spawn)
        else -> state.falling
    }

    return state.copy(
        board = board,
        falling = falling,
        // The level is stored rather than derived (see `Cascade.continueRun`), so
        // `blocksDropped` has to be moved with it or the level bar starts full
        // and the next level-up arrives at the wrong drop.
        level = startLevel ?: state.level,
        blocksDropped = startLevel?.let { (it - 1) * config.blocksPerLevel } ?: state.blocksDropped,
        inDanger = board.isRowOccupied(DANGER_ROW),
    )
}

private const val DANGER_ROW = 1
