package com.dangerfield.drop2048.libraries.ads.fake

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
import com.dangerfield.drop2048.libraries.ads.AdFormat
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
 * What a house ad looks like, pinned.
 *
 * A placeholder has no design to be checked against, so these goldens are here
 * for the other thing goldens are for on this project: the surface's whole job
 * is to be **unmistakable**, and the two ways it can quietly stop being that —
 * the "NOT A REAL AD" banner losing its prominence, and the two formats drifting
 * into looking the same — are both invisible in review and obvious in a picture.
 * The second one matters most: the interstitial is the ad the player did not
 * ask for, and a tester who cannot tell which format they are looking at cannot
 * report the bug this whole chunk exists to make findable.
 *
 * Captured against [HouseAdSurface] rather than `HouseAdHost` on purpose. The
 * host owns a one-second `delay` loop, and Compose never goes idle while one is
 * pending — a capture of it would hang rather than fail. The surface is a pure
 * function of the countdown, so the frames below are the real ones.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class HouseAdScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rewarded() = compose.capture("house-ad-rewarded") {
        HouseAdSurface(
            format = AdFormat.Rewarded,
            secondsRemaining = HouseAdNetwork.RewardedSeconds,
            onClose = {},
            onSkipToEnd = {},
        )
    }

    /** One second left: the frame where closing early still withholds the reward. */
    @Test
    fun rewardedAboutToPay() = compose.capture("house-ad-rewarded-ending") {
        HouseAdSurface(
            format = AdFormat.Rewarded,
            secondsRemaining = 1,
            onClose = {},
            onSkipToEnd = {},
        )
    }

    @Test
    fun interstitial() = compose.capture("house-ad-interstitial") {
        HouseAdSurface(
            format = AdFormat.Interstitial,
            secondsRemaining = HouseAdNetwork.InterstitialSeconds,
            onClose = {},
            onSkipToEnd = {},
        )
    }
}

private fun ComposeContentTestRule.capture(name: String, content: @Composable () -> Unit) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(
                    modifier = Modifier
                        .width(ShortPhoneWidth)
                        .height(ShortPhoneHeight)
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
private val ShortPhoneWidth = androidx.compose.ui.unit.Dp(360f)
private val ShortPhoneHeight = androidx.compose.ui.unit.Dp(640f)

private const val ROBOLECTRIC_SDK = 34
private const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"
