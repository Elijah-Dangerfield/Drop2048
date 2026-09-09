package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dangerfield.drop2048.libraries.core.Catching
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventParameterIDHapticSharpness
import platform.CoreHaptics.CHHapticEventTypeHapticContinuous
import platform.CoreHaptics.CHHapticEventTypeHapticTransient
import platform.CoreHaptics.CHHapticPattern
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberPlatformHapticEngine(): HapticEngine = remember { IosHapticEngine() }

/**
 * `UIImpactFeedbackGenerator` for the three hits, Core Haptics for the two
 * patterns.
 *
 * The split is not arbitrary. The impact generators are what every other iOS app
 * feels like, so a lock or a merge borrowing them is the game speaking the
 * platform's own language. The burst and the stacked-out double are *patterns* —
 * a shaped envelope and a timed pair — and UIKit has no vocabulary for either;
 * Core Haptics does, at the cost of an engine that has to be started, can be
 * stopped by the system at any time, and does not exist on hardware without a
 * Taptic Engine.
 *
 * Every Core Haptics call is wrapped and every failure falls back to an impact
 * hit rather than to silence. A player on an iPhone SE should still feel that
 * their row burst happened, even if they cannot feel its shape.
 */
@OptIn(ExperimentalForeignApi::class)
private class IosHapticEngine : HapticEngine {

    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)

    private val patternEngine: CHHapticEngine? = Catching {
        CHHapticEngine(null).also { it.startAndReturnError(null) }
    }.getOrNull()

    override fun play(intensity: HapticIntensity, strength: Float) {
        if (intensity == HapticIntensity.None || strength <= 0f) return
        when (intensity) {
            HapticIntensity.None -> Unit
            HapticIntensity.Light -> light.impactOccurredWithIntensity(strength.toDouble())
            HapticIntensity.Medium -> medium.impactOccurredWithIntensity(strength.toDouble())
            HapticIntensity.Heavy -> heavy.impactOccurredWithIntensity(strength.toDouble())
            HapticIntensity.Burst -> playPattern(burstEvents(strength)) { heavy.impactOccurred() }
            HapticIntensity.DoubleSharp -> playPattern(doubleSharpEvents(strength)) {
                heavy.impactOccurred()
            }
        }
    }

    private fun playPattern(events: List<CHHapticEvent>, fallback: () -> Unit) {
        val engine = patternEngine ?: return fallback()
        val played = Catching {
            val pattern = CHHapticPattern(events = events, parameters = emptyList<Any>(), error = null)
            val player = engine.createPlayerWithPattern(pattern, null) ?: return@Catching false
            player.startAtTime(0.0, null)
        }.getOrNull() ?: false
        if (!played) fallback()
    }

    /** A sharp transient, then a continuous roll fading out under it. */
    private fun burstEvents(strength: Float): List<CHHapticEvent> = listOf(
        transient(relativeTime = 0.0, intensity = strength, sharpness = 0.9f),
        CHHapticEvent(
            eventType = CHHapticEventTypeHapticContinuous,
            parameters = listOfNotNull(
                parameter(CHHapticEventParameterIDHapticIntensity, strength * 0.85f),
                parameter(CHHapticEventParameterIDHapticSharpness, 0.35f),
            ),
            relativeTime = 0.02,
            duration = BurstSeconds,
        ),
    )

    private fun doubleSharpEvents(strength: Float): List<CHHapticEvent> = listOf(
        transient(relativeTime = 0.0, intensity = strength, sharpness = 1.0f),
        transient(relativeTime = DoubleGapSeconds, intensity = strength, sharpness = 1.0f),
    )

    private fun transient(relativeTime: Double, intensity: Float, sharpness: Float) = CHHapticEvent(
        eventType = CHHapticEventTypeHapticTransient,
        parameters = listOfNotNull(
            parameter(CHHapticEventParameterIDHapticIntensity, intensity),
            parameter(CHHapticEventParameterIDHapticSharpness, sharpness),
        ),
        relativeTime = relativeTime,
    )

    /**
     * Null when the framework constant is absent, which the interop types allow
     * and the framework never does. Dropping a parameter costs the event its
     * shaping; throwing would cost the player their game.
     */
    private fun parameter(id: String?, value: Float): CHHapticEventParameter? = id?.let {
        CHHapticEventParameter(parameterID = it, value = value.coerceIn(0f, 1f))
    }

    private companion object {
        const val BurstSeconds = 0.34
        const val DoubleGapSeconds = 0.09
    }
}
