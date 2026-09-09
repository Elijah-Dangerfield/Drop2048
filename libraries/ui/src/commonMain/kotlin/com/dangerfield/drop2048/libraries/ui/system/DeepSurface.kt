package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.libraries.ui.system.color.deepen
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.Radius

/**
 * The chunky surface: a coloured face sitting on a hard shadow, with a highlight
 * along its top edge, that drops into the shadow when pressed.
 *
 * **This is the single biggest reason the current build looks flat, so it is
 * worth being precise about what the illusion is made of.** Three parts, and all
 * three have to be there:
 *
 * 1. **A hard offset shadow with no blur.** `0 Npx 0 <darker>`. Not a Material
 *    elevation, not a blur, not an alpha gradient — a solid band of the same hue
 *    at [com.dangerfield.drop2048.libraries.ui.system.color.ShadowDrop] less
 *    lightness, showing below the face. A blurred shadow says "this is floating
 *    above a page". A hard one says "this is a physical object with a side", and
 *    the whole design rests on the second reading.
 * 2. **An inset highlight along the top.** The crescent between the face's shape
 *    and the same shape pushed down a couple of pixels. It reads as a light
 *    source above the board, and without it the face is a flat rectangle sitting
 *    on a darker flat rectangle.
 * 3. **A press that moves the face down by the shadow delta**, so the shadow
 *    shrinks rather than the element scaling. The element's height never changes:
 *    the face reserves [depth] at the bottom whether or not anything is pressing
 *    it.
 *
 * Only the *outer* box propagates its minimum constraints, and that asymmetry is
 * load-bearing in both directions. Propagating from the outer box is what makes
 * the face fill a control given a fixed height. Not propagating from the face is
 * what makes a label be measured at its own size and centred, rather than
 * stretched to the button and drawn from its top-left corner. Add the flag to the
 * inner box and every glyph in the game moves into a corner; add `fillMaxSize`
 * instead and every button that sizes to its content inflates to the screen.
 *
 * @param depth how tall the shadow is at rest.
 * @param pressedDepth how tall it is while held. The face moves down by the
 *   difference, which is how the handoff writes every pressed state
 *   (`translateY(3px)` against a shadow going from 5px to 2px).
 */
@Composable
fun DeepSurface(
    color: Color,
    modifier: Modifier = Modifier,
    shadow: Color = color.deepen(),
    highlight: Color = GameColors.TopHighlight,
    enabled: Boolean = true,
    shape: Radius = Radii.Round,
    depth: Dp = DefaultDepth,
    pressedDepth: Dp = depth * DefaultPressedFraction,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val drop = animateDpAsState(
        targetValue = if (pressed && enabled) depth - pressedDepth else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "DeepSurface",
    )

    Box(
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        ),
    ) {
        Box(Modifier.matchParentSize().background(shadow, shape.shape))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = depth)
                .offset { IntOffset(x = 0, y = drop.value.roundToPx()) }
                .chunkyFace(color, shape.shape, highlight),
        ) { content() }
    }
}

/**
 * The same face-on-a-shadow, for something that is not pressable.
 *
 * [DeepSurface] is a control: it takes a click, collects presses, and drops the
 * face when you hold it. A score panel or a tile is none of that — it is a
 * label — and wrapping one in a `clickable` to borrow the look would hand a
 * screen reader a button that does nothing.
 */
fun Modifier.deepFace(
    color: Color,
    shape: Radius = Radii.Round,
    depth: Dp = DefaultDepth,
    shadow: Color = color.deepen(),
    highlight: Color = GameColors.TopHighlight,
): Modifier = this
    .background(shadow, shape.shape)
    .padding(bottom = depth)
    .chunkyFace(color, shape.shape, highlight)

/**
 * A filled shape with the design's inset top highlight on it.
 *
 * The highlight is the difference between the shape and the same shape
 * translated down, which is what CSS's `inset 0 Npx 0` actually draws. Faking it
 * with a straight band across the top puts colour outside the rounded corners on
 * anything with a radius as large as a tile's, and a tile's radius is 22% of its
 * width.
 *
 * The whole thing is drawn rather than layered as backgrounds because the
 * highlight has to be clipped by the face's own outline, and three stacked
 * `background` calls cannot express that without a clip that would also crop the
 * numeral.
 */
fun Modifier.chunkyFace(
    color: Color,
    shape: Shape,
    highlight: Color = GameColors.TopHighlight,
    highlightDepth: Dp = DefaultHighlightDepth,
): Modifier = drawWithContent {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, color)
    if (highlight.alpha > 0f && highlightDepth > 0.dp) {
        drawHighlight(outline, shape, highlight, highlightDepth.toPx())
    }
    drawContent()
}

private fun DrawScope.drawHighlight(
    outline: Outline,
    shape: Shape,
    highlight: Color,
    depthPx: Float,
) {
    val face = Path().apply { addOutline(outline) }
    val pushed = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawHighlight))
        translate(Offset(x = 0f, y = depthPx))
    }
    drawPath(Path().apply { op(face, pushed, PathOperation.Difference) }, highlight)
}

private fun DrawScope.drawOutline(outline: Outline, color: Color) {
    when (outline) {
        is Outline.Rectangle -> drawRect(color, size = size)
        is Outline.Rounded -> drawPath(Path().apply { addOutline(outline) }, color)
        is Outline.Generic -> drawPath(outline.path, color)
    }
}

private fun Path.addOutline(outline: Outline) {
    when (outline) {
        is Outline.Rectangle -> addRect(outline.rect)
        is Outline.Rounded -> addRoundRect(outline.roundRect)
        is Outline.Generic -> addPath(outline.path)
    }
}

/** Deep enough to read as an edge, shallow enough not to look like two stacked pills. */
val DefaultDepth: Dp = 5.dp

/** The handoff's controls go from a 5px shadow to a 2px one. */
private const val DefaultPressedFraction = 0.4f

/** `inset 0 2px 0` at the scale most of the UI is drawn at. */
val DefaultHighlightDepth: Dp = 2.dp
