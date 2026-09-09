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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.system.color.deepen
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.Radius
import com.dangerfield.drop2048.system.clip

/**
 * A coloured face sitting on a darker lip, which the face drops onto when
 * pressed.
 *
 * The illusion is the lip and nothing else: a band of a darker shade of the same
 * hue, the same shape, showing below the face. The face keeps a constant bottom
 * reserve so the control's height never changes, and the press moves the face
 * down into the lip rather than scaling anything.
 *
 * This is also what a block is made of — see
 * [com.dangerfield.drop2048.libraries.ui.components.board.BlockFace] — which is
 * why the lip colour is derived by the same [deepen] the palette uses. Two ways
 * of drawing "a thing with thickness" in one app is one too many.
 *
 * The press animation is held as `State` and read inside `offset { }` rather than
 * unwrapped with `by`. `offset` already defers to the layout phase; reading the
 * value in composition would recompose the content on every frame of the spring,
 * and is what the `AnimatedStateReadInComposition` detekt rule fails the build
 * over.
 */
@Composable
fun DeepSurface(
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Radius = Radii.Round,
    depth: Dp = DefaultDepth,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val drop = animateDpAsState(
        targetValue = if (pressed && enabled) depth else 0.dp,
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
        Box(Modifier.matchParentSize().clip(shape).background(color.deepen()))
        Box(
            propagateMinConstraints = true,
            modifier = Modifier
                .padding(bottom = depth)
                .offset { IntOffset(x = 0, y = drop.value.roundToPx()) }
                .clip(shape)
                .background(color),
        ) { content() }
    }
}

/**
 * The same face-on-a-lip, for something that is not pressable.
 *
 * [DeepSurface] is a control: it takes a click, collects presses, and drops the
 * face into the lip when you hold it. A next-block preview chip is none of that —
 * it is a label — and wrapping one in a `clickable` to borrow the look would hand
 * a screen reader a button that does nothing.
 *
 * Two rules of the illusion are worth knowing before using it: the darker band
 * has to be the same hue rather than a grey, and the face has to reserve the same
 * [depth] whether or not anything is pressing it, or the thing changes height
 * when it changes state.
 */
fun Modifier.deepFace(
    color: Color,
    shape: Radius = Radii.Round,
    depth: Dp = DefaultDepth,
): Modifier = this
    .background(color.deepen(), shape.shape)
    .padding(bottom = depth)
    .background(color, shape.shape)

/** Deep enough to read as an edge, shallow enough not to look like two stacked pills. */
private val DefaultDepth: Dp = 4.dp
