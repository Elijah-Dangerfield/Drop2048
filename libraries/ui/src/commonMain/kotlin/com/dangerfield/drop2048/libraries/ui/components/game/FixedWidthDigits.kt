package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

/**
 * A number laid out one digit per slot, every slot as wide as the widest numeral
 * in [style]. Decision D15.
 *
 * **The problem this solves is measured rather than suspected.** Fredoka ships
 * with no `tnum` feature at all and ten digits across eight distinct advance
 * widths; at weight 700 its `1` is 379 units against the `2`'s 566. A score
 * rolling from 1,111 to 2,222 therefore changes width as it counts, and during a
 * cascade it does that several times a second. The number the player is meant to
 * be watching becomes the thing that will not hold still.
 *
 * Every alternative costs more. Swapping the face to Nunito ExtraBold is tabular
 * in effect and costs the most prominent number in the game its Fredoka
 * character; patching a `tnum` cut of Fredoka means owning a font build pipeline
 * for one number. This costs slightly looser letterfit than the design's natural
 * spacing, and nothing else.
 *
 * Only digits are slotted. A thousands separator, a decimal point and the `K` of
 * an abbreviated score keep their own widths, because none of them is ever
 * replaced by a *different* glyph mid-roll — the wobble comes from a digit
 * becoming another digit, and only digits do that.
 *
 * The widest numeral is re-measured rather than remembered against [style]. The
 * measurement has to be right on the frame the bundled face finishes resolving,
 * and a cached slot width taken while the platform fallback was still in use is
 * wrong for the rest of the run with nothing on screen to say so. The measurer's
 * own cache makes the repeat cost a lookup.
 */
@Composable
fun FixedWidthDigits(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val slot: Dp = with(LocalDensity.current) {
        Digits.maxOf { measurer.measure(it.toString(), style).size.width }.toDp()
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        text.forEach { character ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = if (character.isDigit()) Modifier.width(slot) else Modifier,
            ) {
                BasicText(text = character.toString(), style = style, maxLines = 1)
            }
        }
    }
}

private val Digits = ('0'..'9').toList()
