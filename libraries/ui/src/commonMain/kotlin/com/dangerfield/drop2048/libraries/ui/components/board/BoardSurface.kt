package com.dangerfield.drop2048.libraries.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.dangerfield.drop2048.libraries.ui.Elevation
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.elevation
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.thenIfNotNull
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The well the blocks fall into: a recessed, rounded panel with a gutter of its
 * own around the cells.
 *
 * Bare cells sitting straight on the page read as a spreadsheet. The same cells
 * inside a panel read as a board — a thing you play on — and the gutter it puts
 * between the cells is what stops eleven saturated tiers becoming one bright
 * smear at speed.
 *
 * It lives beside [BlockFace] rather than in the game screen for the same reason
 * every board animation will: there is exactly one board in this app and there
 * should be exactly one place that decides what it looks like.
 *
 * @param contentDescription the one sentence a screen reader hears before it
 *   starts walking the grid. Null leaves the panel unannounced, which is right
 *   for a preview of a single cell and wrong for a board. Supplied by the caller
 *   because `:libraries:ui` holds no copy.
 */
@Composable
fun BoardSurface(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .elevation(Elevation.Card, Radii.R600.shape, color = AppTheme.colors.shadow)
            .background(AppTheme.colors.surfaceSecondary.color, Radii.R600.shape)
            .padding(BoardInset)
            .thenIfNotNull(contentDescription) { label ->
                semantics { this.contentDescription = label; isTraversalGroup = true }
            },
        content = content,
    )
}

/**
 * The margin between the outermost cells and the panel's edge. A little more than
 * the gutter between cells, so the outer ring looks placed rather than cropped.
 */
private val BoardInset = Dimension.D400

/**
 * The gutter between two cells.
 *
 * Lives here rather than in the screen laying out the grid because it is half of
 * what [BoardSurface] is for: the panel only shows *through* if there is
 * something for it to show through. [BoardGeometry] has to be built with the same
 * number or a drag reads the wrong column.
 */
val BoardCellGap: Dp = Dimension.D200

@Preview
@Composable
private fun BoardSurfacePreview() {
    PreviewContent {
        BoardSurface(modifier = Modifier.padding(Dimension.D500)) {
            BlockFace(value = 128, size = Dimension.D1300)
        }
    }
}
