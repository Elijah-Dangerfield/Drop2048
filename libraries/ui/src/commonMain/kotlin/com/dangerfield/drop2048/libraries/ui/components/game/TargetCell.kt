package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The cell a guided beat is telling the player to put the block in.
 *
 * ### Why it is not the landing ghost
 *
 * The ghost answers "where does this go if I let go now", and it moves with the
 * finger. This answers "where should it go", and it does not move at all. During
 * the tutorial's opening beat both are on the board at once and they mean
 * opposite things until the player has done what was asked, at which point they
 * sit on the same cell — which is the moment the beat is trying to produce. So
 * it is drawn deliberately unlike the ghost: a solid accent ring and a faint
 * wash rather than a dashed white outline, and no `×2`.
 *
 * ### Why it does not pulse
 *
 * Every other attention-seeking thing on this board loops, and a loop here would
 * be the third animation in one frame next to a pulsing ghost and a tile in
 * flight. It is also the cheap way to stay out of the screenshot trap: a looping
 * animation that forgets `LocalInspectionMode` does not fail capture, it hangs
 * it. A static ring against a dashed, pulsing ghost is already the loudest
 * distinction available.
 */
@Composable
fun TargetCell(
    modifier: Modifier = Modifier,
    scale: BoardScale = LocalBoardScale.current,
) {
    val strokeWidth = scale.ghostStroke * StrokeScale
    Box(
        modifier = modifier
            .size(scale.cell)
            .drawBehind {
                val radius = CornerRadius(size.width * TileRadiusPercent / PercentScale)
                drawRoundRect(color = GameColors.AccentYellow.copy(alpha = WashAlpha), cornerRadius = radius)
                val inset = strokeWidth.toPx() / 2f
                drawRoundRect(
                    color = GameColors.AccentYellow,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = radius,
                    style = Stroke(width = strokeWidth.toPx()),
                )
            },
    )
}

/**
 * Half again as thick as the ghost's dashes. The two are the same size and sit
 * in the same grid, so weight is what tells them apart at a glance.
 */
private const val StrokeScale = 1.5f

/** Enough to read as a filled cell on the dark well, not enough to read as a tile. */
private const val WashAlpha = 0.10f

private const val PercentScale = 100f

@Preview
@Composable
private fun TargetCellPreview() {
    PreviewContent {
        Box(modifier = Modifier.size(PreviewBoxSize)) {
            TargetCell()
        }
    }
}

private val PreviewBoxSize: Dp = 96.dp
