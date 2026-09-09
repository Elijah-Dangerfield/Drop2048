package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The candy treatment: a colour, a lit top and a shaded base.
 *
 * Three things together make a flat rectangle look like an object you could pick
 * up, and it is worth naming why each is there rather than treating "shiny" as
 * one effect.
 *
 * **The base.** A darker band along the bottom, drawn under the fill and peeking
 * out below it. This is the whole illusion — it reads as the side of a thing with
 * thickness, and without it the other two just look like a gradient.
 *
 * **The fill.** A vertical gradient from a lighter tint of the colour to the
 * colour itself, which is what a rounded surface does under a light above it.
 *
 * **The gloss.** A soft white ellipse across the top, well inside the edges. This
 * is the one that is easy to overdo: a hard highlight reads as plastic wrap, and
 * it wants to be barely there.
 *
 * Call it before `clip` so the rounded shape trims all three, and give the
 * caller's padding enough bottom room for [baseHeightFraction] or the numeral
 * sits on the shaded band.
 *
 * **For things that are not pressed.** Anything that takes a press should use
 * [DeepSurface] instead, which puts a face on a lip the press actually drops
 * onto; a static gloss on a control reads as a shadow blob under a pill rather
 * than as something with thickness.
 */
fun Modifier.glossy(
    color: Color,
    enabled: Boolean = true,
    baseHeightFraction: Float = DefaultBaseFraction,
): Modifier = drawBehind {
    if (!enabled) {
        drawRect(color)
        return@drawBehind
    }

    drawRect(color.shade(BaseDarkening))
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color.tint(TopLightening), color),
            endY = size.height * (1f - baseHeightFraction),
        ),
        size = Size(size.width, size.height * (1f - baseHeightFraction)),
    )

    val glossWidth = size.width * GlossWidthFraction
    drawOval(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = GlossAlpha), Color.Transparent),
        ),
        topLeft = Offset(x = (size.width - glossWidth) / 2f, y = size.height * GlossTopFraction),
        size = Size(glossWidth, size.height * GlossHeightFraction),
    )
}

/** Toward black, keeping the hue. */
private fun Color.shade(amount: Float): Color =
    Color(red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha)

/** Toward white, keeping the hue. */
private fun Color.tint(amount: Float): Color = Color(
    red + (1f - red) * amount,
    green + (1f - green) * amount,
    blue + (1f - blue) * amount,
    alpha,
)

/**
 * Thick enough to read as an edge at a glance, thin enough that it never looks
 * like a two-tone button.
 */
private const val DefaultBaseFraction = 0.14f

private const val BaseDarkening = 0.22f
private const val TopLightening = 0.18f

/**
 * Wide and shallow, hugging the top edge.
 *
 * The first pass was narrower, taller and twice as opaque, and on a device it
 * read as a painted white oval rather than as light — the giveaway was that you
 * could see where it stopped. A sheen has no edge you can point at.
 */
private const val GlossWidthFraction = 0.94f
private const val GlossTopFraction = 0.0f
private const val GlossHeightFraction = 0.62f

/** Barely there on purpose. Much above this it stops being light and starts being paint. */
private const val GlossAlpha = 0.20f
