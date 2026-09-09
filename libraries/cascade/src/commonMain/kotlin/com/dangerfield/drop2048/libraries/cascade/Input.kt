package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

/**
 * The complete input alphabet.
 *
 * Serializable because a seed plus a list of these is a whole run: it is what a
 * bug report attaches, what the debug menu replays, and what the balance
 * harness generates.
 *
 * There is no soft-drop input. Soft drop is the same [Tick] arriving on a
 * shorter interval, and the interval is the ViewModel's business — the engine
 * has no idea time exists. There is no undo input either; undo restores a whole
 * snapshot from [UndoRing] rather than running the machine backwards.
 *
 * `HardDrop` and `Hold` were removed by decision D11. The design handoff governs
 * interaction and says both were tried and cut, along with the next-block
 * preview that hold was built around. [Nudge] is what replaced hard drop, and it
 * is a weaker thing on purpose.
 */
@Serializable
sealed interface Input {

    /** One row of gravity for the falling block. A no-op when it has already landed. */
    @Serializable
    data object Tick : Input

    @Serializable
    data object MoveLeft : Input

    @Serializable
    data object MoveRight : Input

    /**
     * The ▼ control, and a downward flick: advance the fall by
     * [EngineConfig.nudgeRows] ticks and nothing else (SPEC 6, decision D11).
     *
     * It is an accelerator, not an instant drop. It does not lock, it does not
     * score, and it stops early against whatever is underneath — a nudge into a
     * blocked cell is a no-op rather than a rejection, exactly as [Tick] is,
     * because the player pressing ▼ on a resting block has not done anything
     * wrong.
     */
    @Serializable
    data object Nudge : Input

    /**
     * Lock where the block rests. The lock delay that precedes it lives in the
     * ViewModel.
     *
     * "Where it rests" is the landing cell of the block's current column, not the
     * cell it currently occupies, so a `Lock` from mid-air places the block at the
     * bottom of its column. That is what hard drop used to be minus the bonus, and
     * it is why removing hard drop cost the engine no code path.
     */
    @Serializable
    data object Lock : Input
}
