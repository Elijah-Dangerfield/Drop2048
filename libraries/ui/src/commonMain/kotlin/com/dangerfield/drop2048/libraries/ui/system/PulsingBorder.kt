package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.dangerfield.drop2048.libraries.ui.components.rememberLoopingFloat

/**
 * A border whose colour is resolved during **draw**, not composition.
 *
 * `Modifier.border(width, color, shape)` takes an already-resolved [Color], so
 * feeding it an animated value means recomposing every frame just to produce that
 * colour. Taking a lambda instead moves the snapshot read inside the draw scope,
 * so an animation ticking invalidates draw and nothing else.
 *
 * **This shape is not a preference.** In the sibling repo it came from, the same
 * mistake shipped in two places: a player row's turn pulse recomposed the name,
 * chip count and hand label 471 times in a 25-second trace, and a seat ring did
 * the same on every opponent. Rebuilding that text per frame thrashes Skia's
 * glyph cache and wedges the RenderThread hard enough to ANR — four production
 * traces there all end in `GrTextBlobRedrawCoordinator`. Do not "simplify" this
 * into a `Color` parameter.
 *
 * Callers pass the animation as `State` and read `.value` **inside** [color],
 * never unwrapping it with `by` at the call site. Doing that puts the read back
 * in composition and undoes the whole point, which is what the
 * `AnimatedStateReadInComposition` detekt rule fails the build over.
 *
 * SPEC 8.3's danger state is the first consumer: row 0 gains a pulsing red border
 * while row 1 is occupied. [rememberPulsingColor] is the other half of that.
 */
fun Modifier.pulsingBorder(
    width: Dp,
    shape: Shape,
    color: () -> Color,
): Modifier = drawWithCache {
    val stroke = width.toPx()
    val inset = shape.createOutline(
        Size(size.width - stroke, size.height - stroke),
        layoutDirection,
        this,
    )
    onDrawWithContent {
        drawContent()
        if (stroke > 0f) {
            translate(left = stroke / 2f, top = stroke / 2f) {
                drawOutline(outline = inset, color = color(), style = Stroke(width = stroke))
            }
        }
    }
}

/**
 * A colour breathing between [from] and [to], forever, as `State`.
 *
 * Returned as `State` rather than a `Color` for the same reason [pulsingBorder]
 * takes a lambda: read it in a draw lambda and nothing recomposes.
 *
 * Two things it handles so no caller has to remember them. It holds still under
 * `@Preview` and screenshot capture, because a never-ending animation means
 * Compose never goes idle and a capture that waits for idle hangs rather than
 * fails (see [rememberLoopingFloat]). And it holds still when the player has
 * asked for less motion: SPEC 16 shortens animation rather than removing it, but
 * a *loop* has nothing to shorten — the honest reduced-motion form of a pulse is
 * a colour that simply stays on, which still says "danger" without moving.
 *
 * @param periodMillis one full breath, out and back.
 */
@Composable
fun rememberPulsingColor(
    from: Color,
    to: Color,
    periodMillis: Int = DangerPulseMillis,
): State<Color> {
    if (LocalReduceMotion.current) {
        return remember(to) { mutableStateOf(to) }
    }
    val phase = rememberLoopingFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis / 2),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulsing-color",
        previewValue = 1f,
    )
    return remember(from, to, phase) {
        derivedStateOf { lerp(from, to, phase.value) }
    }
}

/**
 * Slow on purpose, and the same duration the danger state fades in over.
 *
 * A fast pulse on a border reads as a rendering glitch rather than as a warning,
 * and SPEC 8.3's danger state can be on screen for a long time — a player near
 * the top of the board is often there for many drops. Anything quicker becomes
 * something to tune out.
 */
private const val DangerPulseMillis = Motion.DangerMillis * 4
