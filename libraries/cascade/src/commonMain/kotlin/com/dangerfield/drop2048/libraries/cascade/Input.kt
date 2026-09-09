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

    /** Place in the lowest empty cell of the column and lock immediately (SPEC 6). */
    @Serializable
    data object HardDrop : Input

    /** Lock where the block rests. The lock delay that precedes it lives in the ViewModel. */
    @Serializable
    data object Lock : Input

    /** Stash the falling block, or swap with what is stashed. One per drop (SPEC 5.4). */
    @Serializable
    data object Hold : Input
}
