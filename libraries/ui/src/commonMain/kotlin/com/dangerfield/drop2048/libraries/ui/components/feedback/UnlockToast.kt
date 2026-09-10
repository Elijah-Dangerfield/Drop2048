package com.dangerfield.drop2048.libraries.ui.components.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.dangerfield.drop2048.libraries.ui.Elevation
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.bounceClick
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.elevation
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.reduced
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.clip
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * One line of "you just earned something": a glyph, what kind of thing it was,
 * and its name.
 *
 * Deliberately carries no domain type. The design system has no business knowing
 * what an achievement is, and a toast built around three strings is equally
 * usable for a streak milestone or a Pro unlock later.
 */
data class UnlockToastItem(
    val glyph: String,
    val label: String,
    val title: String,
)

/**
 * Congratulations that get out of the way on their own.
 *
 * A **stack**, not a single toast, because the common case is several at once: a
 * player's first finished run can earn First Merge, Sixty-Four and the opening
 * score rung in the same second, and a queue that showed them one after another
 * would hold the celebration hostage for nine seconds on the first run of the
 * game.
 *
 * They dismiss on a tap as well as on the timer. The toast sits over the
 * stacked-out sheet, so somebody who wants to get to Drop again should not have
 * to wait for a badge to finish congratulating them.
 *
 * The dwell shortens under reduce motion (SPEC 16) like every other timing in
 * the design system: the badge is still shown, it is shown for less time and it
 * arrives without the slide.
 *
 * The timer is skipped entirely under `LocalInspectionMode`, because a
 * screenshot test waits for an idle composition and a pending `delay` is not
 * idle. The second delay after the dwell is the exit's own length: clearing the
 * list under the animation makes a toast vanish rather than leave.
 */
@Composable
fun UnlockToasts(
    items: List<UnlockToastItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember(items) { mutableStateOf(items.isNotEmpty()) }
    val inPreview = LocalInspectionMode.current
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(items, inPreview) {
        if (items.isEmpty() || inPreview) return@LaunchedEffect
        delay(reduced(DwellMillis, reduceMotion).toLong())
        shown = false
        delay(reduced(ExitMillis, reduceMotion).toLong())
        onDismiss()
    }

    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D300),
        ) {
            items.forEach { item ->
                UnlockToast(
                    item = item,
                    onClick = {
                        shown = false
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * `bounceClick` comes before the clip and the background, or only the label
 * scales.
 *
 * There is no outline. The first version had one in the accent colour and the
 * golden caught it immediately: `Modifier.border(Border)` takes no shape, so it
 * drew a rectangle across a pill and left four yellow stubs sticking out of the
 * corners. The accent-coloured label is what says "you earned something", and it
 * says it without a second colour.
 */
@Composable
private fun UnlockToast(item: UnlockToastItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .widthIn(max = ToastMaxWidth)
            .elevation(Elevation.Card, Radii.Round.shape)
            .clip(Radii.Round)
            .background(AppTheme.colors.surfacePrimary.color)
            .padding(horizontal = Dimension.D700, vertical = Dimension.D400),
    ) {
        Text(text = item.glyph, typography = AppTheme.typography.Heading.H700)
        Column {
            Text(
                text = item.label,
                typography = AppTheme.typography.Caption.C300,
                color = AppTheme.colors.accentPrimary,
            )
            Text(text = item.title, typography = AppTheme.typography.Body.B600)
        }
    }
}

/**
 * How long a toast holds the screen. Long enough to read a two-word badge name,
 * short enough that three of them stacked are gone before the player has decided
 * what to tap.
 */
private const val DwellMillis = 2_600

private const val ExitMillis = 300

private val ToastMaxWidth = Dimension.D1900 * 3

@Preview
@Composable
private fun UnlockToastsPreview() {
    PreviewContent {
        UnlockToasts(
            items = listOf(
                UnlockToastItem(glyph = "🧱", label = "Achievement unlocked", title = "Sixty-Four"),
                UnlockToastItem(glyph = "⛓", label = "Achievement unlocked", title = "Chain of Five"),
            ),
            onDismiss = {},
        )
    }
}
