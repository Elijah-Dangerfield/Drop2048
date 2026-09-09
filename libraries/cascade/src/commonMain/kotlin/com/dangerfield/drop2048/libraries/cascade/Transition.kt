package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

/** Why the engine refused an input. Never a thrown exception — inputs are player data. */
@Serializable
enum class RejectionReason {
    RUN_OVER,
    NO_FALLING_BLOCK,
    MOVE_BLOCKED,
}

/**
 * A bug the engine noticed in itself.
 *
 * SPEC 18.1 and 18.14 both say these are reported, not swallowed. The engine has
 * no logger, so it hands the fault back to the caller as a value and the
 * ViewModel decides what to do with it.
 */
@Serializable
enum class Fault {
    /** SPEC 18.14: the 100-step cascade cap was hit. Block count strictly decreases, so it cannot be. */
    CASCADE_STEP_CAP_EXCEEDED,

    /** SPEC 18.1: a block spawned into an occupied cell. The stacked-out check should have fired first. */
    SPAWN_INTO_OCCUPIED_CELL,
}

/**
 * Everything a caller needs to know about a transition beyond the board itself.
 * Anything the transcript already carries (points, merges, bursts) is not
 * duplicated here.
 */
@Serializable
sealed interface GameEvent {
    @Serializable
    data class LevelReached(val level: Int) : GameEvent

    @Serializable
    data object DangerEntered : GameEvent

    @Serializable
    data object DangerCleared : GameEvent

    @Serializable
    data object BoardCleared : GameEvent

    @Serializable
    data class StackedOut(val cause: DeathCause) : GameEvent

    @Serializable
    data class Rejected(val reason: RejectionReason) : GameEvent

    @Serializable
    data class EngineFault(val fault: Fault) : GameEvent
}

/**
 * `GameState x Input -> Transition` (SPEC 4.1).
 *
 * Serializable in full so a bug report can carry the transition that produced
 * it, and so a cascade interrupted by backgrounding (SPEC 18.9) can be
 * snapshotted and replayed on resume without re-running the engine.
 */
@Serializable
data class Transition(
    val state: GameState,
    val transcript: Transcript = Transcript.Empty,
    val events: List<GameEvent> = emptyList(),
) {
    val faults: List<Fault> get() = events.filterIsInstance<GameEvent.EngineFault>().map { it.fault }

    val isRejected: Boolean get() = events.any { it is GameEvent.Rejected }
}
