package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable

/**
 * The platform half of a haptic.
 *
 * Exists because Compose Multiplatform's shared haptics cannot express what
 * SPEC 9 asks for. It offers two `HapticFeedbackType` values; the game needs
 * light, medium, heavy, a sustained burst pattern and a sharp double. Android
 * gets there with `VibrationEffect` composition, iOS with
 * `UIImpactFeedbackGenerator` plus Core Haptics for the pattern, and neither is
 * reachable from common code.
 *
 * [strength] is the player's Off / Light / Strong setting already resolved to a
 * scale, so an implementation never has to know the setting exists.
 *
 * Failing quietly is correct here. A device with no vibrator, a user who has
 * turned haptics off at the OS level, an emulator: none of those is an error
 * worth surfacing, and a game that crashed because it could not buzz would be a
 * worse bug than one that stayed still.
 */
interface HapticEngine {
    fun play(intensity: HapticIntensity, strength: Float)

    companion object {
        val Silent = object : HapticEngine {
            override fun play(intensity: HapticIntensity, strength: Float) = Unit
        }
    }
}

/** The real engine for this platform, or [HapticEngine.Silent] where there is none. */
@Composable
expect fun rememberPlatformHapticEngine(): HapticEngine
