package com.dangerfield.drop2048.libraries.ui.components.streak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/** One rung of a streak: reached, or still ahead. */
enum class StreakStopState { Reached, Ahead }

/**
 * One reward day on the track.
 *
 * [description] is the whole thing spoken — "three days, reached" — rather than
 * the number alone, because a reader crossing a row of bare digits announces a
 * phone number.
 */
@Immutable
data class StreakStop(
    val label: String,
    val description: String,
    val state: StreakStopState,
)

/**
 * SPEC 14's rewards at 3, 7, 14 and 30 days, drawn as the rungs they are.
 *
 * A milestone track rather than a month calendar, and the difference is what the
 * player is being asked to care about. A calendar answers "which days did I
 * play", which is history; this answers "how far to the next reward", which is
 * the only question a streak mechanic is actually posing. Sodogku draws a
 * calendar because its streak has freezes and restores to place on specific
 * days; Drop 2048's has neither, so a grid of thirty squares would be thirty
 * squares of decoration around one number.
 *
 * The connecting rule is drawn behind the stops rather than between them so a
 * partially-filled track reads as one continuous thing. It is a plain `Row`: four
 * stops always fit, and laziness would cost the row its intrinsic height inside a
 * scrolling page.
 */
@Composable
fun StreakTrack(
    stops: List<StreakStop>,
    modifier: Modifier = Modifier,
) {
    val reached = AppTheme.colors.accentPrimary.color
    val ahead = AppTheme.colors.borderDisabled.color
    val lastReached = stops.indexOfLast { it.state == StreakStopState.Reached }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StopSize + Dimension.D400)
            .drawBehind {
                val y = size.height / 2
                val step = if (stops.size > 1) size.width / (stops.size - 1) else 0f
                val inset = StopSize.toPx() / 2
                drawLine(
                    color = ahead,
                    start = Offset(inset, y),
                    end = Offset(size.width - inset, y),
                    strokeWidth = RuleWidth.toPx(),
                )
                if (lastReached > 0) {
                    drawLine(
                        color = reached,
                        start = Offset(inset, y),
                        end = Offset(step * lastReached, y),
                        strokeWidth = RuleWidth.toPx(),
                    )
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stops.forEach { stop -> StreakStopDot(stop) }
    }
}

@Composable
private fun StreakStopDot(stop: StreakStop) {
    val fill = AppTheme.colors.accentPrimary.color
    val empty = AppTheme.colors.surfacePrimary.color
    val edge = AppTheme.colors.borderDisabled.color
    val filled = stop.state == StreakStopState.Reached

    Box(
        modifier = Modifier
            .size(StopSize)
            .semantics { contentDescription = stop.description }
            .drawBehind {
                val radius = CornerRadius(size.minDimension / 2)
                drawRoundRect(color = if (filled) fill else empty, cornerRadius = radius)
                if (!filled) {
                    drawRoundRect(
                        color = edge,
                        cornerRadius = radius,
                        style = Stroke(width = RuleWidth.toPx()),
                        topLeft = Offset(RuleWidth.toPx() / 2, RuleWidth.toPx() / 2),
                        size = Size(
                            size.width - RuleWidth.toPx(),
                            size.height - RuleWidth.toPx(),
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stop.label,
            typography = AppTheme.typography.Label.L500,
            color = if (filled) AppTheme.colors.onAccentPrimary else AppTheme.colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private val StopSize = 36.dp
private val RuleWidth = 3.dp

@Preview
@Composable
private fun StreakTrackPreview() {
    PreviewContent {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D600)) {
            StreakTrack(stops = previewStops(reachedThrough = 1))
            StreakTrack(stops = previewStops(reachedThrough = -1))
            StreakTrack(stops = previewStops(reachedThrough = 3))
        }
    }
}

private fun previewStops(reachedThrough: Int) = listOf(3, 7, 14, 30).mapIndexed { index, day ->
    StreakStop(
        label = day.toString(),
        description = "$day days",
        state = if (index <= reachedThrough) StreakStopState.Reached else StreakStopState.Ahead,
    )
}
