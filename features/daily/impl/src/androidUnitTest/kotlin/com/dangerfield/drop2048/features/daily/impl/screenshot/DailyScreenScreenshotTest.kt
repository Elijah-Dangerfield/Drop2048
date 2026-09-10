package com.dangerfield.drop2048.features.daily.impl.screenshot

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
import com.dangerfield.drop2048.features.daily.impl.DailyScreen
import com.dangerfield.drop2048.features.daily.impl.DailyState
import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStatus
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The Daily screen in the five states a still frame can hold.
 *
 * The set is chosen so each image is the only one that would move for its own
 * concern. Not played catches the layout and the primary action; in progress
 * catches the fork between resuming an attempt and starting one, which is one
 * boolean away from being collapsed; completed catches the score block and the
 * absence of a Play button; the milestone frame catches the streak track filling
 * and the copy switching from "N days" to the milestone line.
 *
 * The countdown is part of every frame on purpose. It is the only thing on the
 * screen rendered against the player's own clock rather than UTC, so a change
 * that made it render in UTC would move every one of these.
 *
 * [GraphicsMode.Mode.NATIVE] is required rather than preferred: Robolectric's
 * legacy graphics stack draws nothing at all, so every golden would be a
 * correctly-sized transparent rectangle and every comparison would pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class DailyScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun notPlayed() = compose.captureScreen("daily-not-played") {
        state()
    }

    @Test
    fun inProgress() = compose.captureScreen("daily-in-progress") {
        state(
            result = DailyResult(
                date = Day,
                seed = Seed,
                score = 0,
                attemptsUsed = 1,
                completed = false,
            ),
            attemptsAllowed = 2,
        )
    }

    /**
     * A one-attempt day with the attempt in flight, which is the shape every
     * abandoned Daily actually has and which never drew before C3c.
     *
     * The screen offered Resume on `playable && inProgress`, and starting the
     * attempt is what spends it, so those two are never true together under
     * SPEC 14's one-attempt rule. The button that was meant to bring a player
     * back to their board was unreachable. This frame is the one that would go
     * back to "no attempts left" if the condition regressed.
     */
    @Test
    fun inProgressOnASpentDay() = compose.captureScreen("daily-in-progress-spent") {
        state(
            result = DailyResult(
                date = Day,
                seed = Seed,
                score = 0,
                attemptsUsed = 1,
                completed = false,
            ),
        )
    }

    @Test
    fun completed() = compose.captureScreen("daily-completed") {
        state(
            result = DailyResult(
                date = Day,
                seed = Seed,
                score = 18_240,
                attemptsUsed = 1,
                completed = true,
            ),
            streak = DailyStreak(current = 4, best = 11),
        )
    }

    @Test
    fun streakMilestone() = compose.captureScreen("daily-streak-milestone") {
        state(
            result = DailyResult(
                date = Day,
                seed = Seed,
                score = 26_100,
                attemptsUsed = 1,
                completed = true,
            ),
            streak = DailyStreak(current = 7, best = 7),
        )
    }

    private fun state(
        result: DailyResult? = null,
        streak: DailyStreak = DailyStreak(current = 2, best = 9),
        attemptsAllowed: Int = 1,
    ) = DailyState(
        loading = false,
        zone = TimeZone.UTC,
        status = DailyStatus(
            date = Day,
            seed = Seed,
            result = result,
            streak = streak,
            attemptsAllowed = attemptsAllowed,
            retryOffered = false,
            resetsIn = 4.hours + 12.minutes,
            enabled = true,
        ),
    )
}

private fun ComposeContentTestRule.captureScreen(
    name: String,
    state: () -> DailyState,
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
                    DailyScreen(state = state(), onAction = {})
                }
            }
        }
    }
    onNodeWithTag(CaptureTag).captureRoboImage(File(GoldenDirectory, "$name.png"))
}

private val Day = LocalDate(2026, 9, 9)
private const val Seed = 6_768_401_296_324_266_825L

/**
 * Committed, and deliberately outside `build/`. A golden that lives in a build
 * directory disappears on a clean and cannot be reviewed in a pull request, which
 * removes both of the things it was for.
 */
private const val GoldenDirectory = "screenshots"

private const val CaptureTag = "capture"

/** The same small phone `:features:game:impl` captures on (L45). */
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
