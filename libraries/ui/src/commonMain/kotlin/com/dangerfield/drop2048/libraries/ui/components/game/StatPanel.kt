package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.libraries.ui.system.deepFace
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.typography.DigitFontFamily
import com.dangerfield.drop2048.system.typography.FredokaFontFamily
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The chunky surface every panel outside the board sits on: the handoff's
 * control colour, its hard offset shadow and its inset top highlight, at the
 * panel radius rather than the tile's.
 *
 * A [deepFace] rather than a [DeepSurface][com.dangerfield.drop2048.libraries.ui.system.DeepSurface]
 * because nothing grouped on a stats page is pressable, and wrapping a readout in
 * a `clickable` to borrow the look hands a screen reader a button that does
 * nothing.
 *
 * This is what a meta screen uses instead of
 * [SectionCard][com.dangerfield.drop2048.libraries.ui.components.SectionCard].
 * Both draw a rounded block of grouped content; the difference is that one is a
 * flat fill on a role token and the other has a side you could catch your nail
 * on, and that difference is the entire reason the stats page used to read as a
 * settings list that had been painted dark.
 */
@Composable
fun GamePanel(
    modifier: Modifier = Modifier,
    spacing: Dp = Dimension.D600,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .deepFace(
                color = GameColors.Control,
                shape = Radii.Panel,
                depth = PanelDepth,
                shadow = GameColors.ControlShadow,
            )
            .padding(PanelPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * A titled [GamePanel]: a Fredoka heading, then the block.
 *
 * The heading sits outside the panel rather than inside it so the panels read as
 * a stack of objects on the backdrop rather than as one long card with rules
 * across it. Grouping is the whole point of the page — lifetime totals and bests
 * are different questions — and a heading that is part of the surface it labels
 * groups nothing.
 */
@Composable
fun GameSection(
    title: String,
    modifier: Modifier = Modifier,
    spacing: Dp = Dimension.D600,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        BasicText(
            text = title,
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = SectionTitleSize,
                color = GameColors.Ink,
            ),
            modifier = Modifier.padding(bottom = Dimension.D500),
        )
        GamePanel(spacing = spacing, content = content)
    }
}

/**
 * One headline number on its own chunky plate: the HUD's quiet uppercase label
 * over a loud Fredoka numeral.
 *
 * The same pairing [StatLabel] and the score use on the game screen, which is the
 * point — a stats page is almost entirely numerals, and drawing them the way the
 * score is drawn is what ties the page to the board.
 *
 * Laid out in [FixedWidthDigits] (decision D15) for the same reason the score is:
 * Fredoka's figures are proportional and its `1` is half the width of its `2`, so
 * a column of numbers that are *not* slotted has a ragged left edge that looks
 * like a layout bug rather than like data.
 */
@Composable
fun StatPanel(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = PanelValueSize,
    valueColor: Color = GameColors.Ink,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D200),
        modifier = modifier
            .deepFace(
                color = GameColors.Control,
                shape = Radii.Panel,
                depth = PanelDepth,
                shadow = GameColors.ControlShadow,
            )
            .padding(horizontal = Dimension.D500, vertical = Dimension.D600),
    ) {
        StatLabel(text = label)
        FixedWidthDigits(
            text = value,
            style = TextStyle(
                fontFamily = DigitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = valueSize,
                color = valueColor,
            ),
        )
    }
}

/**
 * One line inside a [GamePanel]: the label on the left, the number on the right.
 *
 * A readout rather than a list row, and the distinction is what the owner was
 * looking at. A list row gives the label and the value the same weight in the
 * same face stacked vertically, which is how a settings screen states a
 * preference. This gives the number the game's numeral face and hangs a quiet
 * wide label off it, which is how the HUD states a score.
 */
@Composable
fun StatReadout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = GameColors.Ink,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatLabel(text = label, fontSize = ReadoutLabelSize)
        FixedWidthDigits(
            text = value,
            style = TextStyle(
                fontFamily = DigitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = ReadoutValueSize,
                color = valueColor,
            ),
        )
    }
}

/** Shallower than a control's 5dp: a panel is a plate, not a button. */
private val PanelDepth: Dp = 4.dp

private val PanelPadding: Dp = 16.dp
private val SectionTitleSize = 20.sp
private val PanelValueSize = 28.sp
private val ReadoutLabelSize = 11.sp
private val ReadoutValueSize = 20.sp

@Preview
@Composable
private fun StatPanelPreview() {
    PreviewContent {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D600)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D500)) {
                StatPanel(label = "RUNS", value = "14", modifier = Modifier.weight(1f))
                StatPanel(label = "AVERAGE", value = "6112", modifier = Modifier.weight(1f))
            }
            GameSection(title = "Lifetime") {
                StatReadout(label = "TOTAL MERGES", value = "1284")
                StatReadout(label = "BLOCKS PLACED", value = "2610")
            }
        }
    }
}
