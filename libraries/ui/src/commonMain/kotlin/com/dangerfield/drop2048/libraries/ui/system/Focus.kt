package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * A named thing on screen that a spotlight can point at.
 *
 * Keys are strings rather than an enum so a feature can mint one per board cell
 * (`"cell-1-7"`) without the design system knowing what a cell is.
 */
@Immutable
data class FocusTargetKey(val value: String)

/**
 * What the spotlight is currently showing: the set of things that stay lit while
 * everything else dims. A null spotlight is the resting state.
 *
 * [anchor] is which of [targets] a card should hang off. It exists because "lit"
 * and "pointed at" stop being the same thing the moment a lesson lights two
 * things — a board the player has to see and the control they have to press.
 * Anchoring on the union of those two spans most of the screen and leaves the
 * card nowhere sensible to go. Null falls back to the union, which is right when
 * everything lit is one cluster.
 */
@Immutable
data class Spotlight(
    val targets: Set<FocusTargetKey>,
    val anchor: FocusTargetKey? = null,
)

/**
 * Registry of where each focusable thing physically is.
 *
 * Positions are reported by [focusTarget] as the layout settles and read by
 * [FocusScrim] when it draws, so the design system can cut holes in a scrim over
 * arbitrary composables without those composables knowing anything about
 * spotlights.
 */
@Stable
class FocusRegistry {
    internal val bounds = mutableStateMapOf<FocusTargetKey, Rect>()

    internal fun report(key: FocusTargetKey, rect: Rect) {
        bounds[key] = rect
    }

    internal fun forget(key: FocusTargetKey) {
        bounds.remove(key)
    }
}

/**
 * Defaults to an empty registry rather than erroring, so previews and screenshot
 * tests get a working no-op without providing anything.
 */
val LocalFocusRegistry = staticCompositionLocalOf { FocusRegistry() }

/**
 * Marks this composable as spotlight-able under [key].
 *
 * Costs one `onGloballyPositioned` per marked element, so mark the things a
 * lesson will actually point at rather than every cell on a board by reflex.
 */
@Composable
fun Modifier.focusTarget(key: FocusTargetKey): Modifier {
    val registry = LocalFocusRegistry.current
    DisposableEffect(key, registry) { onDispose { registry.forget(key) } }
    return this.onGloballyPositioned { coordinates ->
        registry.report(key, Rect(coordinates.positionInRoot(), coordinates.size.toSize()))
    }
}

/**
 * Dims everything except [spotlight]'s targets.
 *
 * The hole is punched with [BlendMode.Clear] into an offscreen layer rather than
 * drawn as four rectangles around the target, which is what lets one spotlight
 * light several scattered cells at once.
 *
 * Place it as the last child of a full-screen `Box` so it covers the content it
 * is dimming.
 *
 * **It takes no pointer input, and that is a deliberate departure from the
 * component this was ported from.** Sodogku's scrim swallows every touch and
 * reports back the ones that land in a hole, because there the lit thing is
 * always a tap target. Drop 2048's first lesson asks the player to *drag the
 * board*, and a gesture cannot be reported back one tap at a time — a scrim that
 * consumed the pointer would make the one thing the lesson asks for the one
 * thing that cannot be done. A `Box` with no `pointerInput` is transparent to
 * hit testing, so every touch reaches the real control underneath and the
 * spotlight is purely a way of pointing.
 *
 * [content] is handed the **union** of the lit rectangles, not one of them, so a
 * caller hanging a card off the spotlight gets the box around everything lit
 * rather than an arbitrary member of the set.
 */
@Composable
fun BoxScope.FocusScrim(
    spotlight: Spotlight?,
    modifier: Modifier = Modifier,
    scrimColor: Color = DefaultScrim,
    cornerRadius: Dp = DefaultCornerRadius,
    padding: Dp = DefaultPadding,
    content: @Composable (Rect) -> Unit = {},
) {
    val registry = LocalFocusRegistry.current
    val fade = remember { Animatable(0f) }
    val fadeMillis = reducible(Motion.FadeMillis)
    val still = LocalInspectionMode.current
    var present by remember { mutableStateOf(spotlight != null) }

    LaunchedEffect(spotlight != null, fadeMillis, still) {
        if (spotlight != null) {
            present = true
            if (still) fade.snapTo(1f) else fade.animateTo(1f, tween(fadeMillis))
        } else {
            if (still) fade.snapTo(0f) else fade.animateTo(0f, tween(fadeMillis))
            present = false
        }
    }

    if (!present) return

    val lit = spotlight?.targets?.mapNotNull { registry.bounds[it] }.orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = fade.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawRect(scrimColor)
                val pad = padding.toPx()
                lit.forEach { rect ->
                    val hole = RoundRect(
                        rect = Rect(
                            offset = Offset(rect.left - pad, rect.top - pad),
                            size = Size(rect.width + pad * 2, rect.height + pad * 2),
                        ),
                        radiusX = cornerRadius.toPx(),
                        radiusY = cornerRadius.toPx(),
                    )
                    drawPath(Path().apply { addRoundRect(hole) }, Color.Transparent, blendMode = BlendMode.Clear)
                }
                drawContent()
            },
    ) {
        val anchor = spotlight?.anchor?.let { registry.bounds[it] } ?: lit.union()
        content(anchor)
    }
}

/** The single box around every lit rectangle, or [Rect.Zero] when nothing is lit. */
private fun List<Rect>.union(): Rect = fold(null as Rect?) { box, rect ->
    if (box == null) {
        rect
    } else {
        Rect(
            left = minOf(box.left, rect.left),
            top = minOf(box.top, rect.top),
            right = maxOf(box.right, rect.right),
            bottom = maxOf(box.bottom, rect.bottom),
        )
    }
} ?: Rect.Zero

private val DefaultScrim = Color(0xC40E0B17)
private val DefaultCornerRadius = 14.dp
private val DefaultPadding = 6.dp
