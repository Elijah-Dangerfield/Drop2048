package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

/**
 * The complete input alphabet.
 *
 * Serializable because a seed plus a list of these is a whole run: it is what a
 * bug report attaches, what the debug menu replays, and what the balance
 * harness generates.
 *
 * There is no soft-drop input, and since decision D21 there is no soft drop at
 * all. There is no undo input either; undo restores a whole snapshot from
 * [UndoRing] rather than running the machine backwards.
 *
 * `Hold` was removed by decision D11 along with the next-block preview it was
 * built around. `Nudge`, D11's two-tick accelerator, was removed by D21: ▼ is a
 * hard drop again, and a hard drop is [Lock].
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
     * Lock where the block rests, and **this is also the hard drop** (SPEC 6,
     * decision D21). The lock delay that precedes an unhurried lock lives in the
     * ViewModel.
     *
     * "Where it rests" is the landing cell of the block's current column, not the
     * cell it currently occupies, so a `Lock` from mid-air places the block at the
     * bottom of its column. That has always been true, which is why hard drop
     * could be deleted in D11 without touching a code path and reinstated in D21
     * without adding one: the two differ only in how many rows were skipped, and
     * the engine reads that off the state it already has.
     */
    @Serializable
    data object Lock : Input
}
