package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.thenIfNotNull

/**
 * The well the tiles fall into: a near-black panel cut into the screen, with a
 * ring around it that goes red when the stack reaches the top.
 *
 * Three things make it read as *cut* rather than as a dark rectangle, and all
 * three are drawn rather than layered:
 *
 * - an inner shadow along the top edge, which is what says the surface is below
 *   the page rather than on it;
 * - a 4dp ring in a lighter violet, drawn outside the fill;
 * - a glow outside the ring in the danger state, so the warning is visible in
 *   peripheral vision — the player is looking at the falling tile, not at the
 *   board's edge, and a warning they have to look at is a warning that arrives
 *   late.
 *
 * The danger cross-fade is 300ms and read in the draw phase. A fast swap to red
 * reads as a rendering glitch rather than as a state change, which is the
 * specific mistake the slow duration exists to avoid.
 *
 * This is a separate component from the template's `BoardSurface`, which dresses
 * a light-theme panel and is what the game screen currently uses. The two will
 * not coexist for long: the game screen restyle replaces one with the other.
 * Adding a `dark: Boolean` to `BoardSurface` instead would have produced a
 * component with two unrelated appearances and one name.
 *
 * @param contentDescription the one sentence a screen reader hears before it
 *   walks the grid. Supplied by the caller — this module holds no copy.
 */
@Composable
fun BoardWell(
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ring = animateColorAsState(
        targetValue = if (danger) GameColors.DangerRing else GameColors.BoardRing,
        animationSpec = tween(durationMillis = Motion.DangerMillis),
        label = "board-ring",
    )
    val glow = animateFloatAsState(
        targetValue = if (danger) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.DangerMillis),
        label = "board-glow",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .thenIfNotNull(contentDescription) { label ->
                semantics { this.contentDescription = label; isTraversalGroup = true }
            }
            .drawBehind { drawWell(ring, glow) }
            .padding(WellPadding)
            .graphicsLayer { clip = true; shape = RoundedCornerShape(InnerRadius) },
        content = content,
    )
}

private fun DrawScope.drawWell(ring: State<Color>, glow: State<Float>) {
    val radius = CornerRadius(BoardRadius.toPx())
    val ringWidth = RingWidth.toPx()

    if (glow.value > 0f) {
        repeat(GlowLayers) { layer ->
            val spread = ringWidth + GlowStep.toPx() * (layer + 1)
            drawRoundRect(
                color = GameColors.DangerGlow.copy(
                    alpha = GameColors.DangerGlow.alpha * glow.value / (GlowLayers * (layer + 1)),
                ),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2f, size.height + spread * 2f),
                cornerRadius = CornerRadius(radius.x + spread),
            )
        }
    }

    drawRoundRect(
        color = ring.value,
        topLeft = Offset(-ringWidth / 2f, -ringWidth / 2f),
        size = Size(size.width + ringWidth, size.height + ringWidth),
        cornerRadius = CornerRadius(radius.x + ringWidth / 2f),
        style = Stroke(width = ringWidth),
    )

    drawRoundRect(color = GameColors.BoardWell, cornerRadius = radius)

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(GameColors.WellInnerShadow, Color.Transparent),
            endY = InnerShadowDepth.toPx(),
        ),
        cornerRadius = radius,
    )
}

/** The design's 8px well padding: the gutter between the outermost cells and the edge. */
val WellPadding: Dp = 8.dp

/** The inner clip, which is the board's 24dp radius minus its padding. */
val InnerRadius: Dp = 16.dp

private val RingWidth: Dp = 4.dp
private val InnerShadowDepth: Dp = 24.dp
private val GlowStep: Dp = 12.dp
private const val GlowLayers = 3
