package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Plays a [Cue] — both halves of it, in one call.
 *
 * Routed through one object rather than call sites reaching for a vibrator and a
 * sample bank separately, for the reason SPEC 9 gives: a merge is one event, and
 * two calls that can be made independently are two calls that will eventually
 * disagree about what just happened. `cues.play(Cue.Merge(step = 4))` is the
 * whole API.
 *
 * It also means the Off / Light / Strong setting is respected everywhere without
 * every feature remembering to check it, and it is the one place the two cues
 * that collide can be arbitrated — see [Cue.chatters] and [HapticBus].
 */
@Immutable
class Cues(
    private val haptics: HapticEngine,
    private val sounds: SoundPlayer,
    private val setting: HapticsSetting,
    timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val chatter = ChatterGate(timeSource)
    private val bus = HapticBus(timeSource)

    /**
     * The audio half always plays; the haptic half has to get past [HapticBus].
     *
     * Asymmetric on purpose. Two samples overlapping mix, and SPEC 21's ascending
     * run only exists if every merge of a cascade is heard. Two vibrations
     * overlapping do not mix: there is one vibrator, and the second request
     * cancels the first.
     *
     * The bus is only consulted for a cue that will actually be felt, which is
     * the same test [HapticEngine] implementations make before they touch the
     * hardware. `HapticsSetting.Off` still reaches the engine as a strength of
     * zero rather than as a skipped call, and a silent cue never takes a turn it
     * would only waste.
     */
    fun play(cue: Cue) {
        if (cue.chatters && !chatter.admits()) return
        sounds.play(cue.sound, cue.pitchSteps)
        val felt = cue.haptic != HapticIntensity.None && setting.strength > 0f
        if (felt && !bus.admits(cue.haptic)) return
        haptics.play(cue.haptic, setting.strength)
    }

    companion object {
        /** Defaults to this, so previews, tests and screenshots need arrange nothing. */
        val Silent = Cues(HapticEngine.Silent, SoundPlayer.Silent, HapticsSetting.Off)
    }
}

/**
 * Whether this cue is one of the short clicks that a fast hand can issue faster
 * than an ear can separate them.
 *
 * [Cue.Move], and nothing else. It fires once per engine column step, and a
 * drag across the board steps a column every frame or two: on device that is a
 * click every ~40ms, which stops sounding like steering and starts sounding like
 * a fault. Every other cue in the game is caused by a resolution step, and those
 * are paced by the playback driver.
 */
private val Cue.chatters: Boolean
    get() = sound == Sound.Move

/**
 * The floor on how close together two chattering cues may land.
 *
 * Deliberately not a debounce, which would swallow the *first* click of a drag
 * and make the control feel dead. The first one through always plays; the next
 * one waits out the gap. A player tapping an arrow button never notices this —
 * a deliberate tap is a hundred milliseconds at best — and a drag comes out as a
 * texture instead of a buzz, which is what [Cue.Move]'s own KDoc asks for.
 *
 * The [TimeSource] is a constructor parameter so the gate is testable at all:
 * there is no clock in a composition and a real one cannot be advanced.
 */
private class ChatterGate(private val timeSource: TimeSource) {

    private var last: TimeMark? = null

    fun admits(): Boolean {
        val previous = last
        if (previous != null && previous.elapsedNow() < MinGap) return false
        last = timeSource.markNow()
        return true
    }

    private companion object {
        val MinGap: Duration = 50.milliseconds
    }
}

