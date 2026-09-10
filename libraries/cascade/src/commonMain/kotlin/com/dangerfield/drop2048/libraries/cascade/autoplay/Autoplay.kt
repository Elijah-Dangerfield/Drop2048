package com.dangerfield.drop2048.libraries.cascade.autoplay

import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.Transition
import kotlin.math.abs
import kotlin.random.Random

/**
 * Plays the engine with nobody watching.
 *
 * It lives here rather than in `tools/balance` because it has two callers now:
 * the balance harness that measured SPEC 5.3's spawn table, and the debug menu's
 * autoplay soak (SPEC 19). A second set of policies would have made the soak
 * measure a player the harness has never described, so the soak reuses these.
 *
 * Still pure, still no clock, still no coroutines. `tools/balance`'s clocked half
 * stays in `tools/balance`, because a `PlayerProfile` and a drop timer are an
 * instrument rather than part of the engine.
 */
object Autoplay {

    /**
     * Move the falling block to [col] and lock it.
     *
     * A lock places at the landing cell of the block's current column, so the
     * moves have to happen first and the fall does not.
     */
    fun drop(state: GameState, col: Int): Transition {
        val from = state.falling?.cell?.col ?: return Cascade.apply(state, Input.Lock)
        val step = if (col > from) Input.MoveRight else Input.MoveLeft
        var current = state
        repeat(abs(col - from)) { current = Cascade.apply(current, step).state }
        return Cascade.apply(current, Input.Lock)
    }

    /**
     * Plays [from] to its end, or to [maxDrops], and reports what happened.
     *
     * [maxDrops] is a guard rather than a rule: block count strictly decreases
     * through a cascade and the board is finite, so a run always ends. Hitting
     * the cap means the engine is wrong, and the soak says so rather than
     * reporting a run that never died as a run that survived.
     */
    fun soak(
        from: GameState,
        policy: Policy,
        random: Random,
        maxDrops: Int = DEFAULT_MAX_DROPS,
    ): SoakResult {
        var state = from
        var drops = 0
        var merges = 0
        var bursts = 0
        var deepest = 0
        val faults = mutableListOf<String>()
        while (!state.isOver && drops < maxDrops) {
            val col = policy.column(state, random, state.config)
            val transition = drop(state, col)
            transition.faults.forEach { faults += it.name }
            merges += transition.transcript.merges.size
            bursts += transition.transcript.bursts.size
            if (transition.transcript.depth > deepest) deepest = transition.transcript.depth
            state = transition.state
            drops++
        }
        return SoakResult(
            policy = policy.name,
            state = state,
            drops = drops,
            merges = merges,
            bursts = bursts,
            deepestCascade = deepest,
            faults = faults,
            hitDropCap = drops >= maxDrops && !state.isOver,
        )
    }

    const val DEFAULT_MAX_DROPS = 5_000
}

/**
 * One soaked run. [faults] is the reason the soak exists at all — an engine that
 * reports a fault under a policy nobody plays like is still a bug, and the debug
 * menu is where somebody would go looking for one.
 */
class SoakResult(
    val policy: String,
    val state: GameState,
    val drops: Int,
    val merges: Int,
    val bursts: Int,
    val deepestCascade: Int,
    val faults: List<String>,
    val hitDropCap: Boolean,
) {
    val level: Int get() = state.level
    val score: Long get() = state.score
    val config: EngineConfig get() = state.config
}
