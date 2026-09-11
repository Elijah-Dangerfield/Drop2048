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
 * every feature remembering to check it, and it is the one place a rate limit can
 * be applied to the cues that chatter — see [Cue.chatters].
 */
@Immutable
class Cues(
    private val haptics: HapticEngine,
    private val sounds: SoundPlayer,
    private val setting: HapticsSetting,
    timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val chatter = ChatterGate(timeSource)

    fun play(cue: Cue) {
        if (cue.chatters && !chatter.admits()) return
        sounds.play(cue.sound, cue.pitchSteps)
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
