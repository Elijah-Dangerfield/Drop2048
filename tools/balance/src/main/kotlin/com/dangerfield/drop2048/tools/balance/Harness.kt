package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.DeathCause
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.Transition
import com.dangerfield.drop2048.libraries.cascade.numberValue
import kotlin.math.abs
import kotlin.random.Random

/** Why a harness run stopped. */
enum class Death {
    ROW_ZERO_OCCUPIED,
    SPAWN_BLOCKED,
    ENGINE_FAULT,

    /** The run hit [Harness.MAX_DROPS] without dying. Never expected; reported loudly if it happens. */
    DROP_CAP,
}

/**
 * One finished run, reduced to the numbers SPEC 4.4 asks for.
 *
 * Histograms rather than raw samples: 10,000 runs of a few hundred drops each is
 * millions of data points, and every question asked of them is a quantile or a
 * share.
 */
class RunOutcome(
    val level: Int,
    val blocksDropped: Int,
    val score: Long,
    val highestTier: BlockValue?,
    val death: Death,
    val bursts: Int,
    val depths: IntArray,
    val clutter: IntArray,
    val staleCapDrops: Int,
)

/**
 * Plays whole runs of the shipped engine with no renderer and no clock.
 *
 * Every policy hard-drops. Soft drop and lock delay are the ViewModel's, and a
 * scripted player that used them would be measuring the drop timer rather than
 * the spawn table.
 */
object Harness {

    const val MAX_DROPS = 3_000
    const val CLUTTER_SAMPLE_EVERY = 10

    private const val DEPTH_BUCKETS = 16

    fun play(seed: Long, policy: Policy, config: EngineConfig): RunOutcome {
        val random = Random(seed)
        var state = Cascade.newGame(seed, config)
        var highest: BlockValue? = null
        var bursts = 0
        var death = Death.DROP_CAP
        val depths = IntArray(DEPTH_BUCKETS)
        val clutter = IntArray(config.cols * config.rows + 1)
        var staleCapDrops = 0

        while (state.blocksDropped < MAX_DROPS) {
            if (exceedsLandingTimeCap(state, config)) staleCapDrops++
            val transition = drop(state, policy.column(state, random, config))
            val transcript = transition.transcript
            depths[minOf(transcript.depth, DEPTH_BUCKETS - 1)]++
            bursts += transcript.bursts.size
            highest = higher(highest, transcript.merges.maxOfOrNull { it.result })
            state = transition.state
            highest = higher(highest, state.highestTier)

            if (state.blocksDropped % CLUTTER_SAMPLE_EVERY == 0) clutter[state.clutter]++

            if (transition.faults.isNotEmpty()) {
                death = Death.ENGINE_FAULT
                break
            }
            if (state.isOver) {
                death = when (state.deathCause) {
                    DeathCause.SPAWN_BLOCKED -> Death.SPAWN_BLOCKED
                    else -> Death.ROW_ZERO_OCCUPIED
                }
                break
            }
        }

        return RunOutcome(
            level = state.level,
            blocksDropped = state.blocksDropped,
            score = state.score,
            highestTier = highest,
            death = death,
            bursts = bursts,
            depths = depths,
            clutter = clutter,
            staleCapDrops = staleCapDrops,
        )
    }

    /**
     * Whether the block about to land is one SPEC 5.3's board-aware cap would
     * have refused if it had been evaluated now instead of at draw time.
     *
     * The cap reads the board two drops early — SPEC 5.3 says so, and SPEC 5.4's
     * non-optional preview forces it. That means it can only ever loosen on stale
     * data, never tighten, so this counts exactly the drops where the safety
     * valve was open wider than the board justified. If chains ever get frequent
     * enough that `highestOnBoard` climbs faster than two drops, this is the
     * number that shows it compounding.
     */
    private fun exceedsLandingTimeCap(state: GameState, config: EngineConfig): Boolean {
        val value = state.falling?.block?.numberValue ?: return false
        val highest = state.board.highestValue()?.points ?: 0
        val ceiling = maxOf(config.spawnCapFloor, highest / config.spawnCapDivisor)
        return value.points > ceiling
    }

    /**
     * Steers the falling block to [col] and hard-drops it.
     *
     * The horizontal moves are real [Input.MoveLeft] / [Input.MoveRight]s rather
     * than a teleport, because moving is what sets `lastDirection`, and that is
     * slot 2 of SPEC 4.3's merge priority order. A harness that placed blocks
     * directly would be playing a slightly different game from the one that
     * ships.
     */
    fun drop(state: GameState, col: Int): Transition {
        val from = state.falling?.cell?.col ?: return Cascade.apply(state, Input.HardDrop)
        val step = if (col > from) Input.MoveRight else Input.MoveLeft
        var current = state
        repeat(abs(col - from)) { current = Cascade.apply(current, step).state }
        return Cascade.apply(current, Input.HardDrop)
    }

    private fun higher(a: BlockValue?, b: BlockValue?): BlockValue? = when {
        a == null -> b
        b == null -> a
        else -> if (b.ordinal > a.ordinal) b else a
    }
}
