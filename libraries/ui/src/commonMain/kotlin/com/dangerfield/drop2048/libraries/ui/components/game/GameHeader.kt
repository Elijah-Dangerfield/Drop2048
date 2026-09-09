package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.typography.NunitoFontFamily

/**
 * `SCORE`, `LEVEL`, `BIGGEST` — the small uppercase letter-spaced label the
 * design hangs every number off.
 *
 * Nunito rather than Fredoka, and that pairing is the whole reason the header
 * reads as a HUD rather than as a heading: the label is quiet and wide, the
 * number below it is loud and tight, and the contrast between the two faces is
 * what makes the number look like a readout.
 *
 * The caller supplies the copy already cased. Uppercasing here would do it with
 * the default locale, and Turkish dotless i is the classic way that goes wrong on
 * somebody else's phone.
 */
@Composable
fun StatLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = LabelSize,
) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = LabelTracking.em,
            color = GameColors.InkMuted,
        ),
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * The 80×6 violet pill under the level number: blocks placed since the last level
 * against the [com.dangerfield.drop2048.libraries.cascade] engine's
 * `blocksPerLevel`.
 *
 * **Not [com.dangerfield.drop2048.libraries.ui.components.LevelProgressBar].**
 * That one is the template's two-tone chunky bar, 14dp tall with a derived lip,
 * and it is right on a stats page where the bar is a piece of content. This is a
 * 6dp hairline in the corner of a game HUD, where a chunky bar with its own lip
 * would compete with the score for the eye. Two bars, two jobs; the alternative
 * is one bar with a `chunky: Boolean`, which is a component with two appearances
 * and one name.
 *
 * **The fraction is the caller's, and the handoff's formula for it is wrong.**
 * The design file draws `(merges % 10) * 10`, which is the level clock this
 * project explicitly rejected: SPEC 5.5 advances a level every 20 *blocks
 * dropped*, because blocks dropped is a clock skill does not accelerate and a
 * merge-driven clock punishes good players with runaway speed. That formula sits
 * in the handoff's visual section, which is exactly why it is worth naming here
 * rather than leaving to be copied.
 *
 * Read in the draw phase, never during composition — a header that recomposed on
 * every frame of a 300ms width transition would re-measure the score beside it.
 */
@Composable
fun LevelMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    width: Dp = MeterWidth,
    height: Dp = MeterHeight,
) {
    val filled = animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = Motion.ProgressMillis),
        label = "level-meter",
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(GameColors.ProgressTrack, RoundedCornerShape(percent = PillPercent))
            .drawBehind { drawFill(filled) },
    )
}

private fun DrawScope.drawFill(filled: State<Float>) {
    val width = size.width * filled.value
    if (width <= 0f) return
    drawRoundRect(
        color = GameColors.AccentViolet,
        size = Size(width = width, height = size.height),
        cornerRadius = CornerRadius(size.height / 2f),
    )
}

private val LabelSize = 10.sp
private const val LabelTracking = 0.16f
private val MeterWidth: Dp = 80.dp
private val MeterHeight: Dp = 6.dp
private const val PillPercent = 50
