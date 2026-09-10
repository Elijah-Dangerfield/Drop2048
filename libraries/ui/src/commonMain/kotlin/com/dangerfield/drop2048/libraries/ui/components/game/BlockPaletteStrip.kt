package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPalettes
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A few tiers of one palette, side by side, as a swatch.
 *
 * The settings row for "block colours" is the one row on that screen where the
 * name is not the answer. "Tritanopia" tells a player nothing about what their
 * board is about to look like, and the five ramps are the most-worked-on part of
 * the design system, so the row shows them.
 *
 * Deliberately **not** the whole eleven-tier ramp: at row width that is eleven
 * slivers, and the pairs a player actually has to separate are neighbours (see
 * `BlockPalette`), so a handful of consecutive tiers says more than all of them.
 *
 * Semantics are cleared rather than described. The palette's *name* is the
 * headline of the row this sits in, and a screen reader announcing six unlabelled
 * coloured boxes after it is noise in the one place it is least welcome.
 */
@Composable
fun BlockPaletteStrip(
    choice: BlockPaletteChoice,
    modifier: Modifier = Modifier,
    swatch: Dp = SwatchSize,
) {
    val palette = BlockPalettes[choice]
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(SwatchGap),
    ) {
        SwatchTiers.forEach { tier ->
            Box(
                modifier = Modifier
                    .size(swatch)
                    .clip(RoundedCornerShape(SwatchRadius))
                    .background(palette.styles[tier].face),
            )
        }
    }
}

/** Five consecutive tiers from the middle of the ramp: 8, 16, 32, 64, 128. */
private val SwatchTiers = listOf(2, 3, 4, 5, 6)

private val SwatchSize = 18.dp
private val SwatchGap = 3.dp
private val SwatchRadius = 4.dp

@Preview
@Composable
private fun BlockPaletteStripPreview() {
    PreviewContent {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(SwatchGap),
        ) {
            BlockPaletteChoice.entries.forEach { BlockPaletteStrip(it) }
        }
    }
}
