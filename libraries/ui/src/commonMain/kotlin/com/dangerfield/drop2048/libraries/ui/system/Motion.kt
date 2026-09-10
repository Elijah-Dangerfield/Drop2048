package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import kotlin.math.roundToInt

/**
 * The motion vocabulary. Call sites pick a named intent, not a damping ratio, so
 * two things that move in this game move the same way.
 *
 * **The design handoff supplies the timing table, and it overrides C2's.** C2
 * wrote these as springs on the argument that a linear tween on a game about
 * falling objects reads as cheap. The handoff disagrees in the one place that
 * matters and it is right: a tile's *descent* is linear and it has to be, because
 * it steps one row per tick and any easing on that step makes a constant-rate
 * fall read as a stutter. Everything the design does want to feel springy — the
 * pop, the press — it gets from an overshooting curve rather than from a
 * physical spring, so the overshoot is the same on every device.
 *
 * The springs C2 wrote are kept where the handoff has nothing to say: press
 * feedback that has no duration in the table, and the cascade pacing, which is a
 * gameplay decision rather than a visual one.
 *
 * [Pop] is the one to get right. It plays on every landing, every merge result
 * and every gravity-settled tile, which makes it the single most-seen animation
 * in the game.
 */
object Motion {

    /**
     * A tile stepping down one row. Linear, and the duration is exactly one
     * step — the fall is a run of these, not one long animation.
     *
     * Linear is load-bearing. Ease anything onto it and a constant-rate descent
     * acquires a heartbeat.
     */
    const val StepDownMillis: Int = 110

    /** A tile stepping one column sideways under the player's finger. */
    const val StepSideMillis: Int = 70

    /**
     * The pop: `0.55 → 1.14 at 60% → 1.0`.
     *
     * The handoff notes that the prototype alternates between two identically
     * defined CSS keyframes purely to force the browser to re-run the animation
     * on a tile that did not change. That is a workaround for a limitation
     * Compose does not have — an `Animatable` replays because it is told to — so
     * it is not ported. Do not reintroduce a second identical token.
     */
    const val PopMillis: Int = 300

    /** The pop's curve. Overshoots hard, settles fast. */
    val PopEasing: Easing = CubicBezierEasing(0.2f, 1.5f, 0.4f, 1f)

    /** How far the pop overshoots at its peak, and where the peak sits. */
    const val PopFrom: Float = 0.55f
    const val PopPeak: Float = 1.14f
    const val PopPeakFraction: Float = 0.60f

    /** How long a merge is held on screen before gravity is applied. */
    const val MergeHoldMillis: Int = 210

    /** A column compacting downward after a merge. */
    const val GravitySettleMillis: Int = 180

    /**
     * A 2048 clearing its row. The longest thing in the game, and the only one
     * that earns it.
     *
     * **Raised from the handoff's 240 in C3a, after watching one.** At 240 the
     * row vanished between two frames and `ROW BUST!` was announced over an empty
     * board, so the loudest event in the game had no picture attached to it — the
     * whole burst, from the 2048 appearing to the board being clear, took 400ms.
     * SPEC 9 says in as many words not to undersell this. It is now the longest
     * hold in [Motion] other than [TerminalMergeMillis], which is the beat
     * immediately before it.
     */
    const val RowBurstMillis: Int = 420

    /**
     * The merge that makes a 2048, held on its own.
     *
     * 2048 is terminal: it bursts its own row, so the tile the game is named
     * after is the one tile that can never be looked at. Measured on device it
     * was on screen for four tenths of a second between the merge that made it
     * and the burst that took it away. This is the beat that lets a player see
     * the thing they spent the run building.
     *
     * It is the only place in this file where a duration depends on *what*
     * merged rather than on which kind of step it is, and that is the point: no
     * other merge in the game ends the object it produced.
     */
    const val TerminalMergeMillis: Int = 520

    /** `CHAIN xN`, `ROW BUST!`, `LEVEL N`. In and out, once. */
    const val ToastMillis: Int = 900

