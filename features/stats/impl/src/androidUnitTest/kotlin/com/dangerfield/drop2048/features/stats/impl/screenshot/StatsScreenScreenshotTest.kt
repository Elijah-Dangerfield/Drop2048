package com.dangerfield.drop2048.features.stats.impl.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.stats.impl.StatsScreen
import com.dangerfield.drop2048.features.stats.impl.StatsState
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The stats page, which until C3c had no goldens at all and was the other half of
 * "the app looks like two products".
 *
 * Four frames, each the only one that would move for its own concern. The
 * populated page catches the headline counter, the bar chart and both cards; the
 * empty page catches the one-line state a fresh install actually sees and is the
 * frame most likely to be forgotten in a refactor; the after-one-run page is the
 * version most people will ever see; the tall one is an inventory of every
 * figure, because the page is longer than the phone it is judged on.
 *
 * There was another, `stats-daily-only`, which drew D19's rule: a player whose
 * only runs were Dailies saw fourteen runs played beside a best score of zero.
 * D27 removed the mode, so there is no longer a combination to draw.
 *
 * [GraphicsMode.Mode.NATIVE] is required rather than preferred: Robolectric's
 * legacy graphics stack draws nothing at all, so every golden would be a
 * correctly-sized transparent rectangle and every comparison would pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class StatsScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun populated() = compose.captureScreen("stats-populated") {
        StatsState(loading = false, stats = Played)
    }

    @Test
    fun empty() = compose.captureScreen("stats-empty") {
        StatsState(loading = false)
    }

    /**
     * Every figure on the page in one frame, the way `SettingsScreenshotTest`
     * inventories its list.
     *
     * The populated capture above is 640dp and judges the layout at the
     * tightest frame the app ships to (L45); the page is longer than that, so
     * everything from "Bests" down never appeared in a golden at all. The high
     * score and the highest level the owner asked for on 2026-09-20 sit at
     * opposite ends of the page, and this is the only frame that holds both.
     */
    @Test
    @Config(sdk = [ROBOLECTRIC_SDK], qualifiers = TALL_QUALIFIERS)
    fun wholePage() = compose.captureScreen("stats-full", height = FullPageHeight) {
        StatsState(loading = false, stats = Played)
    }

    /**
     * The page after exactly one finished run, which is the version of it the
     * most people will ever see and the only one nobody had drawn.
     *
     * A one-entry bar chart used to give its single bar the whole width, because
     * every bar takes an equal share and one share is all of it. A card-wide
     * block under "RECENT" reads as a progress bar, not as a score. The bar is
     * now capped at `BarChartDefaults.MaxBarWidth`, and this frame is what would
     * move if that cap were ever removed.
     */
    @Test
    fun afterOneRun() = compose.captureScreen("stats-first-run") {
        StatsState(
            loading = false,
            stats = RunStats(
                runsPlayed = 1,
                bestScore = 2_410,
                averageScore = 2_410,
                highestLevel = 4,
                highestTier = 64,
                totalMerges = 47,
                totalBlocksPlaced = 62,
                longestCascade = 3,
                totalPlaytimeMs = 214_000,
                recentScores = listOf(2_410L),
            ),
        )
    }

    private companion object {
        val Played = RunStats(
            runsPlayed = 14,
            bestScore = 18_240,
            averageScore = 6_112,
            highestLevel = 11,
            highestTier = 1024,
            totalMerges = 1_284,
            totalBlocksPlaced = 2_610,
            longestCascade = 7,
            mostBurstsInARun = 2,
            lifetimeBursts = 5,
            totalPlaytimeMs = 9_240_000,
            recentScores = listOf(18_240L, 4_010L, 9_120L, 2_400L, 6_780L),
        )
    }
}

private fun ComposeContentTestRule.captureScreen(
    name: String,
    height: Dp = ShortPhoneHeight,
    state: () -> StatsState,
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
                    StatsScreen(state = state(), onAction = {})
                }
            }
        }
    }
    onNodeWithTag(CaptureTag).captureRoboImage(File(GoldenDirectory, "$name.png"))
}

/**
 * Committed, and deliberately outside `build/`. A golden that lives in a build
 * directory disappears on a clean and cannot be reviewed in a pull request, which
 * removes both of the things it was for.
 */
private const val GoldenDirectory = "screenshots"

private const val CaptureTag = "capture"

/** The same small phone every other harness captures on (L45). */
private val ShortPhoneWidth = 360.dp
private val ShortPhoneHeight = 640.dp

/** Tall enough for the whole page. Not a device size, and not pretending to be. */
private val FullPageHeight = 1100.dp

/**
 * The SDK Robolectric renders against, pinned rather than tracking `compileSdk`.
 * A golden is a pixel comparison and a platform bump moves text metrics, so the
 * SDK moving should be a deliberate re-record with a diff to look at.
 */
internal const val ROBOLECTRIC_SDK = 34

/** A fixed density, for the same reason. `xhdpi` is a whole number of pixels per dp. */
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"

/**
 * The inventory frame, matching `SettingsScreenshotTest`'s. Taller than any
 * device on purpose: it is a list of everything on the page rather than a
 * judgement of a layout, and every golden that judges layout stays at 360x640.
 */
internal const val TALL_QUALIFIERS = "w360dp-h1100dp-xhdpi"
