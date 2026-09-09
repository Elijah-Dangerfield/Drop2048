package com.dangerfield.drop2048.libraries.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.color.ColorResource
import com.dangerfield.drop2048.libraries.ui.system.color.deepen
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The thin bar under the level number, showing blocks until the next one
 * (SPEC 8.1).
 *
 * Two-tone rather than flat: a lighter face sits on a darker band, so the fill
 * reads as the same kind of raised object a block does, in a recessed track with
 * rounded caps. It is the one progress surface in the game that is *about
 * progression*, which is why it does not reuse [LinearProgressIndicator] — that
 * one is Material underneath and right for a download or a sync, where the point
 * is that something is happening rather than that something is being earned.
 *
 * [fraction] is coerced into `0..1` so a stale rollover or an off-by-one in the
 * level table never escapes into layout. The engine is allowed to be wrong here
 * without the HUD breaking.
 *
 * The dark band is **derived** from [faceColor] rather than taken as a token, the
 * same way `BlockStyle.edge` is derived from its face: there is one right answer
 * for "this colour, in shadow", and a palette author choosing both would
 * eventually choose a pair that does not look like one object in two lights.
 *
 * @param progressBrush an override for the face fill, for a surface that wants a
 *   gradient. The band underneath stays either way.
 */
@Composable
fun LevelProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = DefaultHeight,
    trackColor: ColorResource = AppTheme.colors.surfaceTertiary,
    faceColor: ColorResource = AppTheme.colors.accentPrimary,
    progressBrush: Brush? = null,
) {
    val lip = (height * LipFraction).coerceAtLeast(MinLip)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(Radii.Round.shape)
            .background(trackColor.color),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(Radii.Round.shape),
        ) {
            Box(Modifier.matchParentSize().background(faceColor.color.deepen()))
            val face = Modifier
                .matchParentSize()
                .padding(bottom = lip)
                .clip(Radii.Round.shape)
            Box(
                modifier = if (progressBrush != null) {
                    face.background(progressBrush)
                } else {
                    face.background(faceColor.color)
                },
            )
        }
    }
}

/** Chunky enough to read beside a level number, thin enough not to be a HUD element of its own. */
private val DefaultHeight = 14.dp

/** How much of the dark band shows along the bottom, floored so a short bar keeps its sliver. */
private const val LipFraction = 0.34f
private val MinLip = 2.dp

@Preview
@Composable
private fun LevelProgressBarPreview() {
    PreviewContent {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D400),
            modifier = Modifier.padding(Dimension.D400),
        ) {
            LevelProgressBar(fraction = 0f)
            LevelProgressBar(fraction = 0.5f)
            LevelProgressBar(fraction = 0.9f, height = 10.dp)
        }
    }
}
