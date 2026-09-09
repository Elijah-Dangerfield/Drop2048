package com.dangerfield.drop2048.libraries.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.components.board.BlockFace
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPalette
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPalettes
import com.dangerfield.drop2048.libraries.ui.system.color.TIER_VALUES
import com.dangerfield.drop2048.libraries.ui.system.color.contrastRatio
import com.dangerfield.drop2048.libraries.ui.system.color.perceptualDistance
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension

/*
 * Block-tier catalog content. The previews live in DesignSystemPreview.kt with
 * every other grouped page — see learning L5: Sodogku's game catalog declared its
 * own incompatible scaffolding, was never aggregated into its design-system
 * preview, and ended up browsable only by someone who already knew the file
 * existed.
 */

/**
 * Every tier in every palette, as a matrix: one row per tier, one column per
 * palette, with the measured numbers in the cells rather than in a footnote.
 *
 * A matrix rather than five separate previews because the failure this exists to
 * catch is *two adjacent tiers colliding in one palette*, and that shows up as
 * two neighbouring cells in a column you can see at once. Five previews compared
 * from memory is exactly how the mistake gets shipped.
 *
 * Every number here is measured live, not transcribed. A number copied into a
 * catalog goes stale the first time somebody nudges a hex, and a stale number
 * beside a swatch is worse than no number.
 *
 * These are the same measurements `BlockPaletteTest` asserts. The test is what
 * stops a bad palette landing; this is what lets an author see *why* before they
 * run it.
 */
@Composable
internal fun BlockTierMatrix() {
    CatalogSection(
        "Tier ramp · all five palettes",
        "One row per tier, one column per palette. The number under each block is its CIELAB " +
            "distance to the tier below it, in that palette — under about 20 is a pair a player " +
            "has to think about, and the test floor for neighbours is 24. The last two columns " +
            "are the worst case across all five: the closest that pair of tiers gets in any " +
            "palette, and the weakest contrast between a numeral and its face.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D300)) {
            MatrixHeader()
            TIER_VALUES.forEachIndexed { tier, value -> MatrixRow(tier, value) }
        }
    }
}

@Composable
private fun MatrixHeader() {
    MatrixRowLayout {
        MatrixLabel("tier", TierColumnWidth)
        BlockPaletteChoice.entries.forEach { MatrixLabel(it.name, SwatchColumnWidth) }
        MatrixLabel("worst ΔE", NumberColumnWidth)
        MatrixLabel("worst ink", NumberColumnWidth)
    }
}

@Composable
private fun MatrixRow(tier: Int, value: Int) {
    MatrixRowLayout {
        MatrixLabel(value.toString(), TierColumnWidth)
        BlockPaletteChoice.entries.forEach { choice ->
            val palette = BlockPalettes[choice]
            Column(
                modifier = Modifier.width(SwatchColumnWidth),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(Dimension.D100),
            ) {
                BlockFace(value = value, size = SwatchSize, style = palette.styles[tier])
                MatrixLabel(palette.deltaToPrevious(tier), SwatchColumnWidth)
            }
        }
        MatrixLabel(worstDeltaToPrevious(tier), NumberColumnWidth)
        MatrixLabel(worstInkRatio(tier), NumberColumnWidth)
    }
}

@Composable
private fun MatrixRowLayout(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
        verticalAlignment = Alignment.Top,
    ) { content() }
}

@Composable
private fun MatrixLabel(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        typography = AppTheme.typography.Caption.C300,
        color = AppTheme.colors.textSecondary,
    )
}

private fun BlockPalette.deltaToPrevious(tier: Int): String =
    if (tier == 0) "—" else perceptualDistance(styles[tier].face, styles[tier - 1].face).oneDecimal()

private fun worstDeltaToPrevious(tier: Int): String =
    if (tier == 0) {
        "—"
    } else {
        BlockPalettes.all.values
            .minOf { perceptualDistance(it.styles[tier].face, it.styles[tier - 1].face) }
            .oneDecimal()
    }

private fun worstInkRatio(tier: Int): String =
    BlockPalettes.all.values
        .minOf { contrastRatio(it.styles[tier].ink, it.styles[tier].face) }
        .oneDecimal() + ":1"

private fun Float.oneDecimal(): String {
    val scaled = (this * 10f).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

private val TierColumnWidth = 56.dp
private val SwatchColumnWidth = 84.dp
private val NumberColumnWidth = 84.dp
private val SwatchSize = 60.dp
