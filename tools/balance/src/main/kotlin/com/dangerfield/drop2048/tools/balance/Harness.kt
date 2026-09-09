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
 * Clock-free, every policy hard-drops into the column it wants at every level,
 * which is the right instrument for the spawn table and the wrong one for
 * difficulty: it reports what an unhurried player would manage. Hand it a
 * [PlayerProfile] and [DropClock] puts SPEC 5.5's timer and SPEC 6's controls
 * between the policy and the board, and the same policy has to earn its column.
 *
 * Both are kept because the gap between them is the measurement.
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
        var staleCapDrops = 0

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
            if (exceedsLandingTimeCap(state, config)) staleCapDrops++
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
            staleCapDrops = staleCapDrops,
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
