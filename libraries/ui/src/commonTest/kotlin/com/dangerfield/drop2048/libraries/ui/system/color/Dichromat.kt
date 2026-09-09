@file:Suppress("MagicNumber")

package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * What a dichromat sees, by the Viénot–Brettel–Mollon 1999 method.
 *
 * Test-only on purpose. Nothing in the app renders through this — the app ships
 * *designed* palettes rather than filtered ones (SPEC 16), so a runtime filter
 * would be the wrong answer twice over. What it is for is asserting that the
 * three colour-vision palettes are still separated *after* the deficiency they
 * were designed for, which is the only version of that claim worth making. A
 * palette can hold ΔE 40 in normal vision and collapse to 6 for the player it
 * was built for, and nothing about reading the hex codes would tell you.
 *
 * The method: linearise, convert to LMS, project onto the plane the missing cone
 * leaves behind, convert back. The projection matrices are the standard ones for
 * a display white point.
 */
enum class ColorVision {
    Protanopia,
    Deuteranopia,
    Tritanopia,
}

fun Color.asSeenBy(vision: ColorVision): Color {
    val lms = RGB_TO_LMS.times(
        floatArrayOf(linearise(red), linearise(green), linearise(blue)),
    )
    val projected = vision.plane.times(lms)
    val linear = LMS_TO_RGB.times(projected)
    return Color(
        red = delinearise(linear[0]),
        green = delinearise(linear[1]),
        blue = delinearise(linear[2]),
    )
}

private val ColorVision.plane: Array<FloatArray>
    get() = when (this) {
        ColorVision.Protanopia -> arrayOf(
            floatArrayOf(0f, 1.05118294f, -0.05116099f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f),
        )

        ColorVision.Deuteranopia -> arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0.9513092f, 0f, 0.04866992f),
            floatArrayOf(0f, 0f, 1f),
        )

        ColorVision.Tritanopia -> arrayOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(-0.86744736f, 1.86727089f, 0f),
        )
    }

private val RGB_TO_LMS = arrayOf(
    floatArrayOf(0.31399022f, 0.63951294f, 0.04649755f),
    floatArrayOf(0.15537241f, 0.75789446f, 0.08670142f),
    floatArrayOf(0.01775239f, 0.10944209f, 0.87256922f),
)

private val LMS_TO_RGB = arrayOf(
    floatArrayOf(5.47221206f, -4.6419601f, 0.16963708f),
    floatArrayOf(-1.1252419f, 2.29317094f, -0.1678952f),
    floatArrayOf(0.02980165f, -0.19318073f, 1.16364789f),
)

private fun Array<FloatArray>.times(v: FloatArray) = FloatArray(3) { row ->
    this[row][0] * v[0] + this[row][1] * v[1] + this[row][2] * v[2]
}

private fun delinearise(component: Float): Float {
    val clamped = component.coerceIn(0f, 1f)
    return if (clamped <= 0.0031308f) clamped * 12.92f else clamped.pow(1f / 2.4f) * 1.055f - 0.055f
}
