package com.dangerfield.drop2048.libraries.ui.components.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.LocalLargeNumbers
import com.dangerfield.drop2048.libraries.ui.system.color.BlockStyle
import com.dangerfield.drop2048.libraries.ui.system.color.TIER_VALUES
import com.dangerfield.drop2048.libraries.ui.system.glossy
import com.dangerfield.drop2048.system.thenIfNotNull
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.typography.DigitFontFamily
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One block: a coloured face on a darker lip, with its value printed on it.
 *
 * **The numeral is not optional and there is no parameter to turn it off.**
 * SPEC 5.1 makes colour a second signal rather than the signal, which is what
 * lets the game ship five palettes without any of them having to carry eleven
 * unmistakable hues on its own. Making the right thing the easy thing means the
 * only way to draw a block is the way that prints its number.
 *
 * The face is sized from [size] rather than from a typography token because a
 * block is a *square with a number in it* — the number has to fit the square at
 * whatever dimension the board turns out to be, and a fixed `sp` would overflow
 * the cell the moment SPEC 3's open question resolves to eight rows on a small
 * phone. Four-digit values get a tighter ratio for the same reason.
 *
 * @param contentDescription what a screen reader says. Passed in rather than
 *   built here: `:libraries:ui` has no string resources, and a design system that
 *   hardcodes English is a design system that has to be unpicked at translation
 *   time. The game feature supplies it (SPEC 16).
 */
@Composable
fun BlockFace(
    value: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    style: BlockStyle = AppTheme.blocks[value],
    contentDescription: String? = null,
) {
    val depth = (size * DepthFraction).coerceAtLeast(MinDepth)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .thenIfNotNull(contentDescription) { label ->
                semantics { this.contentDescription = label }
            }
            .clip(Radii.R300.shape)
            .background(style.edge),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .padding(bottom = depth)
                .clip(Radii.R300.shape)
                .glossy(style.face),
        ) {
            BasicText(
                text = value.toString(),
                style = TextStyle(
                    fontFamily = DigitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = numeralSize(size, value).sp,
                    color = style.ink,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * How big the numeral is, in sp, for a cell of [size] holding [value].
 *
 * Shrinks with digit count so a 1024 and a 2 look like the same family of block
 * rather than two different components, and grows when the player has asked for
 * larger numbers (SPEC 16). The large-numbers scale is read from the
 * CompositionLocal here rather than taken as a parameter, so no caller can forget
 * it (decision D4).
 */
@Composable
private fun numeralSize(size: Dp, value: Int): Float {
    val digits = value.toString().length
    val ratio = when {
        digits <= 2 -> 0.44f
        digits == 3 -> 0.36f
        else -> 0.28f
    }
    val scale = if (LocalLargeNumbers.current) LargeNumberScale else 1f
    return size.value * ratio * scale
}

/**
 * Enough of a lip to read as thickness at the ~44dp a five-wide board gives a
 * cell, floored so a preview chip at half that size does not lose it entirely.
 */
private const val DepthFraction = 0.09f
private val MinDepth: Dp = 2.dp

/** Big enough to be worth the setting, small enough that a 1024 still fits its cell. */
private const val LargeNumberScale = 1.22f

@Preview
@Composable
private fun BlockFaceRampPreview() {
    PreviewContent {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimension.D200),
            modifier = Modifier.padding(Dimension.D400),
        ) {
            TIER_VALUES.take(6).forEach { BlockFace(value = it, size = Dimension.D1300) }
        }
    }
}
