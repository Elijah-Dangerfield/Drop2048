package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.components.rememberLoopingFloat
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.typography.DigitFontFamily

/**
 * The dashed outline showing where the falling tile will land.
 *
 * Two states, and the difference between them is the whole point of the
 * component. A landing that will **not** merge draws a plain dashed outline: an
 * answer to "where does this go", nothing more. A landing that **will** merge
 * draws a near-white outline, a faint fill, and a `×2` in the middle — an answer
 * to "is this the move", which is the only question the player is actually
 * asking. Collapsing the two into one appearance with an opacity difference
 * throws away the game's best piece of teaching.
 *
 * @param willMerge which of the two states to draw.
 * @param label the mark on a will-merge ghost. Defaulted rather than hardcoded
 *   because `×2` is a symbol rather than copy — it needs no translation, but a
 *   caller that wants `×4` for a three-way touch should be able to say so
 *   without a second component.
 */
@Composable
fun LandingGhost(
    willMerge: Boolean,
    modifier: Modifier = Modifier,
    scale: BoardScale = LocalBoardScale.current,
    label: String = MergeLabel,
) {
    val pulse = rememberLoopingFloat(
        initialValue = RestingOpacity,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (LocalReduceMotion.current) {
                    Motion.GhostPulseMillis * ReducedPulseStretch
                } else {
                    Motion.GhostPulseMillis
                },
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ghost-pulse",
        previewValue = 1f,
    )

    val stroke = if (willMerge) GameColors.GhostMerge else GameColors.GhostPlain
    val fill = if (willMerge) GameColors.GhostMergeFill else null

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(scale.cell)
            .graphicsLayer { alpha = pulse.value }
            .drawBehind {
                val radius = CornerRadius(size.width * TileRadiusPercent / PercentScale)
                if (fill != null) drawRoundRect(color = fill, cornerRadius = radius)
                drawDashedOutline(stroke, radius, scale.ghostStroke.toPx())
            },
    ) {
        if (willMerge) {
            BasicText(
                text = label,
                style = TextStyle(
                    fontFamily = DigitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = scale.ghostLabel.value.sp,
                    color = stroke,
                ),
                maxLines = 1,
            )
        }
    }
}

private fun DrawScope.drawDashedOutline(
    color: Color,
    radius: CornerRadius,
    widthPx: Float,
) {
    val inset = widthPx / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - widthPx, size.height - widthPx),
        cornerRadius = radius,
        style = Stroke(
            width = widthPx,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(widthPx * DashOn, widthPx * DashOff),
            ),
        ),
    )
}

/**
 * The mark on a will-merge ghost. A symbol, not a string resource: `×2` reads
 * the same in every locale this game will ship to, and routing it through
 * `:libraries:resources` would move a design decision into a translation file.
 */
private const val MergeLabel = "×2"

/**
 * The trough of the pulse. The design's `opacity .5 → 1 → .5`, expressed as a
 * reversing loop rather than a three-stop keyframe, which is the same curve.
 */
private const val RestingOpacity = 0.5f

/**
 * Reduce motion slows the ghost rather than stopping it.
 *
 * This is the one loop in the game that is *information* — it is how the player
 * finds the ghost against a busy board — so SPEC 16's "less motion" cannot mean
 * "no ghost". Every other duration in the game shrinks under reduce motion; this
 * one stretches, and it is deliberately the exception.
 */
private const val ReducedPulseStretch = 3

private const val PercentScale = 100f
private const val DashOn = 1.6f
private const val DashOff = 1.2f
