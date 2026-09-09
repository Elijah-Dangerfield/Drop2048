package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Direction
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.ResolutionStep
import com.dangerfield.drop2048.libraries.cascade.Transcript
import com.dangerfield.drop2048.libraries.cascade.Transition
import kotlin.math.abs
import kotlin.random.Random

/** Who actually put the block down. */
enum class Placement {
    /** A drop input put it down: a tap for a hard drop, or a hold for a soft drop. */
    PLAYER,

    /**
     * The lock delay expired and the block settled on its own.
     *
     * This is a count of who pressed the button, not of who won a race. A drop
     * can be timer-placed with the player sitting on their hands three seconds
     * early, which is C3's complaint, and it can be timer-placed because the
     * player was still steering when it locked, which is pressure. `off
     * preferred column` is the one that separates them.
     */
    TIMER,
}

/** How a clocked policy ends a drop once it has reached the column it wants. */
enum class Finish {
    /** A tap on the shared drop control (SPEC 6). */
    HARD_DROP,

    /** A hold on the same control: 40ms a row, flat at every level. */
    SOFT_DROP,

    /** Neither. Steer, then watch it fall, which is how the game plays itself if you let it. */
    WAIT,
}

/**
 * The player between the drop control and the board.
 *
 * A clock in the loop is only worth having if something on the other side of it
 * costs time, and on a phone that cost is the hand: a beat to read the new block
 * and the board, then a tap rate the thumb can actually sustain. Both numbers
 * are assumptions rather than measurements — nobody has instrumented a real
 * player yet — so three profiles bracket them and every reported number says
 * which one it came from.
 *
 * [Average] is the one the headline numbers use. 250ms to decide is roughly
 * choice-reaction time plus a glance at the preview, and 120ms between taps is
 * a comfortable repeat rate rather than a mashing one.
 */
