package com.dangerfield.drop2048.features.debug.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ads.AdGateSnapshot
import com.dangerfield.drop2048.features.debug.DebugOverrides
import com.dangerfield.drop2048.features.debug.DiagnosticsSettings
import com.dangerfield.drop2048.features.debug.PresetBoard
import com.dangerfield.drop2048.features.debug.TranscriptLine
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.blockOf
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.debug.DiagnosticsOverlay
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The debug menu and the diagnostics overlay.
 *
 * A QA screen has no design to be checked against, so what a golden is for here
 * is the other thing goldens are for on this project (L39a): the menu is six
 * sections of near-identical rows and its failure mode is a control quietly
 * losing its switch, its value or its whole section in a refactor. Invisible in
 * review, obvious in a picture.
 *
 * The overlay has a second reason. Its frame-rate meter draws in `drawBehind`
 * from a `withFrameNanos` loop, which is exactly the shape that hangs capture
 * forever if the `LocalInspectionMode` guard is ever dropped — so this golden is
 * also the thing that notices.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class DebugScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** A menu nobody has touched: every override off, nothing forced. */
    @Test
    fun untouched() = compose.capture("debug-default", height = FullMenuHeight) {
        DebugScreen(state = DebugState(appVersion = Version), onAction = {})
    }

    /**
     * A menu somebody is using: a preset, a level, a forced queue and every
     * overlay switch on. This is the frame that says a control did not vanish.
     */
    @Test
    fun inUse() = compose.capture("debug-in-use", height = FullMenuHeight) {
        DebugScreen(
            state = DebugState(
                appVersion = Version,
                overrides = DebugOverrides(
                    seed = 20_480,
                    startLevel = 12,
                    preset = PresetBoard.CascadeChain,
                    forcedBlocks = listOf(
                        blockOf(BlockValue.V1024),
                        blockOf(Special.WILDCARD),
                        blockOf(Special.BOMB),
                    ),
                    tickIntervalMs = 1000,
                    freezeTimer = true,
                    invincible = true,
                ),
                diagnostics = DiagnosticsSettings(
                    showFrameRate = true,
                    showTick = true,
                    showCellCoordinates = true,
                    showMergeArrows = true,
                    showTranscript = true,
                ),
                proGranted = true,
                dailyStreak = 7,
                soak = SoakSummary(
                    policy = "greedy",
                    cheats = false,
                    seed = 20_480,
                    level = 22,
                    score = 148_000,
                    drops = 431,
                    merges = 512,
                    bursts = 1,
                    deepestCascade = 6,
                    faults = emptyList(),
                    hitDropCap = false,
                ),
                transcript = SampleTranscript,
                ads = AdToolsState(
                    houseAdsAvailable = true,
                    houseAdsSelected = true,
                    reportsReady = true,
                    snapshot = SampleAdSnapshot,
                    lastResult = "new_install",
                ),
            ),
            onAction = {},
        )
    }

    /** A release build, before the passphrase. Nothing behind it to read. */
    @Test
    fun lockedOnARelease() = compose.capture("debug-locked") {
        DebugScreen(
            state = DebugState(
                appVersion = Version,
                needsPassphrase = true,
                passphraseRejected = true,
            ),
            onAction = {},
        )
    }

    /**
     * SPEC 10's keys, and the four rows that turn SPEC 12's three-day wait into
     * four taps.
     *
     * The frame worth pinning is the one with overrides **in force**: a row that
     * stopped saying which of its two numbers is the override, or stopped
     * offering the reset, leaves a tester pinning a key for the life of the
     * install without knowing it — and that is a bug they carry into every
     * other chunk they test.
     */
    @Test
    fun configOverrides() = compose.capture("config-overrides", height = ConfigHeight) {
        ConfigOverridesScreen(
            state = ConfigOverridesState(rows = SampleConfigRows),
            onAction = {},
        )
    }

    @Test
    fun overlay() = compose.capture("debug-overlay", height = OverlayHeight) {
        DiagnosticsOverlay(
            showFrameRate = true,
            intendedTickMs = 500,
            actualTickMs = 512,
            cascadeStep = 3,
            transcript = SampleTranscript.map { "${it.step}  ${it.text}  +${it.points}" },
        )
    }

    private companion object {
        const val Version = "1.0.0 (12)"

        /**
         * A gate that is refusing, because that is the state SPEC 19's readout
         * exists for. A snapshot with nothing blocking would show every number
         * and prove nothing about the line that names the rule.
         */
        val SampleAdSnapshot = AdGateSnapshot(
            adsEnabled = true,
            isPro = false,
            runAlive = false,
            runsThisSession = 1,
            minSessionRuns = 4,
            secondsSinceLastInterstitial = null,
            cooldownSeconds = 180,
            secondsSinceRewarded = null,
            rewardedInFlight = false,
            rewardedGapSeconds = 45,
            daysSinceInstall = 0,
            suppressDaysSinceInstall = 3,
            interstitialPreloaded = true,
            blockedReason = "new_install",
            networkName = "house",
        )

        val SampleTranscript = listOf(
            TranscriptLine(1, "merge (3,6) < (2,6) = 8", 8),
            TranscriptLine(1, "gravity (2,5)>(2,6)", 0),
            TranscriptLine(2, "merge (2,6) v (2,7) = 16", 16),
            TranscriptLine(3, "burst row 7, 5 cells, 1 stone", 6250),
            TranscriptLine(0, "survived level 12", 120),
        )
    }
}

/**
 * [LocalInspectionMode] is forced on for the whole tree, exactly as the other
 * harnesses do it: without it the frame-rate meter's `withFrameNanos` loop never
 * lets Compose go idle and the capture **hangs** rather than failing.
 */
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

/**
 * An inventory of a menu longer than any phone, the way `settings-full` is. It
 * is not a judgement of a layout, which is why it is allowed past 640.
 */
private val FullMenuHeight = 2400.dp

private val OverlayHeight = 240.dp

/** Five keys' worth of rows, each of which is three list items and a field. */
private val ConfigHeight = 2000.dp

internal const val ROBOLECTRIC_SDK = 34
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h2400dp-xhdpi"
