package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

@Serializable
enum class UndoRefusal {
    /** Nothing has been recorded yet. */
    EMPTY,

    /** SPEC 18.8: undo on the drop that ended the run is refused. Continue exists for that. */
    RUN_OVER,
}

@Serializable
sealed interface UndoResult {
    @Serializable
    data class Restored(val state: GameState, val ring: UndoRing) : UndoResult

    @Serializable
    data class Refused(val reason: UndoRefusal) : UndoResult
}

/**
 * A bounded ring of pre-drop [GameState]s (SPEC 5.6).
 *
 * Undo is a full snapshot restore, so a burst is restored correctly by
 * construction — there are no inverse operations to get wrong (SPEC 18.7). That
 * is the whole reason the engine is a pure function of state in the first place.
 *
 * In v1 this is not a player feature. It is the debug menu's step-back and the
 * crash-recovery path. When the powerup economy lands, the Undo powerup is a
 * call to an API that already exists and is already tested.
 *
 * It is held next to a [GameState] rather than inside one: nesting eight whole
 * boards inside the value that SPEC 11 rewrites on every drop would make the
 * in-progress-run blob grow with the ring.
 */
@Serializable
data class UndoRing(
    val states: List<GameState> = emptyList(),
    val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "undo capacity must be positive, was $capacity" }
        require(states.size <= capacity) { "ring holds ${states.size} states, capacity is $capacity" }
    }

    val depth: Int get() = states.size

    /** Call this with the state as it stood *before* a drop was locked in. */
    fun recording(state: GameState): UndoRing =
        copy(states = (states + state).takeLast(capacity))

    fun undo(current: GameState): UndoResult = when {
        current.isOver -> UndoResult.Refused(UndoRefusal.RUN_OVER)
        states.isEmpty() -> UndoResult.Refused(UndoRefusal.EMPTY)
        else -> UndoResult.Restored(states.last(), copy(states = states.dropLast(1)))
    }

    companion object {
        const val DEFAULT_CAPACITY = 8
    }
}
