package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.autoplay.Autoplay
import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
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
    val clock: ClockStats? = null,
)

/**
 * What the clock saw, for a run played with [DropClock] in the loop.
 *
 * The per-level arrays cover only the first [Harness.EARLY_DROPS] drops, because
 * the question they exist to answer is about the opening of a run and averaging
 * them over a whole run would bury it: a level-1 drop and a level-25 drop are
 * different games and the mean of the two describes neither.
 */
class ClockStats(
    val elapsedMillis: Long,
    val timerPlaced: Int,
    val offTarget: Int,
    val compromised: Int,
    val slackMillis: Long,
    val earlyDrops: IntArray,
    val earlyTimerPlaced: IntArray,
    val earlyOffTarget: IntArray,
    val earlySlackMillis: LongArray,
    val earlyElapsedMillis: LongArray,
    val reachedLevelAtMillis: LongArray,
)

/**
 * Plays whole runs of the shipped engine with no renderer, and with or without
 * a clock.
 *
 * Clock-free, every policy places a block in the column it wants at every level,
 * which is the right instrument for the spawn table and the wrong one for
 * difficulty: it reports what an unhurried player would manage. Hand it a
 * [PlayerProfile] and [DropClock] puts SPEC 5.5's timer and SPEC 6's controls
 * between the policy and the board, and the same policy has to earn its column.
 *
 * Both are kept because the gap between them is the measurement.
 *
 * **Decision D21 put hard drop back and the clocked numbers return to the column
 * C1e already published** — level 4 in ~33s for a player who uses ▼. No balance
 * pass was re-run for it, and that is not a shortcut: L40 measured that a drop
 * control moves the wall clock and leaves every outcome column identical to the
 * digit, because `Input.Lock` places at the landing cell whichever control asked
 * for it. The finish model changed; what it produces did not.
 *
 * The stale-cap counter is gone with the same ruling: the board-aware cap now
 * reads the board at spawn, so the quantity L20 measured at 0.02-0.04% is
 * identically zero and a metric that can only report zero is worse than no
 * metric.
 */
object Harness {

    const val MAX_DROPS = 3_000
    const val CLUTTER_SAMPLE_EVERY = 10

    /** SPEC 5.5 puts four levels inside these, which is the stretch C3 played and called a cutscene. */
    const val EARLY_DROPS = 60

    const val LEVEL_BUCKETS = 32

    private const val DEPTH_BUCKETS = 16

    /**
     * Plays a whole run, with [profile] deciding whether the clock is in the loop.
     *
     * A null [profile] is C1a's harness unchanged, kept because the comparison
     * between the two is the finding: everything C1a reported is an upper bound
     * for a player who is never hurried, and the only way to say how big that
     * bound is is to run both.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun play(seed: Long, policy: Policy, config: EngineConfig, profile: PlayerProfile? = null): RunOutcome {
        val random = Random(seed)
        var state = Cascade.newGame(seed, config)
        var highest: BlockValue? = null
        var bursts = 0
        var death = Death.DROP_CAP
        val depths = IntArray(DEPTH_BUCKETS)
        val clutter = IntArray(config.cols * config.rows + 1)

        var elapsed = 0L
        var timerPlaced = 0
        var offTarget = 0
        var compromised = 0
        var slack = 0L
        val earlyDrops = IntArray(LEVEL_BUCKETS)
        val earlyTimer = IntArray(LEVEL_BUCKETS)
        val earlyOff = IntArray(LEVEL_BUCKETS)
        val earlySlack = LongArray(LEVEL_BUCKETS)
        val earlyElapsed = LongArray(LEVEL_BUCKETS)
        val reachedAt = LongArray(LEVEL_BUCKETS) { -1L }
        var buffered = false

        while (state.blocksDropped < MAX_DROPS) {
            val level = state.level
            val bucket = minOf(level, LEVEL_BUCKETS - 1)
            if (profile != null && reachedAt[bucket] < 0) reachedAt[bucket] = elapsed
            val early = state.blocksDropped < EARLY_DROPS

            val transition = if (profile == null) {
                drop(state, policy.column(state, random, config))
            } else {
                val played = DropClock.play(state, policy, random, buffered, profile)
                val resolution = DropClock.resolutionMillis(played.transition.transcript)
                val cost = played.elapsedMillis + resolution
                elapsed += cost
                slack += played.slackMillis
                buffered = resolution >= profile.decisionMillis
                if (played.placement == Placement.TIMER) timerPlaced++
                if (!played.onTarget) offTarget++
                if (played.compromised) compromised++
                if (early) {
                    earlyDrops[bucket]++
                    earlySlack[bucket] += played.slackMillis
                    earlyElapsed[bucket] += cost
                    if (played.placement == Placement.TIMER) earlyTimer[bucket]++
                    if (!played.onTarget) earlyOff[bucket]++
                }
                played.transition
            }
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
            clock = profile?.let {
                ClockStats(
                    elapsedMillis = elapsed,
                    timerPlaced = timerPlaced,
                    offTarget = offTarget,
                    compromised = compromised,
                    slackMillis = slack,
                    earlyDrops = earlyDrops,
                    earlyTimerPlaced = earlyTimer,
                    earlyOffTarget = earlyOff,
                    earlySlackMillis = earlySlack,
                    earlyElapsedMillis = earlyElapsed,
                    reachedLevelAtMillis = reachedAt,
                )
            },
        )
    }

    /**
     * Steers the falling block to [col] and locks it where it would land.
     *
     * The horizontal moves are real [Input.MoveLeft] / [Input.MoveRight]s rather
     * than a teleport, because moving is what sets `lastDirection`, and that is
     * slot 2 of SPEC 4.3's merge priority order. A harness that placed blocks
     * directly would be playing a slightly different game from the one that
     * ships.
     *
     * This used to end in `Input.HardDrop`, which decision D11 removed.
     * [Input.Lock] locks at the landing cell, so the *placement* is identical and
     * every clock-free number C1a measured still means what it meant. What is
     * gone is the score bonus, and — in the clocked harness — any pretence that a
     * player can put a block down in one press. See [DropClock].
     */
    fun drop(state: GameState, col: Int): Transition = Autoplay.drop(state, col)

    private fun higher(a: BlockValue?, b: BlockValue?): BlockValue? = when {
        a == null -> b
        b == null -> a
        else -> if (b.ordinal > a.ordinal) b else a
    }
}