data class PlayerProfile(
    val name: String,
    val decisionMillis: Int,
    val tapMillis: Int,
    val finish: Finish,
) {
    companion object {
        val Average = PlayerProfile("average", decisionMillis = 250, tapMillis = 120, finish = Finish.HARD_DROP)
        val Quick = PlayerProfile("quick", decisionMillis = 150, tapMillis = 70, finish = Finish.HARD_DROP)
        val Deliberate = PlayerProfile("deliberate", decisionMillis = 400, tapMillis = 180, finish = Finish.HARD_DROP)

        /**
         * SPEC 6's other half of the shared drop control: hold instead of tap and
         * the block falls at 40ms a row rather than instantly. Kept as a profile
         * because a harness in which soft drop is never pressed cannot say
         * whether soft drop matters.
         */
        val Softie = PlayerProfile("softie", decisionMillis = 250, tapMillis = 120, finish = Finish.SOFT_DROP)

        /**
         * The other end of the pacing bracket: never touches the drop control.
         *
         * Not a good player and not meant to be one. It exists because the wall
         * clock of a run is set almost entirely by how often the drop control is
         * used, and a single number for "how long a level takes" is meaningless
         * without both ends of that.
         */
        val Patient = PlayerProfile("patient", decisionMillis = 250, tapMillis = 120, finish = Finish.WAIT)

        val All = listOf(Average, Quick, Deliberate, Softie, Patient)

        fun byName(name: String): PlayerProfile? = All.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** One drop played against the clock. [transition] is what the engine did; the rest is what the clock saw. */
class ClockedDrop(
    val transition: Transition,
    val elapsedMillis: Long,
    val slackMillis: Long,
    val placement: Placement,
    val onTarget: Boolean,
    val compromised: Boolean,
)

/**
 * SPEC 5.5's drop timer and SPEC 6's controls, simulated around the engine.
 *
 * The engine has no clock (SPEC 4.1) and the `GameViewModel` is the thing that
 * gives it one, so this is a headless copy of that ViewModel's timing rules and
 * nothing else: ticks at `msPerRow(level)`, a 150ms lock delay with **one**
 * reset per drop, soft drop flat at 40ms a row, and the one-slot sideways buffer
 * C3 added. Every one of those is a rule a scripted player can be beaten by, and
 * a harness that ignored them would be measuring a game nobody plays.
 *
 * Events at the same instant resolve player input first, then the drop timer,
 * then the lock. On a device those arrive on one channel in whatever order the
 * dispatcher picked; a tie is genuinely undefined there, so it is defined here
 * in the player's favour and reported as an assumption rather than a fact.
 */
object DropClock {

    const val LOCK_DELAY_MILLIS = 150

    private const val NEVER = Long.MAX_VALUE

    /**
     * How long the transcript takes to play out on screen.
     *
     * Mirrors `Playback.holdMillis` in `:features:game:impl`, which the harness
     * cannot depend on because that module is Compose. The numbers matter for
     * two things: the wall clock a run takes, and whether the resolution lasted
     * long enough for the player to have queued the buffered move.
     */
    fun resolutionMillis(transcript: Transcript): Long = transcript.steps.sumOf { step ->
        when (step) {
            is ResolutionStep.Burst -> BURST_MILLIS
            is ResolutionStep.HardDropBonus,
            is ResolutionStep.Survival,
            is ResolutionStep.LevelUp,
            -> SCORE_ONLY_MILLIS

            else -> CASCADE_STEP_MILLIS
        }.toLong()
    }

    /**
     * Plays one drop with the timer running.
     *
     * [buffered] is C3's sideways buffer: the previous resolution was long enough
     * for the player to have tapped a direction while watching it, and that tap
     * is replayed the instant this block exists. It is modelled as one free step
     * toward the column this drop wants, which is the optimistic end of what the
     * buffer can do — the player is choosing it against a board that is still
     * finishing its cascade.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun play(
        state: GameState,
        policy: Policy,
        random: Random,
        buffered: Boolean,
        profile: PlayerProfile,
    ): ClockedDrop {
        val config = state.config
        val falling = state.falling
            ?: return ClockedDrop(Cascade.apply(state, Input.HardDrop), 0, 0, Placement.PLAYER, true, false)

        val fallMillis = config.speed.msPerRow(state.level)
        val preferred = policy.column(state, random, config)
        val withinReach = { col: Int -> reachable(state, col, fallMillis, buffered, profile) }
        val compromised = !withinReach(preferred)
        var target = if (compromised) policy.column(state, random, config, withinReach) else preferred

        var current = state
        if (buffered && target != falling.cell.col) {
            current = Cascade.apply(current, stepToward(falling.cell.col, target)).state
        }

        var now = 0L
        var interval = fallMillis.toLong()
        var nextInput = profile.decisionMillis.toLong()
        var nextTick = interval
        var lockAt = NEVER
        var lockResetUsed = false
        var slack = -1L
        var placement = Placement.TIMER
        var finished: Transition? = null

        while (finished == null) {
            val block = current.falling ?: break
            now = minOf(nextInput, nextTick, lockAt)
            when {
                now == nextInput -> {
                    if (block.cell.col == target) {
                        slack = restingMillis(current, fallMillis)
                        when (profile.finish) {
                            Finish.HARD_DROP -> {
                                placement = Placement.PLAYER
                                finished = Cascade.apply(current, Input.HardDrop)
                            }

                            Finish.SOFT_DROP -> {
                                placement = Placement.PLAYER
                                interval = config.speed.softDropMsPerRow.toLong()
                                nextTick = minOf(nextTick, now + interval)
                                nextInput = NEVER
                            }

                            Finish.WAIT -> nextInput = NEVER
                        }
                    } else {
                        val moved = Cascade.apply(current, stepToward(block.cell.col, target))
                        if (moved.isRejected) {
                            target = block.cell.col
                        } else {
                            current = moved.state
                            if (lockAt != NEVER && !lockResetUsed) {
                                lockResetUsed = true
                                lockAt = now + LOCK_DELAY_MILLIS
                            }
                        }
                        nextInput = now + profile.tapMillis
                    }
                }

                now == nextTick -> {
                    if (current.board.isEmpty(block.cell + Direction.DOWN)) {
                        current = Cascade.apply(current, Input.Tick).state
                        lockAt = NEVER
                    } else if (lockAt == NEVER) {
                        lockAt = now + LOCK_DELAY_MILLIS
                    }
                    nextTick = now + interval
                }

                else -> finished = Cascade.apply(current, Input.Lock)
            }
        }

        val landedIn = current.falling?.cell?.col ?: target
        return ClockedDrop(
            transition = finished ?: Cascade.apply(current, Input.HardDrop),
            elapsedMillis = now,
            slackMillis = if (slack < 0) 0 else slack,
            placement = placement,
            onTarget = landedIn == preferred,
            compromised = compromised,
        )
    }

    /**
     * Whether the player could still be in [col] by the time the block locks.
     *
     * The deadline is set by the **shallowest** landing row along the way, not by
     * the destination's: a block crossing a tall column locks on top of it. The
     * lock reset is deliberately left out, so this is a conservative plan and the
     * simulation above is free to do better than it. A policy that cannot make
     * its first choice re-picks among what is left, which is the whole point of
     * putting a clock in the loop — the ceiling becomes an estimate.
     */
    private fun reachable(
        state: GameState,
        col: Int,
        fallMillis: Int,
        buffered: Boolean,
        profile: PlayerProfile,
    ): Boolean {
        val falling = state.falling ?: return true
        val from = falling.cell.col
        val distance = abs(col - from)
        if (distance == 0) return true

        val row = falling.cell.row
        val shallowest = (minOf(from, col)..maxOf(from, col)).minOf { state.board.landingRow(it, row) }
        val deadline = (shallowest - row).toLong() * fallMillis + LOCK_DELAY_MILLIS
        val taps = if (buffered) distance - 1 else distance
        val needed = if (taps <= 0) 0L else profile.decisionMillis + (taps - 1).toLong() * profile.tapMillis
        return needed <= deadline
    }

    /**
     * How long the timer would still take to place the block from where it is.
     *
     * Measured at the moment the player has finished steering, so it is the time
     * they spend watching rather than playing. This is the number C3's "the first
     * two minutes are a cutscene" is really about.
     */
    private fun restingMillis(state: GameState, fallMillis: Int): Long {
        val falling = state.falling ?: return 0
        val rows = state.board.landingRow(falling.cell.col, falling.cell.row) - falling.cell.row
        return rows.toLong() * fallMillis + LOCK_DELAY_MILLIS
    }

    private fun stepToward(from: Int, to: Int): Input = if (to > from) Input.MoveRight else Input.MoveLeft

    private const val CASCADE_STEP_MILLIS = 150
    private const val BURST_MILLIS = 420
    private const val SCORE_ONLY_MILLIS = 60
}
