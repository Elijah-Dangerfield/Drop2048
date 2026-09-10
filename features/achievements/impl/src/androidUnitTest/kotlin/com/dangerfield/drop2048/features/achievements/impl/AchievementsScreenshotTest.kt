package com.dangerfield.drop2048.features.achievements.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.feedback.UnlockToastItem
import com.dangerfield.drop2048.libraries.ui.components.feedback.UnlockToasts
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The badge grid, and the toast that announces a badge.
 *
 * The grid is twenty-four near-identical tiles in seven sections, which is
 * exactly the shape whose failures are invisible in review and obvious in a
 * picture: a section that lost its heading, a locked tile that stopped being
 * faded, a progress bar that appeared under every unstarted card.
 *
 * Three frames, and each is the only one that would move for its own concern.
 * [freshInstall] is the state every player sees first, and the one where the
 * locked treatment is doing all the work. [wellPlayed] is the same grid with the
 * earned outline and the lit glyph on it, so the two states can be compared side
 * by side rather than described. [badgeDetail] is the dialog, which draws over
 * the whole window through the design system's host rather than inside the
 * screen's content slot, and [badgeDetailWithoutANumber] is the same dialog on a
 * badge whose copy quotes no number.
 *
 * The toast has its own frame because it renders over the *game*, not here, and
 * a golden of it in `:libraries:ui` would have meant re-recording that module's
 * fifteen goldens to add one.
 *
 * **Two frame sizes.** The 360x640 captures are L45's rule — the tightest frame
 * the app ships to. The tall grid capture is the other job: an inventory of a
 * list longer than any phone, so a section that vanished has somewhere to show
 * up. Every golden that judges layout stays at 360x640.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class AchievementsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun freshInstall() = compose.capture("achievements-locked") {
        AchievementsScreen(state = previewState(earned = emptySet()), onAction = {})
    }

    @Test
    fun wellPlayed() = compose.capture("achievements-unlocked") {
        AchievementsScreen(state = previewState(earned = WellPlayed), onAction = {})
    }

    /** Every shelf, so a section that vanished has somewhere to show up. */
    @Test
    @Config(sdk = [ROBOLECTRIC_SDK], qualifiers = TALL_QUALIFIERS)
    fun wholeGrid() = compose.capture("achievements-full", height = FullGridHeight) {
        AchievementsScreen(state = previewState(earned = WellPlayed), onAction = {})
    }

    @Test
    fun badgeDetail() = compose.capture("achievements-detail") {
        AchievementsScreen(
            state = previewState(earned = WellPlayed).copy(selectedId = AchievementId.ChainOfFive),
            onAction = {},
        )
    }

    /**
     * A badge whose description quotes no number.
     *
     * Every description is resolved with the achievement's own target as a
     * format argument, and most of them have nowhere to put it. This frame is
     * the one that proves an unused argument renders rather than throwing —
     * which is a runtime question a compiler cannot answer.
     */
    @Test
    fun badgeDetailWithoutANumber() = compose.capture("achievements-detail-plain") {
        AchievementsScreen(
            state = previewState(earned = WellPlayed).copy(selectedId = AchievementId.CleanSweep),
            onAction = {},
        )
    }

    @Test
    fun unlockToast() = compose.captureInBox("achievements-unlock-toast") {
        UnlockToasts(
            modifier = Modifier.align(Alignment.TopCenter),
            items = listOf(
                UnlockToastItem(glyph = "🧩", label = ToastLabel, title = "Two of a Kind"),
                UnlockToastItem(glyph = "🧱", label = ToastLabel, title = "Sixty-Four"),
            ),
            onDismiss = {},
        )
    }

    private companion object {
        val WellPlayed = setOf(
            AchievementId.FirstMerge,
            AchievementId.SixtyFour,
            AchievementId.FirstBurst,
            AchievementId.FirstFigures,
            AchievementId.SolidRun,
            AchievementId.OneHour,
            AchievementId.LevelTwenty,
        )

        /** Resolved here rather than from resources: this is a preview-shaped frame. */
        const val ToastLabel = "Achievement unlocked"
    }
}

/**
 * [LocalInspectionMode] is forced on for the whole tree. Every looping animation
 * the design system draws checks it and holds a fixed value; without it Compose
 * never goes idle, the capture waits for an idle that never arrives, and the
 * build **hangs** rather than failing. The unlock toast's dwell timer is one of
 * them, and it is the reason this matters here rather than being boilerplate.
 */
private fun ComposeContentTestRule.captureInBox(
    name: String,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) = capture(name) { Box(modifier = Modifier.width(ShortPhoneWidth).height(ShortPhoneHeight)) { content() } }

private fun ComposeContentTestRule.capture(
    name: String,
    height: Dp = ShortPhoneHeight,
    content: @Composable () -> Unit,
) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(
                    modifier = Modifier
                        .width(ShortPhoneWidth)
                        .height(height)
                        .testTag(CaptureTag),
                ) {
                    content()
                }
            }
        }
    }
    onNodeWithTag(CaptureTag).captureRoboImage(File(GoldenDirectory, "$name.png"))
}

private const val GoldenDirectory = "screenshots"
private const val CaptureTag = "capture"

/** L45: the tightest frame the app ships to, not the roomiest. */
private val ShortPhoneWidth = 360.dp
private val ShortPhoneHeight = 640.dp

/** Tall enough for all seven shelves. Not a device size, and not pretending to be. */
private val FullGridHeight = 2400.dp

internal const val ROBOLECTRIC_SDK = 34

/** Pinned rather than tracking `compileSdk`, as the other harnesses do. */
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"

private const val TALL_QUALIFIERS = "w360dp-h2400dp-xhdpi"
