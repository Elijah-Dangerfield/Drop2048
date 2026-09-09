package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.system.LocalLargeNumbers
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.chunkyFace
import com.dangerfield.drop2048.libraries.ui.system.color.BlockStyle
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.thenIf
import com.dangerfield.drop2048.system.thenIfNotNull
import com.dangerfield.drop2048.system.typography.DigitFontFamily

/**
 * One tile: a coloured face on a hard shadow of its own hue, with its value
 * printed on it.
 *
 * **The numeral is not optional and there is no parameter to turn it off.**
 * SPEC 5.1 makes colour a second signal rather than the signal, which is what
 * lets the game ship five palettes without any of them having to carry eleven
 * unmistakable hues alone. It matters more since the design ramp landed: that
 * ramp is eleven hues at one lightness, so for a colour-blind player on the
 * default palette the numeral is doing nearly all of the work.
 *
 * The corner radius is **22% of the tile**, proportional rather than a fixed dp,
 * which is the one detail that makes a tile read as a sweet rather than as a cell
 * in a table. Every other dimension comes from [BoardScale], for the reason set
 * out there.
 *
 * @param pop the spawn/merge pop, as `State` so it is read in the draw phase.
 *   Reading it during composition would recompose the whole board on every frame
 *   of every landing, and is what the `AnimatedStateReadInComposition` detekt
 *   rule fails the build over. Drive it with [rememberTilePop].
 * @param lifted draws the falling tile's soft drop shadow. The only soft shadow
 *   on the board, and the only thing that says which tile the player is steering.
 * @param contentDescription what a screen reader says. Passed in rather than
 *   built here: `:libraries:ui` holds no copy, and a design system that hardcodes
 *   English has to be unpicked at translation time.
 */
@Composable
fun Tile(
    value: Int,
    modifier: Modifier = Modifier,
    style: BlockStyle = AppTheme.blocks[value],
    scale: BoardScale = LocalBoardScale.current,
    pop: State<Float>? = null,
    lifted: Boolean = false,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(percent = TileRadiusPercent)
    val numeral = scale.numeral(value).value * if (LocalLargeNumbers.current) LargeNumberScale else 1f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(scale.cell)
            .thenIfNotNull(contentDescription) { label ->
                semantics { this.contentDescription = label }
            }
            .thenIfNotNull(pop) { popping ->
                graphicsLayer {
                    scaleX = popping.value
                    scaleY = popping.value
                }
            }
            .thenIf(lifted) { drawBehind { drawLift() } }
            .background(style.edge, shape)
            .padding(bottom = scale.tileDepth)
            .chunkyFace(style.face, shape, GameColors.TopHighlight, scale.tileHighlight),
    ) {
        BasicText(
            text = value.toString(),
            style = TextStyle(
                fontFamily = DigitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = numeral.sp,
                letterSpacing = NumeralTracking.em,
                lineHeight = OneLine.em,
                color = style.ink,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

/**
 * The spawn/merge pop: `0.55 → 1.14 at 60% → 1.0`, over 300ms.
 *
 * Every landing, every merge result and every gravity-settled tile plays it, so
 * it is the most-seen animation in the game by a wide margin.
 *
 * An `Animatable` rather than the two alternating keyframe names the prototype
 * uses. The prototype's problem is a CSS one: an animation will not re-run on an
 * element whose class did not change, so it flips between two identically defined
 * `popA` / `popB` rules to force it. Compose has no such limitation, so [replay]
 * is the whole mechanism and there is exactly one token for the timing. Do not
 * port the second keyframe.
 */
@Composable
fun rememberTilePop(): TilePop {
    val animatable = remember { Animatable(1f) }
    return remember(animatable) { TilePop(animatable) }
}

/** The handle [rememberTilePop] returns: a value to draw with, and one verb. */
class TilePop internal constructor(private val animatable: Animatable<Float, *>) {

    /** Hand this to [Tile]'s `pop`. Never unwrap it with `by` in composition. */
    val value: State<Float> = animatable.asState()

    /**
     * Play the pop from the start, interrupting one already running.
     *
     * Interrupting is correct rather than merely tolerated. A tile that merges
     * twice in one cascade should pop twice, and a pop that refused to restart
     * because it was mid-flight would silently swallow the second merge's only
     * feedback.
     */
    suspend fun replay(durationMillis: Int = Motion.PopMillis) {
        animatable.snapTo(Motion.PopFrom)
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = Motion.PopEasing),
        )
    }
}

/**
 * The falling tile's lift.
 *
 * Deliberately the only soft shadow anywhere on the board — everything else is a
 * hard offset — which is what makes this one read as "this object is off the
 * surface" rather than as inconsistent styling.
 *
 * Three stacked translucent rounded rects rather than a real blur. A Gaussian
 * blur on a moving element is the most expensive thing on the frame, it is the
 * one graphics API whose Compose Multiplatform support differs by target, and at
 * this size and opacity nobody can tell the difference between it and three
 * rectangles.
 */
private fun DrawScope.drawLift() {
    val radius = size.width * TileRadiusPercent / PercentScale
    repeat(LiftLayers) { layer ->
        val spread = size.width * LiftSpread * (layer + 1)
        drawRoundRect(
            color = GameColors.FallingLift.copy(
                alpha = GameColors.FallingLift.alpha / (LiftLayers * (layer + 1)),
            ),
            topLeft = Offset(x = -spread, y = size.height * LiftDrop),
            size = Size(width = size.width + spread * 2f, height = size.height),
            cornerRadius = CornerRadius(radius + spread),
        )
    }
}

/** The design's `border-radius: 22%`. Proportional, never a fixed dp. */
const val TileRadiusPercent: Int = 22

private const val PercentScale = 100f
private const val NumeralTracking = -0.02f
private const val OneLine = 1f
private const val LargeNumberScale = 1.22f
private const val LiftLayers = 3
private const val LiftSpread = 0.05f
private const val LiftDrop = 0.10f