/**
 * There is one vibrator, and asking it for a second thing cancels the first.
 *
 * `dumpsys vibrator_manager` caught it: a [Cue.Move] and the [Cue.Merge] behind
 * it went out a millisecond apart and the move came back `cancelled_superseded`.
 * A wider [ChatterGate] is not the answer — the two are not the same cue, and
 * widening a window only drops more of them — so this arbitrates instead. While a
 * haptic is still being felt, a request that matters less than it is refused, and
 * a request that matters more takes the vibrator off it.
 *
 * **Refused, not queued.** A haptic is the felt half of something on screen, so
 * one played late is one played over the wrong frame, and a cascade is a run of
 * steps — a queue would fall further behind on each of them and end up describing
 * a cascade that has already finished. Thirty milliseconds late is a different
 * event; four hundred, waiting out a row burst, is a different drop.
 *
 * **The move losing to the merge is the right outcome and it survives the fix.**
 * That pairing is the board answering the steer, and at a millisecond apart no
 * hand feels two taps anyway. What was wrong was that the same collision resolved
 * the same way in the other direction, where it costs something: a steer replayed
 * out of the buffer could cut a `LEVEL UP` short, and the merge after a row burst
 * could cut the burst's decaying roll off less than halfway through — the one
 * effect SPEC 21 says to keep when everything else goes.
 *
 * Equal rank leaves the incumbent alone, because two hits of the same weight
 * inside each other's window are one hit to a hand, and cancelling to re-fire
 * would be strictly worse than letting the first finish.
 */
private class HapticBus(private val timeSource: TimeSource) {

    private var playing: HapticIntensity? = null
    private var since: TimeMark? = null

    fun admits(intensity: HapticIntensity): Boolean {
        val current = playing
        val started = since
        if (current != null && started != null && started.elapsedNow() < current.feltFor) {
            if (intensity.rank <= current.rank) return false
        }
        playing = intensity
        since = timeSource.markNow()
        return true
    }
}

/**
 * What the player would rather feel, when only one of the two can be felt.
 *
 * The order the game's own vocabulary already implies: stacking out outranks the
 * row burst that finished the board, a burst outranks the merges either side of
 * it, a merge outranks the steer that caused it, and a steer outranks nothing.
 * Written out rather than read off `ordinal`, so reordering the enum for
 * readability cannot silently reorder the game's priorities.
 */
internal val HapticIntensity.rank: Int
    get() = when (this) {
        HapticIntensity.None -> 0
        HapticIntensity.Light -> 1
        HapticIntensity.Medium -> 2
        HapticIntensity.Heavy -> 3
        HapticIntensity.Burst -> 4
        HapticIntensity.DoubleSharp -> 5
    }

/**
 * How long this intensity is still being felt after it is asked for.
 *
 * The Android engine's own numbers, which are the longer of the two platforms:
 * 12 / 22 / 40ms for the three hits, `0+60+40+90+40+70+40+50` for the burst
 * waveform and `45+60+70` for the double. See `HapticEngine.android.kt` and
 * `HapticEngine.ios.kt`.
 *
 * A floor rather than a measurement, and the error is one-sided on purpose:
 * neither platform will say what its vibrator is doing, and a value that is too
 * long costs a haptic that could have been felt while one that is too short puts
 * the cancellation straight back.
 */
internal val HapticIntensity.feltFor: Duration
    get() = when (this) {
        HapticIntensity.None -> Duration.ZERO
        HapticIntensity.Light -> 12.milliseconds
        HapticIntensity.Medium -> 22.milliseconds
        HapticIntensity.Heavy -> 40.milliseconds
        HapticIntensity.Burst -> 390.milliseconds
        HapticIntensity.DoubleSharp -> 175.milliseconds
    }

private val HapticsSetting.strength: Float
    get() = when (this) {
        HapticsSetting.Off -> 0f
        HapticsSetting.Light -> 0.5f
        HapticsSetting.Strong -> 1f
    }

val LocalCues = staticCompositionLocalOf { Cues.Silent }

/**
 * Binds [Cues] to this platform's haptics and whatever sound player has been
 * provided.
 *
 * [enabled] is the OS's own haptics availability ANDed with anything the app
 * knows — a player who has silenced haptics system-wide gets silence without the
 * setting having to say so.
 */
@Composable
fun rememberCues(
    setting: HapticsSetting,
    sounds: SoundPlayer = SoundPlayer.Silent,
    enabled: Boolean = true,
): Cues {
    val engine = rememberPlatformHapticEngine()
    return remember(engine, sounds, setting, enabled) {
        Cues(
            haptics = if (enabled) engine else HapticEngine.Silent,
            sounds = sounds,
            setting = setting,
        )
    }
}