    /** One full breath of the landing ghost. Loops for as long as a tile is falling. */
    const val GhostPulseMillis: Int = 1000

    /** The board ring going red and coming back. Slow on purpose; a fast swap reads as a glitch. */
    const val DangerMillis: Int = 300

    /** The level progress bar growing. */
    const val ProgressMillis: Int = 300

    /** The active-column highlight following the falling tile. */
    const val ColumnHighlightMillis: Int = 150

    /** Press feedback and small state flips. Quick, barely overshoots. */
    val Tap: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Colour and alpha transitions, where a spring would look like a glitch. */
    fun <T> fade(): AnimationSpec<T> = tween(durationMillis = FadeMillis)

    /** The pop, as a spec. Every landing, merge result and settled tile plays this. */
    fun pop(): FiniteAnimationSpec<Float> = tween(durationMillis = PopMillis, easing = PopEasing)

    /** A move into a wall, or a drop refused. Over before the player can dwell on it. */
    const val ShakeMillis: Int = 240

    /** Cell fills, ghosts appearing, anything cross-fading. */
    const val FadeMillis: Int = 160

    /** A hard drop's travel, however far it falls. Fixed, so the thud always lands on time. */
    const val HardDropMillis: Int = 90

    /**
     * How long one **chained** merge is held: step two of a cascade and every
     * step after it.
     *
     * The one number in here allowed to be watched rather than waited through.
     * SPEC 21 says the ascending run of merges is half of what makes this game
     * worth playing, and rushing it is the cheapest possible way to throw that
     * away.
     *
     * **Raised from 195 in C3a, and the criterion is legibility rather than
     * taste.** SPEC 8.2 puts a `CHAIN xN` over every chained merge and
     * [ToastMillis] gives it most of a second to be read — but a later callout
     * replaces the one before it, so consecutive chain steps are the real
     * ceiling on how long a toast is on screen. At 195 a three-step cascade on
     * device put `CHAIN x2` up and took it away again inside 200ms: the number
     * the player is being congratulated on was never readable, and the whole run
     * arrived as one flash rather than as three things happening in order. 300 is
     * about the shortest a short word and a numeral can be shown and still be
     * read.
     *
     * A **step-one** merge is not this. It has no chain to announce, it happens
     * on most drops, and it gets [MergeHoldMillis] — the handoff's own number,
     * and cheap enough that a fast level does not feel like it keeps stopping.
     */
    const val CascadeStepMillis: Int = 300

    /** How far a pressed element scales down. Matches `bounceClick`'s default. */
    const val PressScale: Float = 0.90f
}

/**
 * The same duration, shortened when the player has asked for less motion.
 *
 * SPEC 16 is specific about what reduce motion means here: the transcript still
 * plays, it plays faster. Nothing about the game changes, only its rendering —
 * so this scales durations rather than skipping frames or dropping steps, and
 * the engine never sees it.
 *
 * Read from [LocalReduceMotion] inside the component that animates and nowhere
 * else. Threading it as a parameter is how Sodogku ended up with three
 * incompatible idioms for one setting in one repo.
 */
@Composable
@ReadOnlyComposable
fun reducible(millis: Int): Int = reduced(millis, LocalReduceMotion.current)

/**
 * The same scaling, for the one caller that cannot be a composable: the
 * transcript playback pace lives in the game's ViewModel, because the engine has
 * no clock and the frames are handed out over time rather than composed.
 *
 * The setting still comes from [LocalReduceMotion] — the screen reads it and
 * tells the ViewModel — so there is still exactly one place the value is
 * resolved. What this stops is a second `0.4f` written down somewhere the first
 * one cannot be found from.
 */
fun reduced(millis: Int, reduceMotion: Boolean): Int =
    if (reduceMotion) (millis * ReducedMotionScale).roundToInt() else millis

/**
 * Not zero. A cascade that resolves instantly is unreadable — a player who asked
 * for less motion still needs to see which merges happened and in what order.
 */
private const val ReducedMotionScale = 0.4f
