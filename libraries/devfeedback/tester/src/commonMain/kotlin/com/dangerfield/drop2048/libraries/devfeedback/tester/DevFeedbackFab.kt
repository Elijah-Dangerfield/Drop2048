package com.dangerfield.drop2048.libraries.devfeedback.tester

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.dangerfield.drop2048.libraries.devfeedback.FabPlacement
import com.dangerfield.drop2048.libraries.ui.components.icon.Icon
import com.dangerfield.drop2048.libraries.ui.components.icon.IconSize
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import kotlin.math.roundToInt

/**
 * The owner's way into the directive panel: a small button that floats over the
 * app, goes where it is put, and opens the form when tapped.
 *
 * Being movable is the requirement rather than a flourish. Drop 2048's board
 * fills the screen and the HUD takes what is left of it, so *any* fixed position
 * is over something the player needs — and a button pinned over the well is one
 * that gets tapped instead of a block.
 *
 * **It does not take touches it is not over.** The full-size [Box] below is a
 * bare layout node with no pointer handler, so it is invisible to hit testing;
 * only the 48dp button itself has a `pointerInput`, and a gesture that begins
 * anywhere else never reaches this file. That matters more here than in most
 * apps: this thing sits on top of a screen whose entire input surface is a drag.
 *
 * [excludeFromSystemGestures] is needed because Android's gesture navigation
 * claims both screen edges for back, and it claims them for *taps* as well as
 * drags. Dragged flush against an edge — which is exactly where a tester parks
 * it to keep it off the board — the button would otherwise be untappable. The
 * exclusion is scoped to the button's own rect, so it travels with it instead of
 * blanking a strip of the screen.
 */
@Composable
internal fun DevFeedbackFab(
    placement: FabPlacement,
    onSettled: (FabPlacement) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonPx = with(LocalDensity.current) { FabSize.roundToPx() }
    // Neither of these is read during composition. The `offset` lambda reads the
    // position in the layout phase and the drag callback reads it off the
    // pointer coroutine, so a drag relayouts one node per frame and recomposes
    // nothing. This is the same rule as the repo's ban on reading an animated
    // value in a composable body, and for the same reason: the cost of getting
    // it wrong is paid 60 times a second.
    val travel = remember { mutableStateOf(IntSize.Zero) }
    val position = remember { mutableStateOf(placement) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Keeps the button out from under the status and navigation bars,
            // and makes the travel it is clamped to the area it can actually be
            // seen in.
            .safeDrawingPadding()
            .onSizeChanged { travel.value = travelWithin(it, buttonPx) },
    ) {
        Box(
            modifier = Modifier
                .offset { position.value.offsetIn(travel.value) }
                .size(FabSize)
                .excludeFromSystemGestures()
                .clip(CircleShape)
                .background(color = AppTheme.colors.accentPrimary.color, shape = CircleShape)
                // A ring in the background colour, because this thing spends its
                // life parked over arbitrary UI and the accent is also the
                // colour of half the tiles it will be sitting on.
                .border(
                    width = Dimension.D50,
                    color = AppTheme.colors.background.color,
                    shape = CircleShape,
                )
                .pointerInput(onSettled) {
                    detectDragGestures(
                        // Written once, when the finger lifts. Persisting every
                        // frame would put a disk write on the drag.
                        onDragEnd = { onSettled(position.value) },
                    ) { change, dragAmount ->
                        change.consume()
                        position.value = position.value.movedBy(dragAmount, travel.value)
                    }
                }
                // A tap opens the panel and a drag does not, which is not luck.
                // `clickable` consumes the *down*, but `detectDragGestures` arms
                // with `requireUnconsumed = false`, so both are live; the drag
                // then consumes each move past touch slop, and
                // `waitForUpOrCancellation` re-checks consumption on the `Final`
                // pass and gives the click up. Take `clickable` off and this
                // loses its button semantics and its ripple; reorder the two and
                // a button that was only moved also files a directive.
                .clickable(onClick = onClick)
                .semantics { contentDescription = FabDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon = Icons.Bug(null),
                size = IconSize.Small,
                color = AppTheme.colors.onAccentPrimary,
            )
        }
    }
}

/**
 * How far the button can move inside [container], which is the container less
 * the button itself.
 *
 * Floored at zero rather than allowed to go negative: a window narrower than the
 * button has no travel, and a negative one would flip the meaning of the
 * fractions and place the button *outside* the container it is clamped to.
 */
internal fun travelWithin(container: IntSize, buttonPx: Int): IntSize = IntSize(
    width = (container.width - buttonPx).coerceAtLeast(0),
    height = (container.height - buttonPx).coerceAtLeast(0),
)

internal fun FabPlacement.offsetIn(travel: IntSize): IntOffset = IntOffset(
    x = (x * travel.width).roundToInt(),
    y = (y * travel.height).roundToInt(),
)

/**
 * This placement moved by a drag delta in pixels.
 *
 * The clamp is what keeps the button reachable. Without it, a drag off the top
 * of the screen parks the only route to the directive form somewhere it cannot
 * be tapped, and the way back is the QA switch or a reinstall.
 */
internal fun FabPlacement.movedBy(dragAmount: Offset, travel: IntSize): FabPlacement = FabPlacement(
    x = nudge(x, dragAmount.x, travel.width),
    y = nudge(y, dragAmount.y, travel.height),
)

private fun nudge(fraction: Float, delta: Float, travel: Int): Float =
    if (travel <= 0) 0f else (fraction + delta / travel).coerceIn(0f, 1f)

/**
 * 48dp: the minimum comfortable touch target, and no bigger.
 *
 * It has to clear that bar because it is grabbed and dragged, and it must not
 * clear it by much because it spends its life covering the thing being reported
 * on.
 */
private val FabSize = Dimension.D1300

/** What a UI test and `adb shell input tap` both look for. */
internal const val FabDescription = "Leave feedback"
