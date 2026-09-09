package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import kotlin.math.roundToInt

/**
 * The motion vocabulary. Call sites pick a named intent, not a damping ratio, so
 * two things that bounce in this game bounce the same way.
 *
 * Two rules the tokens encode. Everything springs rather than eases, because a
 * linear tween on a game about falling objects reads as cheap. And everything a
 * player waits on lands inside ~300ms, because a run is hundreds of drops and
 * any one of them being delightful at 600ms is the thing they uninstall over.
 *
 * The cascade is the deliberate exception, and it is argued rather than assumed:
 * see [CascadeStepMillis].
 */
object Motion {

    /** A block seating itself in a cell. Overshoots, then settles. */
    val Lock: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Press feedback and small state flips. Quick, barely overshoots. */
    val Tap: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** A block travelling between cells under gravity. No overshoot; gravity does not bounce. */
    val Fall: FiniteAnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Colour and alpha transitions, where a spring would look like a glitch. */
    fun <T> fade(): AnimationSpec<T> = tween(durationMillis = FadeMillis)

    /** A move into a wall, or a drop refused. Over before the player can dwell on it. */
    const val ShakeMillis: Int = 240

    /** Cell fills, ghosts appearing, anything cross-fading. */
    const val FadeMillis: Int = 160

    /** A hard drop's travel, however far it falls. Fixed, so the thud always lands on time. */
    const val HardDropMillis: Int = 90

    /**
     * One step of a cascade: the merge, its pop, and the gravity that follows.
     *
     * The one number in here allowed to be watched rather than waited through.
     * Everything else on this list is *feedback* — the player is waiting on it to
     * carry on, so it has to be over before they are. A cascade is the reward,
     * and SPEC 21 says the ascending run of merges is half of what makes this
     * game worth playing. Rushing it is the cheapest possible way to throw that
     * away.
     *
     * Still short. A six-step cascade at this rate is under a second of held
     * breath, and a player who has to watch a chain finish before the board makes
     * sense again is being told something they already know, slowly.
     */
    const val CascadeStepMillis: Int = 150

    /** The row burst. The longest thing in the game, and the only one that earns it. */
    const val BurstMillis: Int = 420

    /** Danger state fading in and out. Slow on purpose; a fast desaturate reads as a glitch. */
    const val DangerMillis: Int = 320

    /** How far a pressed element scales down. Matches `bounceClick`'s default. */
    const val PressScale: Float = 0.90f

    /** How far a locking block overshoots before settling. */
    const val LockOvershoot: Float = 1.12f
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
fun reducible(millis: Int): Int =
    if (LocalReduceMotion.current) (millis * ReducedMotionScale).roundToInt() else millis

/**
 * Not zero. A cascade that resolves instantly is unreadable — a player who asked
 * for less motion still needs to see which merges happened and in what order.
 */
private const val ReducedMotionScale = 0.4f
