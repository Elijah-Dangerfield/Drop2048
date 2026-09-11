package com.dangerfield.drop2048.features.paywall.impl.screenshot

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
import com.dangerfield.drop2048.features.paywall.impl.PaywallMessage
import com.dangerfield.drop2048.features.paywall.impl.PaywallScreen
import com.dangerfield.drop2048.features.paywall.impl.PaywallState
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
 * The Pro sheet in the three states it actually ships in.
 *
 * The set is chosen so each image is the only one that would move for its own
 * concern. The priced frame catches the layout and the four perks; the unpriced
 * one catches the button with no number in it, which is what a plane, a tunnel
 * and a fresh emulator all produce and which is the frame a hardcoded "$2.99"
 * would quietly delete; the owned one catches the absence of a buy button, which
 * is the only thing standing between a Pro player and being sold Pro again.
 *
 * [GraphicsMode.Mode.NATIVE] is required rather than preferred: Robolectric's
 * legacy graphics stack draws nothing at all, so every golden would be a
 * correctly-sized transparent rectangle and every comparison would pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class PaywallScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun priced() = compose.captureScreen("paywall-priced") {
        PaywallState(price = "$2.99")
    }

    @Test
    fun storeUnreachable() = compose.captureScreen("paywall-no-price") {
        PaywallState(price = null, message = PaywallMessage.Unavailable)
    }

    @Test
    fun alreadyPro() = compose.captureScreen("paywall-owned") {
        PaywallState(price = "$2.99", isPro = true)
    }
}

private fun ComposeContentTestRule.captureScreen(
    name: String,
    state: () -> PaywallState,
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
                    PaywallScreen(state = state(), onAction = {})
                }
            }
        }
    }
    onNodeWithTag(CaptureTag).captureRoboImage(File(GoldenDirectory, "$name.png"))
}

/**
 * Committed, and deliberately outside `build/`. A golden that lives in a build
 * directory disappears on a clean and cannot be reviewed in a pull request,
 * which removes both of the things it was for.
 */
private const val GoldenDirectory = "screenshots"

private const val CaptureTag = "capture"

/** The same small phone every other module captures on (L45). */
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
