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
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.stats.impl.StatsScreen
import com.dangerfield.drop2048.features.stats.impl.StatsState
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
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
 * Three frames, each the only one that would move for its own concern. The
 * populated page catches the headline counter, the bar chart and both cards; the
 * empty page catches the one-line state a fresh install actually sees and is the
 * frame most likely to be forgotten in a refactor; the Daily-only page is
 * decision D19 rendered — fourteen runs played, a real Daily history, and a best
 * score of zero, which is a combination that has to look deliberate rather than
 * broken.
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
        StatsState(loading = false, stats = Played, streak = DailyStreak(current = 4, best = 11))
    }

    @Test
    fun empty() = compose.captureScreen("stats-empty") {
        StatsState(loading = false)
    }

    /**
     * A player whose only finished runs are Dailies. `bestScore` is zero because
     * a Daily cannot own it (D19) while every lifetime total beside it counts
     * those runs, so the page has to read as a rule rather than as a missing
     * number.
     */
    @Test
    fun dailyRunsOnly() = compose.captureScreen("stats-daily-only") {
        StatsState(
            loading = false,
            stats = Played.copy(bestScore = 0),
            streak = DailyStreak(current = 7, best = 7),
        )
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
    state: () -> StatsState,
) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(
                    modifier = Modifier
                        .width(ShortPhoneWidth)
                        .height(ShortPhoneHeight)
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

/**
 * The SDK Robolectric renders against, pinned rather than tracking `compileSdk`.
 * A golden is a pixel comparison and a platform bump moves text metrics, so the
 * SDK moving should be a deliberate re-record with a diff to look at.
 */
internal const val ROBOLECTRIC_SDK = 34

/** A fixed density, for the same reason. `xhdpi` is a whole number of pixels per dp. */
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"
