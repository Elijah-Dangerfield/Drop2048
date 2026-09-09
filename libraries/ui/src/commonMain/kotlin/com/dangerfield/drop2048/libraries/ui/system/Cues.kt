package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

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
 * every feature remembering to check it.
 */
@Immutable
class Cues(
    private val haptics: HapticEngine,
    private val sounds: SoundPlayer,
    private val setting: HapticsSetting,
) {
    fun play(cue: Cue) {
        sounds.play(cue.sound, cue.pitchSteps)
        haptics.play(cue.haptic, setting.strength)
    }

    companion object {
        /** Defaults to this, so previews, tests and screenshots need arrange nothing. */
        val Silent = Cues(HapticEngine.Silent, SoundPlayer.Silent, HapticsSetting.Off)
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
