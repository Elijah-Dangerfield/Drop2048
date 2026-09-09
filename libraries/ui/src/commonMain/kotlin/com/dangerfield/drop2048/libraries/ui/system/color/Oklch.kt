@file:Suppress("MagicNumber")

package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * A colour in OKLCH: perceptual lightness, chroma, hue angle.
 *
 * The design handoff authors every tile, every accent and every hard shadow in
 * this space and nothing else, so it is the space the design system speaks. That
 * is not a stylistic preference. The handoff's shadow rule is literally
 * "the same colour at `L - 0.22`" and its tile ramp is "one hue per tier at a
 * fixed L and C" — both of those are one-line derivations in OKLCH and neither is
 * expressible in sRGB without picking a second number by eye.
 *
 * [toColor] gamut-clips by clamping each sRGB channel, which is the crude answer.
 * Every value the design ships is inside sRGB, so the clip never fires on a
 * shipped colour; it exists so a retune that wanders out of gamut produces a
 * slightly wrong colour rather than a NaN.
 */
@Immutable
data class Oklch(val lightness: Float, val chroma: Float, val hue: Float) {

    fun toColor(): Color {
        val radians = hue * DegreesToRadians
        val a = chroma * cos(radians)
        val b = chroma * sin(radians)

        val longCone = cube(lightness + 0.3963377774f * a + 0.2158037573f * b)
        val mediumCone = cube(lightness - 0.1055613458f * a - 0.0638541728f * b)
        val shortCone = cube(lightness - 0.0894841775f * a - 1.2914855480f * b)

        return Color(
            red = delinearise(
                4.0767416621f * longCone - 3.3077115913f * mediumCone + 0.2309699292f * shortCone,
            ),
            green = delinearise(
                -1.2684380046f * longCone + 2.6097574011f * mediumCone - 0.3413193965f * shortCone,
            ),
            blue = delinearise(
                -0.0041960863f * longCone - 0.7034186147f * mediumCone + 1.7076147010f * shortCone,
            ),
        )
    }

    /** The same hue and chroma, [amount] darker. The handoff's hard-shadow rule. */
    fun darker(amount: Float): Oklch = copy(lightness = (lightness - amount).coerceAtLeast(0f))
}

/**
 * The OKLCH reading of an sRGB colour.
 *
 * The inverse of [Oklch.toColor], and it is what lets the four accessibility
 * ramps be expressed in the same L/C/H terms the design ramp is without any of
 * them changing colour: they were authored as hex, they are read back here, and
 * from that point on all five palettes derive their ink and their shadow through
 * one code path.
 */
fun Color.toOklch(): Oklch {
    val r = linearise(red)
    val g = linearise(green)
    val b = linearise(blue)

    val longCone = cubeRoot(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val mediumCone = cubeRoot(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val shortCone = cubeRoot(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

    val lightness = 0.2104542553f * longCone + 0.7936177850f * mediumCone - 0.0040720468f * shortCone
    val a = 1.9779984951f * longCone - 2.4285922050f * mediumCone + 0.4505937099f * shortCone
    val bb = 0.0259040371f * longCone + 0.7827717662f * mediumCone - 0.8086757660f * shortCone

    val hue = atan2(bb, a) * RadiansToDegrees
    return Oklch(
        lightness = lightness,
        chroma = hypot(a, bb),
        hue = if (hue < 0f) hue + FullTurn else hue,
    )
}

/** Shorthand for the handoff's own notation, which writes `oklch(0.85 0.17 90)`. */
fun oklch(lightness: Float, chroma: Float, hue: Float): Color =
    Oklch(lightness, chroma, hue).toColor()

private fun cube(value: Float) = value * value * value

private fun cubeRoot(value: Float): Float {
    val root = abs(value).pow(OneThird)
    return if (value < 0f) -root else root
}

private fun delinearise(component: Float): Float {
    val clamped = component.coerceIn(0f, 1f)
    return if (clamped <= SrgbLinearKnee) {
        clamped * SrgbSlope
    } else {
        clamped.pow(1f / SrgbGamma) * (1f + SrgbOffset) - SrgbOffset
    }
}

private const val OneThird = 1f / 3f
private const val FullTurn = 360f
private const val DegreesToRadians = 0.017453292f
private const val RadiansToDegrees = 57.29578f

private const val SrgbLinearKnee = 0.0031308f
private const val SrgbSlope = 12.92f
private const val SrgbOffset = 0.055f
private const val SrgbGamma = 2.4f
