package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Cell
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Everything the debug menu can change about a run that is not already a
 * property of the engine.
 *
 * ### Nothing here is an `EngineConfig`
 *
 * That is the single most important thing about this type, and it is what keeps
 * a forced board out of a Daily Challenge. D18 pins the Daily to
 * `EngineConfig.Default` so that everyone who plays a day plays the same game,
 * and D5 puts the config *inside* `GameState` so a replayed seed reproduces the
 * run it was recorded under. A debug override that took the shape of an
 * `EngineConfig` would travel inside a saved run, inside a `run_record` replay
 * and — if anyone ever wired it into `dailyRun` — inside a shared seed.
 *
 * So there is no config override, and there is no guard against one either.
 * These are edits to a *starting state* and to the ViewModel's clock, both of
 * which stop at the boundary of the run they were applied to. The strongest
 * version of "a debug config cannot leak into a Daily" is that no debug config
 * exists.
 *
 * [tickIntervalMs] is the clearest case: SPEC 5.5's speed curve is a config key,
 * and this is deliberately *not* it. It is a number the ViewModel's ticker reads
 * instead of the curve, so the state it produces is identical to one produced at
 * the real speed. Vertical position is not an input to the engine (L40), so
 * changing how fast a block falls cannot change where it lands.
 */
@Serializable
data class DebugOverrides(
    /** The seed the next run starts from. Null draws a fresh one, as normal. */
    val seed: Long? = null,

    /** The level the next run starts at, and therefore its spawn band. */
    val startLevel: Int? = null,

    val preset: PresetBoard? = null,

    /** Hand-placed edits applied on top of [preset], newest last. */
    val placements: List<Placement> = emptyList(),

    /**
     * Blocks handed to the player in order, one per spawn, before the RNG gets a
     * say again.
     *
     * A queue rather than a single forced value because the interesting bugs are
     * sequences — a Wildcard onto a 1024, then a Bomb into the hole it left.
     */
    val forcedBlocks: List<Block> = emptyList(),

    /** Milliseconds per row, replacing SPEC 5.5's curve for this run only. */
    val tickIntervalMs: Int? = null,

    /** Gravity stops. The block sits where it is until it is moved or locked. */
    val freezeTimer: Boolean = false,

    /** A stacked-out run keeps playing. SPEC 19's invincibility. */
    val invincible: Boolean = false,
) {
    /** Whether any of this would change a run that started right now. */
    val changesTheNextRun: Boolean
        get() = seed != null ||
            startLevel != null ||
            preset != null ||
            placements.isNotEmpty() ||
            forcedBlocks.isNotEmpty()

    companion object {
        /** No overrides at all: the run a player would have got. */
        val None = DebugOverrides()
    }
}

/** One hand-placed or hand-deleted cell. A null [block] is a delete. */
@Serializable
data class Placement(val cell: Cell, val block: Block?)

/**
 * The debug menu's state, and the one flag that keeps QA out of the numbers.
 *
 * ### Why the session is the unit, and not the run
 *
 * Anything a debug menu writes to `run_record`, `daily_result` or
 * `Leaderboards.submit` is a lie about a player. The tempting design is to taint
 * individual runs — mark the run that was forced, leave the rest alone — and it
 * is wrong in the only direction that matters. A tester who loads a preset,
 * plays it, exits, then plays a "normal" run has still had their hands on a menu
 * that can set a seed and grant Pro; treating that second run as real data means
 * the person most likely to be producing junk is the one the guard trusts. And
 * per-run tainting has to be *remembered* at four call sites, which is four
 * places to forget it.
 *
 * So [isDebugSession] latches the first time the menu is opened, and stays
 * latched until the process dies. From that moment the run that ends writes no
 * `run_record`, banks no `daily_result` and posts no score, and every analytics
 * event carries `debug_session: true` (SPEC 17, consumed by C8). It cannot be
 * switched back off from inside the app: an escape hatch is the same hole with a
 * confirmation dialog in front of it. Relaunching is the reset, which is both
 * unambiguous and free.
 *
 * The cost is that a tester who opens the menu to look at the seed loses that
 * run's record. That is the right trade — the data is worth more than the run,
 * and the alternative failure is silent.
 */
interface DebugController {

    val overrides: StateFlow<DebugOverrides>

    /**
     * True from the first time the debug menu was opened this launch. Read by
     * the game before it records anything and by telemetry on every event.
     */
    val isDebugSession: StateFlow<Boolean>

    /** Latches [isDebugSession]. Called when the menu is opened, not when it acts. */
    fun markDebugSession()

    suspend fun update(transform: (DebugOverrides) -> DebugOverrides)

    /**
     * The next block the player should be handed, consuming it from the queue.
     *
     * Null once the queue is empty, which hands the draw back to the RNG. It is
     * a consuming read rather than a flow because a spawn happens once and a
     * queue that could be re-read would repeat a block on a recomposition.
     */
    fun takeForcedBlock(): Block?
}
