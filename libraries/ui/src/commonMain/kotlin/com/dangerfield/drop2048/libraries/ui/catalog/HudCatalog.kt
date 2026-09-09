package com.dangerfield.drop2048.libraries.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.components.LevelProgressBar
import com.dangerfield.drop2048.libraries.ui.components.game.BoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.SpecialTile
import com.dangerfield.drop2048.libraries.ui.components.game.ChainCallout
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPalettes
import com.dangerfield.drop2048.libraries.ui.system.color.BlockSpecial
import com.dangerfield.drop2048.libraries.ui.system.color.SPECIAL_STYLES
import com.dangerfield.drop2048.libraries.ui.system.color.TIER_VALUES
import com.dangerfield.drop2048.libraries.ui.system.color.contrastRatio
import com.dangerfield.drop2048.libraries.ui.system.color.perceptualDistance
import com.dangerfield.drop2048.libraries.ui.system.pulsingBorder
import com.dangerfield.drop2048.libraries.ui.system.rememberPulsingColor
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii

/*
 * HUD catalog content. Previews live in DesignSystemPreview.kt with every other
 * grouped page (learning L5).
 *
 * Every animation on this page is deliberately still: the score counter, the
 * chain callout and the pulsing border all hold a fixed value under
 * LocalInspectionMode, so the page renders rather than spinning. That means the
 * catalog shows what each one *looks like*, never what it does. Nobody has seen
 * these move yet.
 */

/** The four HUD primitives SPEC 8 asks for, each beside what it is for. */
@Composable
internal fun HudPrimitives() {
    CatalogSection(
        "Score, level and the chain callout",
        "SPEC 8.1's header and 8.2's cascade feedback. The counter rolls to each new score rather " +
            "than snapping, and rolls for longer the bigger the jump — a merge pays 40 and a burst " +
            "pays thousands, and those should not take the same time to count.",
    ) {
        SpecRow(
            "ScoreCounter",
            "Exact by default. `abbreviated = true` is for the header, where a seven-figure total " +
                "stops being a number and becomes a wall of digits.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D900)) {
                ScoreCounter(score = 4_896)
                ScoreCounter(score = 130_450, abbreviated = true)
            }
        }
        SpecRow(
            "LevelProgressBar",
            "Blocks until the next level. Two-tone so the fill reads as the same kind of raised " +
                "object a block does; the dark band is derived from the face, never authored.",
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimension.D300),
                modifier = Modifier.width(BarWidth),
            ) {
                LevelProgressBar(fraction = 0.15f)
                LevelProgressBar(fraction = 0.6f)
                LevelProgressBar(fraction = 1f, height = 10.dp)
            }
        }
        SpecRow(
            "ChainCallout",
            "Keyed on a nonce, never on the value: two cascades in a run are routinely the same " +
                "length, and a value-keyed animation would skip the second one silently. It grows " +
                "with the step, capped at x8.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D1200)) {
                ChainCallout(text = "CHAIN x2", step = 2, nonce = 1)
                ChainCallout(text = "CHAIN x4", step = 4, nonce = 2)
                ChainCallout(text = "CHAIN x8", step = 8, nonce = 3)
            }
        }
        SpecRow(
            "pulsingBorder",
            "SPEC 8.3's danger ring. The colour is resolved in the draw phase, so the pulse never " +
                "recomposes what it surrounds. Still here, and still under reduce motion.",
        ) {
            val danger = rememberPulsingColor(
                from = AppTheme.colors.danger.color.copy(alpha = DimmestPulse),
                to = AppTheme.colors.danger.color,
            )
            Box(
                modifier = Modifier
                    .size(width = DangerWidth, height = DangerHeight)
                    .pulsingBorder(width = DangerStroke, shape = Radii.R600.shape) { danger.value },
            )
        }
    }
}

/**
 * The three specials, and the measured distance from each to the nearest numeric
 * tier in every palette.
 *
 * The numbers are computed live for the same reason the tier matrix's are: a
 * transcribed number goes stale the first time somebody nudges a hex, and a stale
 * number beside a swatch is worse than no number at all.
 */
@Composable
internal fun SpecialBlocks() {
    CatalogSection(
        "Specials · one set, every palette",
        "Wildcard, Bomb and Stone (SPEC 5.2) are not tiers and are not on the ramp, so they are " +
            "three colours rather than fifteen. Each row shows the closest any tier in that " +
            "palette gets, in CIELAB — the floor is 24, the same one neighbouring tiers hold to.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
            SpecialHeader()
            BlockSpecial.entries.forEach { SpecialRow(it) }
        }
    }
}

@Composable
private fun SpecialHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(SpecialColumnWidth).height(1.dp))
        MutedLabel("mark contrast", MeasureColumnWidth)
        BlockPaletteChoice.entries.forEach { MutedLabel(it.name, MeasureColumnWidth) }
    }
}

@Composable
private fun SpecialRow(special: BlockSpecial) {
    val face = SPECIAL_STYLES.getValue(special).face
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(SpecialColumnWidth),
            horizontalArrangement = Arrangement.spacedBy(Dimension.D400),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpecialTile(special = special, scale = SwatchScale)
            Text(text = special.name, typography = AppTheme.typography.Label.L500)
        }
        val style = SPECIAL_STYLES.getValue(special)
        MutedLabel(contrastRatio(style.ink, style.face).oneDecimal() + ":1", MeasureColumnWidth)
        BlockPaletteChoice.entries.forEach { choice ->
            val palette = BlockPalettes[choice]
            val closest = palette.styles
                .mapIndexed { tier, tierStyle -> perceptualDistance(face, tierStyle.face) to TIER_VALUES[tier] }
                .minBy { it.first }
            MutedLabel("${closest.first.oneDecimal()} · ${closest.second}", MeasureColumnWidth)
        }
    }
}

@Composable
private fun MutedLabel(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        typography = AppTheme.typography.Caption.C300,
        color = AppTheme.colors.textSecondary,
    )
}

private fun Float.oneDecimal(): String {
    val scaled = (this * 10f).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

private val SpecialColumnWidth = 180.dp
private val MeasureColumnWidth = 110.dp
private val SwatchScale = BoardScale(em = 16.dp, cell = 56.dp)
private val BarWidth = 260.dp
private val DangerWidth = 220.dp
private val DangerHeight = 64.dp
private val DangerStroke = 3.dp

/** How far the danger ring fades back on the quiet half of its breath. */
private const val DimmestPulse = 0.25f
