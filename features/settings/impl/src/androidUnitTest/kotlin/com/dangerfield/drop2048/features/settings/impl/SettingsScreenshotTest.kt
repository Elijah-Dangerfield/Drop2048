package com.dangerfield.drop2048.features.settings.impl

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
import com.dangerfield.drop2048.features.settings.PlayerSettings
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The settings screen, and the two destructive dialogs.
 *
 * The handoff lists settings under "not designed yet", so there is no drawing to
 * check this against. What a golden is for here is the other thing: the screen is
 * eight sections of near-identical rows, and the failure mode is a row quietly
 * losing its switch, its hint or its whole section in a refactor — invisible in
 * review, obvious in a picture.
 *
 * **Two frame sizes, deliberately.** The 360x640 captures are L45's rule: the
 * tightest frame the app ships to, which is what says whether the top of the
 * screen is usable. The tall capture is a different job — an inventory of every
 * row on a list that is longer than any phone — and it is the only golden in the
 * project allowed to be taller than a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class SettingsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun freshInstall() = compose.capture("settings-default") {
        SettingsScreen(state = SettingsState(appVersion = Version), onAction = {})
    }

    /** Every row on the list, so a section that vanished has somewhere to show up. */
    @Test
    @Config(sdk = [ROBOLECTRIC_SDK], qualifiers = TALL_QUALIFIERS)
    fun wholeList() = compose.capture("settings-full", height = FullListHeight) {
        SettingsScreen(state = SettingsState(appVersion = Version), onAction = {})
    }

    /**
     * The same screen for a player who uses it, and the reason the palette rows
     * carry a swatch: five names for five ramps say nothing on their own.
     */
    @Test
    @Config(sdk = [ROBOLECTRIC_SDK], qualifiers = TALL_QUALIFIERS)
    fun accessibilityChosen() = compose.capture("settings-accessible", height = FullListHeight) {
        SettingsScreen(
            state = SettingsState(
                settings = PlayerSettings(
                    palette = BlockPaletteChoice.HighContrast,
                    reduceMotion = true,
                    largeNumbers = true,
                    haptics = HapticsSetting.Strong,
                    leftHanded = true,
                ),
                appVersion = Version,
            ),
            onAction = {},
        )
    }

    @Test
    fun resetWarning() = compose.capture("settings-reset-warn") {
        SettingsScreen(
            state = SettingsState(
                appVersion = Version,
                dialog = SettingsDialog.ResetProgressWarn,
            ),
            onAction = {},
        )
    }

    /** The typed-word step. Its button is disabled until the word matches. */
    @Test
    fun resetConfirmation() = compose.capture("settings-reset-confirm") {
        SettingsScreen(
            state = SettingsState(
                appVersion = Version,
                dialog = SettingsDialog.ResetProgressConfirm,
            ),
            onAction = {},
        )
    }

    @Test
    fun deleteWarning() = compose.capture("settings-delete-warn") {
        SettingsScreen(
            state = SettingsState(
                appVersion = Version,
                dialog = SettingsDialog.DeleteLocalDataWarn,
            ),
            onAction = {},
        )
    }

    @Test
    fun licences() = compose.capture("settings-licenses") {
        LicensesScreen(onBack = {})
    }
}

/**
 * [LocalInspectionMode] is forced on for the whole tree. Every looping animation
 * the design system draws checks it and holds a fixed value; without it Compose
 * never goes idle, the capture waits for an idle that never arrives, and the
 * build **hangs** rather than failing.
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

private const val Version = "1.0.0 (12)"

private const val GoldenDirectory = "screenshots"
private const val CaptureTag = "capture"

/** L45: the tightest frame the app ships to, not the roomiest. */
private val ShortPhoneWidth = 360.dp
private val ShortPhoneHeight = 640.dp

/** Tall enough for the whole list. Not a device size, and not pretending to be. */
private val FullListHeight = 2400.dp

/** Pinned rather than tracking `compileSdk`, as the other harnesses do. */
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"

/**
 * The one frame in this project taller than a phone, and it is a different job
 * from L45's: an inventory of a list longer than any device, not a judgement of
 * a layout. Every golden that judges layout stays at 360x640.
 */
private const val TALL_QUALIFIERS = "w360dp-h2400dp-xhdpi"
