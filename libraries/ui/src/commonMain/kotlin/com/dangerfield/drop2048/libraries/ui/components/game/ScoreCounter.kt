package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.color.ColorResource
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.typography.TypographyResource
import com.dangerfield.drop2048.system.typography.digits
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The running score, counting up to each new value rather than snapping.
 *
 * The count-up is the point. A number that jumps from 3,840 to 4,896 reads as a
 * different number; one that rolls there reads as *earning* 1,056, which is the
 * feedback SPEC 7's whole scoring system exists to deliver.
 *
 * **The roll takes longer for a bigger jump**, and in this game that matters more
 * than it did in the one this is ported from. A merge pays 40 and a row burst
 * pays four thousand (SPEC 7); a flat duration covering both means the burst
 * blurs and the number simply appears to change, which is precisely what the
 * animation exists to avoid. `countUpMillis` is the shape of that, and
 * `ScoreCounterTest` pins the shape rather than the constants so a tuning pass
 * can move the floor and the slope without rewriting it.
 *
 * Drawn in [TypographyResource.digits] and not negotiable at the call site. Until
 * the game has a face with tabular figures the digits are different widths, so a
 * score rolling from 1111 to 2222 visibly wobbles — routing every number through
 * one token is what keeps fixing that a one-line change.
 */
@Composable
fun ScoreCounter(
    score: Int,
    modifier: Modifier = Modifier,
    /**
     * What the counter reads before its first roll.
     *
     * Defaults to [score], because the HUD's counter is on screen for the whole
     * run and has nothing to earn: it starts holding the player's real total and
     * only rolls when that total moves. The stacked-out sheet's counter *appears*
     * already holding its number, so it passes zero and the number is seen to be
     * earned rather than found.
     */
    countFrom: Int = score,
    typography: TypographyResource = AppTheme.typography.Display.D900,
    color: ColorResource = AppTheme.colors.text,
    /**
     * Draw `130.5K` rather than `130450`.
     *
     * Opt-in. The HUD's score sits beside a level and a progress bar in a header
     * that also has to hold the next-two preview (SPEC 8.1), and a seven-figure
     * total there stops being a number and becomes a wall of digits. The
     * stacked-out sheet shows one run's score, which is the number the sheet is
     * *about*, so it stays exact.
     */
    abbreviated: Boolean = false,
) {
    val still = LocalReduceMotion.current || LocalInspectionMode.current
    var displayed by remember { mutableIntStateOf(if (still) score else countFrom) }

    LaunchedEffect(score, still) {
        val start = displayed
        if (still) {
            displayed = score
            return@LaunchedEffect
        }
        if (start == score) return@LaunchedEffect
        val progress = Animatable(0f)
        progress.animateTo(1f, tween(durationMillis = countUpMillis(score - start))) {
            displayed = (start + (score - start) * value).roundToInt()
        }
        displayed = score
    }

    Text(
        text = if (abbreviated) abbreviateScore(displayed) else displayed.toString(),
        typography = typography.digits,
        color = color,
        modifier = modifier,
    )
}

/**
 * A score short enough to sit in a header: `845`, `1.2K`, `130.5K`, `1.2M`.
 *
 * One decimal, always, and dropped when it is zero — `12K` rather than `12.0K`.
 * The decimal is what stops the abbreviation eating the feedback: without it a
 * merge worth 400 points would leave a six-figure total reading `130K` before and
 * after, and the count-up roll above would animate between two identical strings.
 *
 * Under a thousand the number is drawn exactly, because there is nothing to save:
 * `845` is shorter than `0.8K` and it is also true.
 *
 * The promotion at the top is the case worth stating. 999,999 rounds to
 * `1000.0K`, which is longer than the number it abbreviates and reads as a
 * mistake; it becomes `1M`. That is detected by counting digits rather than by
 * comparing against a hand-written 999,950, so the edge cannot drift away from
 * the rounding that produces it.
 */
internal fun abbreviateScore(score: Int): String {
    if (score < Thousand) return score.toString()
    val thousands = oneDecimal(score.toDouble() / Thousand)
    if (thousands.substringBefore('.').length <= AbbreviatedDigits) return thousands + "K"
    return oneDecimal(score.toDouble() / Million) + "M"
}

/** [value] to one decimal place, with a trailing `.0` dropped. */
private fun oneDecimal(value: Double): String {
    val tenths = (value * TENTHS).roundToInt()
    val whole = tenths / TENTHS
    val fraction = tenths % TENTHS
    return if (fraction == 0) whole.toString() else "$whole.$fraction"
}

/**
 * How long to roll, given how far.
 *
 * Linear in the delta between a floor and a ceiling. The floor keeps a single
 * merge from feeling sluggish, and the ceiling keeps a long cascade from holding
 * the screen: past a few thousand points nobody is reading the digits anyway,
 * they are watching it climb, and the extra time buys nothing.
 *
 * Takes the absolute delta. Score only climbs during a run, but an undo (SPEC
 * 5.6) hands this counter a smaller number than it is holding, and a negative
 * delta computed as a duration is a negative duration, which `tween` rejects at
 * runtime rather than at compile time.
 */
internal fun countUpMillis(delta: Int): Int =
    (MinCountUpMillis + abs(delta) * MillisPerPoint).roundToInt()
        .coerceAtMost(MaxCountUpMillis)

/** Quick enough that a single merge still reads as immediate. */
private const val MinCountUpMillis = 320

/** A row burst pays a few thousand, and lands near the ceiling. */
private const val MaxCountUpMillis = 1400

private const val MillisPerPoint = 0.35f

private const val Thousand = 1_000
private const val Million = 1_000_000
private const val TENTHS = 10

/** Above three digits before the point, the next suffix up is the shorter answer. */
private const val AbbreviatedDigits = 3

@Preview
@Composable
private fun ScoreCounterPreview() {
    PreviewContent {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D400),
            modifier = Modifier.padding(Dimension.D400),
        ) {
            ScoreCounter(score = 4_896)
            ScoreCounter(score = 130_450, abbreviated = true)
        }
    }
}
