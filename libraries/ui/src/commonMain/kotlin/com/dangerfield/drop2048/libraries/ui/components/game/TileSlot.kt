package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors

/**
 * An empty cell, and the same cell when the falling tile is in its column.
 *
 * The active-column highlight is the only ambient feedback the board gives about
 * where the tile will land, and it is very quiet on purpose — 3.5% white going to
 * 7.5%. It is a hint the player reads without looking at, not a selection state.
 * The landing ghost is the loud half of the same answer.
 *
 * The colour is animated and read inside `drawBehind`, never during composition:
 * on a five-wide board every column change would otherwise recompose seven cells
 * per frame of a 150ms cross-fade, and the detekt rule fails the build over it.
 */
@Composable
fun TileSlot(
    modifier: Modifier = Modifier,
    active: Boolean = false,
    scale: BoardScale = LocalBoardScale.current,
) {
    val fill = animateColorAsState(
        targetValue = if (active) GameColors.SlotActive else GameColors.Slot,
        animationSpec = tween(durationMillis = Motion.ColumnHighlightMillis),
        label = "TileSlot",
    )
    Box(
        modifier = modifier
            .size(scale.cell)
            .drawBehind { drawSlot(fill) },
    )
}

private fun DrawScope.drawSlot(fill: State<Color>) {
    drawRoundRect(
        color = fill.value,
        cornerRadius = CornerRadius(size.width * TileRadiusPercent / PercentScale),
    )
}

/** The rounded rectangle a slot and a tile share. Same 22%, so they line up exactly. */
val TileShape = RoundedCornerShape(percent = TileRadiusPercent)

private const val PercentScale = 100f
