package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.reducible
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The `CHAIN x2` callout, floating up from the merge that caused it and fading
 * out (SPEC 8.2).
 *
 * **Keyed on [nonce], never on [step] or [text].** Two cascades in a run are
 * routinely the same length, and a value-keyed animation silently skips the
 * second one — the player earns a chain and the board says nothing. The nonce is
 * whatever the feature increments per cascade; anything that changes every time
 * works.
 *
 * It scales with the step, which is the whole reason it is a callout and not a
 * label: a `x2` is a nod and a `x6` should feel like the board shouting. That
 * growth is bounded ([MaxScaledStep]) because a six-wide board can in principle
 * cascade further than the text can grow without leaving the cells it is
 * describing.
 *
 * @param text the copy, supplied by the feature. `:libraries:ui` has no strings,
 *   for the same reason [com.dangerfield.drop2048.libraries.ui.components.board.BlockFace]
 *   takes its content description: a design system that hardcodes English is one
 *   that has to be unpicked at translation time. SPEC 8.2 fixes the shape of it
 *   as `CHAIN xN`.
 * @param step the cascade step this chain reached, 2 upward.
 */
@Composable
fun ChainCallout(
    text: String,
    step: Int,
    nonce: Int,
    modifier: Modifier = Modifier,
    riseBy: Dp = Dimension.D1300,
) {
    val progress = remember { Animatable(1f) }
    val floatMillis = reducible(FloatMillis)
    val still = LocalInspectionMode.current

    LaunchedRise(nonce = nonce, progress = progress, millis = floatMillis, still = still)

    val visible by remember { derivedStateOf { progress.value < 1f } }
    if (!visible) return

    val peak = stepScale(step)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.graphicsLayer {
            val travelled = progress.value
            translationY = -riseBy.toPx() * travelled
            alpha = (1f - travelled).coerceIn(0f, 1f)
            val grown = (travelled * ScaleRamp).coerceAtMost(1f)
            val scale = peak * (StartScale + (1f - StartScale) * grown)
            scaleX = scale
            scaleY = scale
        },
    ) {
        Text(
            text = text,
            typography = AppTheme.typography.Heading.H500,
            color = AppTheme.colors.accentPrimary,
        )
    }
}

/**
 * Runs the rise, and **snaps to zero before any early return**.
 *
 * The unconditional snap is the lesson `BoardCell` cost the sibling repo: an
 * animation that returns early without resetting strands its element wherever the
 * previous run was interrupted, and for a callout that means a `CHAIN x3` frozen
 * half-faded over the board until something else happens to invalidate it.
 *
 * Under `@Preview` it snaps to the start and stops, so the callout is captured at
 * full opacity rather than at whatever phase a screenshot happened to catch.
 */
@Composable
private fun LaunchedRise(nonce: Int, progress: Animatable<Float, *>, millis: Int, still: Boolean) {
    LaunchedEffect(nonce, still) {
        progress.snapTo(0f)
        if (still) return@LaunchedEffect
        if (nonce == 0) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(durationMillis = millis))
    }
}

/**
 * How much bigger a deeper chain draws.
 *
 * A step 2 is the baseline and every step above it adds a fixed fraction, capped.
 * Linear rather than exponential: the audio already climbs a semitone per step
 * (SPEC 9), and two things escalating geometrically at once is how a reward turns
 * into a jump scare.
 */
private fun stepScale(step: Int): Float =
    1f + (step.coerceIn(BaseStep, MaxScaledStep) - BaseStep) * ScalePerStep

private const val BaseStep = 2
private const val MaxScaledStep = 8
private const val ScalePerStep = 0.12f

private const val StartScale = 0.7f
private const val ScaleRamp = 4f

/**
 * Longer than the ~300ms [Motion] holds feedback to, on purpose. This is a
 * reward rather than feedback: it fires only on a cascade, it takes no input
 * away while it runs, and the board underneath is live the whole time.
 *
 * Shortened by [reducible] when the player has asked for less motion, which is
 * the only lever SPEC 16 pulls here — the callout still appears, because it is
 * carrying information and not decoration.
 */
private const val FloatMillis = 900

@Preview
@Composable
private fun ChainCalloutPreview() {
    PreviewContent {
        Box(contentAlignment = Alignment.Center, modifier = Modifier) {
            ChainCallout(text = "CHAIN x4", step = 4, nonce = 1)
        }
    }
}
