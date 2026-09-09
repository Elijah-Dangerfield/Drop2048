@file:Suppress("MagicNumber")

package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The WCAG contrast ratio between two opaque colours, 1.0 to 21.0.
 *
 * Public because contrast is a claim this design system makes, and a claim is
 * only worth making if a test can assert it. [composite] is its other half: ink
 * drawn at partial alpha has the contrast of what lands on screen, not of the
 * colour it was declared as, and judging the second for the first is how a
 * legible-looking palette ships an illegible numeral.
 */
fun contrastRatio(a: Color, b: Color): Float = contrastRatio(a.luminance(), b.luminance())

private fun contrastRatio(a: Float, b: Float): Float {
    val lighter = maxOf(a, b)
    val darker = minOf(a, b)
    return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
}

/**
 * How far apart two colours look, as CIELAB ΔE (1976).
 *
 * Not a contrast ratio and not an RGB distance. Contrast answers "can I read
 * this on that", which is the wrong question for two block faces sitting side by
 * side — two colours can have identical luminance and still be obviously
 * different. RGB distance answers a question about numbers rather than about
 * eyes: it puts two greens further apart than a green and a blue anybody could
 * separate instantly.
 *
 * The rule of thumb it gets used against: ~2.3 is the just-noticeable
 * difference, and anything under about 20 is a pair of tiles a player has to
 * think about.
 */
fun perceptualDistance(a: Color, b: Color): Float {
    val (l1, a1, b1) = a.toLab()
    val (l2, a2, b2) = b.toLab()
    return sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
}

/** [source] drawn over [destination], as an opaque colour. */
fun composite(source: Color, destination: Color): Color {
    val alpha = source.alpha
    return Color(
        red = source.red * alpha + destination.red * (1f - alpha),
        green = source.green * alpha + destination.green * (1f - alpha),
        blue = source.blue * alpha + destination.blue * (1f - alpha),
    )
}

/**
 * The same colour, darker, for the hard shadow under a face.
 *
 * Derived rather than authored per surface. A designer choosing both would
 * eventually choose a pair that does not look like one object in two lights, and
 * there is only one right answer.
 *
 * The drop happens in OKLCH lightness rather than by scaling sRGB channels,
 * which is the handoff's own rule and is also the correct one: scaling channels
 * desaturates as it darkens, so a saturated face and its shadow drift apart in
 * hue exactly where the illusion depends on them being the same object.
 */
fun Color.deepen(amount: Float = ShadowDrop): Color {
    val lch = toOklch()
    return Oklch(
        lightness = (lch.lightness - amount).coerceAtLeast(0f),
        chroma = lch.chroma,
        hue = lch.hue,
    ).toColor().copy(alpha = alpha)
}

internal fun Color.toLab(): Triple<Float, Float, Float> {
    val r = linearise(red)
    val g = linearise(green)
    val b = linearise(blue)
    val x = pivot((X_R * r + X_G * g + X_B * b) / WHITE_X)
    val y = pivot(Y_R * r + Y_G * g + Y_B * b)
    val z = pivot((Z_R * r + Z_G * g + Z_B * b) / WHITE_Z)
    return Triple(LAB_L_SCALE * y - LAB_L_OFFSET, LAB_A_SCALE * (x - y), LAB_B_SCALE * (y - z))
}

internal fun linearise(component: Float): Float =
    if (component <= SRGB_KNEE) {
        component / SRGB_SLOPE
    } else {
        ((component + SRGB_OFFSET) / (1f + SRGB_OFFSET)).pow(SRGB_GAMMA)
    }

private fun pivot(t: Float): Float =
    if (t > LAB_EPSILON) t.pow(1f / 3f) else LAB_KAPPA * t + LAB_L_OFFSET / LAB_L_SCALE

private const val CONTRAST_OFFSET = 0.05f

private const val SRGB_KNEE = 0.04045f
private const val SRGB_SLOPE = 12.92f
private const val SRGB_OFFSET = 0.055f
private const val SRGB_GAMMA = 2.4f

private const val X_R = 0.4124f
private const val X_G = 0.3576f
private const val X_B = 0.1805f
private const val Y_R = 0.2126f
private const val Y_G = 0.7152f
private const val Y_B = 0.0722f
private const val Z_R = 0.0193f
private const val Z_G = 0.1192f
private const val Z_B = 0.9505f
private const val WHITE_X = 0.95047f
private const val WHITE_Z = 1.08883f

private const val LAB_EPSILON = 0.008856f
private const val LAB_KAPPA = 7.787f
private const val LAB_L_SCALE = 116f
private const val LAB_L_OFFSET = 16f
private const val LAB_A_SCALE = 500f
private const val LAB_B_SCALE = 200f
