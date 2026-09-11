package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.system.DeepSurface
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.Radius
import com.dangerfield.drop2048.system.typography.FredokaFontFamily

/**
 * The three buttons under the board, and the one visual argument they make.
 *
 * ◀ and ▶ are the game. ▼ ends the drop, and the design paints it a step quieter
 * — a darker face, a muted glyph, a darker shadow — so the row reads as "two
 * things and a helper" rather than as three equal choices. **That recessiveness
 * is intentional and load-bearing.** C1c measured that a player who reaches for
 * the drop control reaches level 4 in 33 seconds against 289 for one who does
 * not, so a centre button that looked like the main verb would be teaching the
 * wrong game, and one that looks like a footnote is the design saying "steer
 * first".
 *
 * The strings are glyphs rather than copy, so they stay here rather than in
 * `:libraries:resources`. The content descriptions are not, which is why they are
 * parameters.
 *
 * @param mirrored swaps ◀ and ▶ for the left-handed setting. A reversal rather
 *   than a second layout, so the two arrangements cannot drift apart and a fourth
 *   control would only have to be added once.
 * @param dropModifier hangs on the centre button alone, so the tutorial can
 *   spotlight it. It carries no gesture: since decision D21 ▼ is a plain click
 *   with no hold, no timeout and no mode, which is exactly what deleted the
 *   latched-soft-drop bug — there is no recogniser left to be torn down mid-press
 *   and no "on" state for it to strand.
 */
@Composable
fun GameControlRow(
    onLeft: () -> Unit,
    onDrop: () -> Unit,
    onRight: () -> Unit,
    leftDescription: String,
    dropDescription: String,
    rightDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mirrored: Boolean = false,
    dropModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().widthIn(max = ControlRowMaxWidth),
        horizontalArrangement = Arrangement.spacedBy(ControlGap),
    ) {
        val leading = if (mirrored) RightGlyph to rightDescription else LeftGlyph to leftDescription
        val trailing = if (mirrored) LeftGlyph to leftDescription else RightGlyph to rightDescription
        val onLeading = if (mirrored) onRight else onLeft
        val onTrailing = if (mirrored) onLeft else onRight

        ControlButton(leading.first, leading.second, onLeading, enabled, ControlKind.Primary)
        ControlButton(
            glyph = DropGlyph,
            contentDescription = dropDescription,
            onClick = onDrop,
            enabled = enabled,
            kind = ControlKind.Quiet,
            modifier = dropModifier,
        )
        ControlButton(trailing.first, trailing.second, onTrailing, enabled, ControlKind.Primary)
    }
}

/** Whether a control is one of the two steering verbs or the drop between them. */
enum class ControlKind { Primary, Quiet }

/**
 * One 60dp-tall chunky button.
 *
 * Public because the game screen will want an arrow on its own somewhere the row
 * does not fit, and because a second implementation of "the game's button" is
 * exactly the duplication `:libraries:ui` exists to prevent.
 */
@Composable
fun RowScope.ControlButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    kind: ControlKind = ControlKind.Primary,
    modifier: Modifier = Modifier,
) {
    val quiet = kind == ControlKind.Quiet
    DeepSurface(
        color = if (quiet) GameColors.ControlQuiet else GameColors.Control,
        shadow = if (quiet) GameColors.ControlQuietShadow else GameColors.ControlShadow,
        highlight = Color.Transparent,
        shape = Radius(CornerSize(ControlRadius)),
        depth = ControlDepth,
        pressedDepth = ControlPressedDepth,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .weight(1f)
            .height(ControlHeight)
            .semantics { this.contentDescription = contentDescription },
    ) {
        BasicText(
            text = glyph,
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = if (quiet) QuietGlyphSize else GlyphSize,
                color = if (quiet) GameColors.InkMuted else GameColors.Ink,
            ),
        )
    }
}

/**
 * The 44dp pause button in the header.
 *
 * Its own composable rather than a small [ControlButton] because it is the one
 * control that is not part of the row: it has its own size, its own radius and
 * its own shadow depth, and squeezing it through the row's API would mean four
 * more parameters on the row's button that only this caller ever sets.
 */
@Composable
fun PauseButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    DeepSurface(
        color = GameColors.Control,
        shadow = GameColors.ControlShadow,
        highlight = Color.Transparent,
        shape = Radius(CornerSize(PauseRadius)),
        depth = PauseDepth,
        pressedDepth = PausePressedDepth,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier
            .size(PauseSize)
            .semantics { this.contentDescription = contentDescription },
    ) {
        BasicText(
            text = PauseGlyph,
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = PauseGlyphSize,
                color = GameColors.Ink,
            ),
        )
    }
}

/**
 * Text characters, not an icon set.
 *
 * The handoff's own note says to swap these for the platform icon set if that
 * reads better natively, and it may well on iOS. They stay as glyphs for now for
 * the reason `BlockFace` draws its marks as geometry: a codepoint is a bet that
 * every font on every target has it. These three are safe (they are in the
 * bundled Fredoka), and swapping them is a one-line change here rather than a
 * change at three call sites.
 */
private const val LeftGlyph = "◀"
private const val RightGlyph = "▶"
private const val DropGlyph = "▼"
private const val PauseGlyph = "II"

private val ControlRowMaxWidth: Dp = 370.dp
private val ControlGap: Dp = 10.dp
private val ControlHeight: Dp = 60.dp
private val ControlRadius: Dp = 20.dp
private val ControlDepth: Dp = 5.dp
private val ControlPressedDepth: Dp = 2.dp
private val GlyphSize = 22.sp
private val QuietGlyphSize = 20.sp

private val PauseSize: Dp = 44.dp
private val PauseRadius: Dp = 14.dp
private val PauseDepth: Dp = 4.dp
private val PausePressedDepth: Dp = 1.dp
private val PauseGlyphSize = 15.sp
