package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.typography.FredokaFontFamily

/**
 * The 8-second auto-decline on the continue offer (SPEC 8.4 / 12.2), drawn as a
 * ring that empties around the number.
 *
 * ### There is no animation here, and that is the decision
 *
 * The obvious version animates the sweep between whole seconds. It would be a
 * few lines and it would be wrong for three reasons that all point the same way.
 *
 * The countdown is **a rule, not a decoration**: when it reaches zero the offer
 * is declined and the run is over, and that decision belongs to the ViewModel
 * where it can be tested without a frame clock. Drawing straight off the integer
 * the ViewModel publishes means the ring can never disagree with the thing that
 * is actually counting — an animated ring driven by its own spec can sit at 1.2
 * seconds while the rule has already fired.
 *
 * It also removes the hazard this component sits right on top of. An animated
 * value read during composition fails the repo's own detekt rule, and an
 * `animateFloatAsState` left running under `LocalInspectionMode` hangs
 * screenshot capture rather than failing it — so the golden for the continue
 * offer would simply never finish. A `remember`-free integer has neither
 * problem, and the goldens below prove the ring draws at all.
 *
 * The cost is that the sweep steps eight times instead of gliding. On a control
 * counting down to a decision, a step per second is arguably the more honest
 * reading anyway.
 */
@Composable
fun CountdownRing(
    secondsLeft: Int,
    totalSeconds: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val fraction = if (totalSeconds <= 0) 0f else {
        (secondsLeft.coerceIn(0, totalSeconds) / totalSeconds.toFloat())
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(RingSize)
            .semantics { this.contentDescription = contentDescription }
            .drawBehind {
                val inset = RingStroke.toPx() / 2f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(
                    color = GameColors.ProgressTrack,
                    startAngle = 0f,
                    sweepAngle = FullSweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round),
                )
                drawArc(
                    color = GameColors.AccentYellow,
                    startAngle = StartAngle,
                    sweepAngle = FullSweep * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round),
                )
            },
    ) {
        BasicText(
            text = secondsLeft.coerceAtLeast(0).toString(),
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = NumberSize,
                color = GameColors.Ink,
            ),
        )
    }
}

private val RingSize: Dp = 44.dp
private val RingStroke: Dp = 4.dp
private val NumberSize = 20.sp
private const val FullSweep = 360f

/** Twelve o'clock, so the ring drains the way a clock face is read. */
private const val StartAngle = -90f
