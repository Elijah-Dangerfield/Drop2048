package com.dangerfield.drop2048.libraries.ui.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dangerfield.drop2048.libraries.core.Catching

@Composable
actual fun rememberPlatformHapticEngine(): HapticEngine {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = context.vibrator() ?: return@remember HapticEngine.Silent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            EffectHapticEngine(vibrator)
        } else {
            DurationHapticEngine(vibrator)
        }
    }
}

/**
 * Amplitude-controlled one-shots and waveforms, which is the only way to get five
 * distinguishable intensities out of Android.
 *
 * A device without amplitude control still gets the right *shape* — the burst is
 * still a decaying roll and the double is still two hits — it just cannot vary
 * how hard each hit lands. That is worse and still legible, which is the right
 * trade for hardware nobody can do anything about.
 */
@RequiresApi(Build.VERSION_CODES.O)
private class EffectHapticEngine(private val vibrator: Vibrator) : HapticEngine {

    private val hasAmplitudeControl = vibrator.hasAmplitudeControl()

    override fun play(intensity: HapticIntensity, strength: Float) {
        if (intensity == HapticIntensity.None || strength <= 0f) return
        Catching { vibrator.vibrate(effectFor(intensity, strength)) }
    }

    private fun effectFor(intensity: HapticIntensity, strength: Float): VibrationEffect =
        when (intensity) {
            HapticIntensity.None,
            HapticIntensity.Light,
            -> oneShot(TapMillis, LightAmplitude * strength)

            HapticIntensity.Medium -> oneShot(ThudMillis, MediumAmplitude * strength)
            HapticIntensity.Heavy -> oneShot(ThumpMillis, HeavyAmplitude * strength)
            HapticIntensity.Burst -> burst(strength)
            HapticIntensity.DoubleSharp -> doubleSharp(strength)
        }

    /**
     * A hit, then a decaying roll. SPEC 21 calls the burst half of what makes
     * this game feel good, so it is a shaped pattern rather than a longer buzz —
     * a long flat vibration reads as an incoming call.
     */
    private fun burst(strength: Float): VibrationEffect = waveform(
        timings = longArrayOf(0, 60, 40, 90, 40, 70, 40, 50),
        envelope = floatArrayOf(0f, 1f, 0f, 0.75f, 0f, 0.5f, 0f, 0.3f),
        strength = strength,
    )

    /** Two hits close enough together to read as one gesture with a full stop on it. */
    private fun doubleSharp(strength: Float): VibrationEffect = waveform(
        timings = longArrayOf(0, 45, 60, 70),
        envelope = floatArrayOf(0f, 1f, 0f, 1f),
        strength = strength,
    )

    private fun oneShot(millis: Long, amplitude: Float): VibrationEffect =
        if (hasAmplitudeControl) {
            VibrationEffect.createOneShot(millis, amplitude.toAmplitude())
        } else {
            VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
        }

    private fun waveform(
        timings: LongArray,
        envelope: FloatArray,
        strength: Float,
    ): VibrationEffect = if (hasAmplitudeControl) {
        VibrationEffect.createWaveform(
            timings,
            IntArray(envelope.size) { (envelope[it] * strength).toAmplitude() },
            NoRepeat,
        )
    } else {
        VibrationEffect.createWaveform(timings, NoRepeat)
    }

    private fun Float.toAmplitude(): Int =
        if (this <= 0f) 0 else (this * MaxAmplitude).toInt().coerceIn(1, MaxAmplitude)

    private companion object {
        const val NoRepeat = -1
        const val MaxAmplitude = 255

        const val LightAmplitude = 0.35f
        const val MediumAmplitude = 0.65f
        const val HeavyAmplitude = 1.0f

        const val TapMillis = 12L
        const val ThudMillis = 22L
        const val ThumpMillis = 40L
    }
}

/**
 * Pre-Oreo, where duration is the only dial there is.
 *
 * The five intensities collapse onto five lengths. It is a worse haptic
 * vocabulary than the game was designed around, but it is still five distinct
 * things rather than Compose's two, which is the point of having this seam.
 */
private class DurationHapticEngine(private val vibrator: Vibrator) : HapticEngine {

    @Suppress("DEPRECATION")
    override fun play(intensity: HapticIntensity, strength: Float) {
        if (intensity == HapticIntensity.None || strength <= 0f) return
        Catching {
            when (intensity) {
                HapticIntensity.None -> Unit
                HapticIntensity.Light -> vibrator.vibrate(10L)
                HapticIntensity.Medium -> vibrator.vibrate(22L)
                HapticIntensity.Heavy -> vibrator.vibrate(45L)
                HapticIntensity.Burst -> vibrator.vibrate(longArrayOf(0, 60, 40, 90, 40, 70), -1)
                HapticIntensity.DoubleSharp -> vibrator.vibrate(longArrayOf(0, 45, 60, 70), -1)
            }
        }
    }
}

private fun Context.vibrator(): Vibrator? = Catching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}.getOrNull()?.takeIf { it.hasVibrator() }
