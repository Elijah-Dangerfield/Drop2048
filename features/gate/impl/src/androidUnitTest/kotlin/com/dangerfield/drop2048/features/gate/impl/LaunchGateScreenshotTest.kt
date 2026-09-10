package com.dangerfield.drop2048.features.gate.impl

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
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.gate.BlockingGate
import com.dangerfield.drop2048.features.gate.NoticeGate
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
 * The three walls and the three banners.
 *
 * These are the screens with the least testing in production and the worst
 * consequences when they are wrong: nobody sees a force-update wall until the day
 * an operator raises the floor, and by then it is on every install at once. A
 * picture is the only cheap way to know the maintenance screen has words on it
 * and the legal one has a button.
 *
 * The banners are captured over a stand-in rather than over the real nav host —
 * what is under them is not the thing being checked, and composing the app inside
 * a gate test would make the gate depend on every screen it wraps.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class LaunchGateScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun forceUpdate() = compose.capture("gate-force-update") {
        LaunchGateScreen(gate = BlockingGate.ForceUpdate, onAction = {})
    }

    /** The operator's own words. A blocking gate with none of them raises no gate. */
    @Test
    fun maintenance() = compose.capture("gate-maintenance") {
        LaunchGateScreen(
            gate = BlockingGate.Maintenance(
                "We are moving some furniture around. Back within the hour.",
            ),
            onAction = {},
        )
    }

    @Test
    fun reacceptLegal() = compose.capture("gate-legal") {
        LaunchGateScreen(
            gate = BlockingGate.ReacceptLegal(termsVersion = 2, privacyVersion = 2),
            onAction = {},
        )
    }

    @Test
    fun softUpdateBanner() = compose.capture("notice-soft-update") {
        NoticePreview(NoticeGate.SoftUpdate(versionCode = 14))
    }

    @Test
    fun legalBanner() = compose.capture("notice-legal") {
        NoticePreview(NoticeGate.LegalUpdated(termsVersion = 2, privacyVersion = 2))
    }

    @Test
    fun maintenanceBanner() = compose.capture("notice-maintenance") {
        NoticePreview(NoticeGate.Maintenance("Scores are slow to post right now."))
    }
}

/**
 * A notice with nothing behind it.
 *
 * `LaunchGateHost` needs a live ViewModel, and what the banner sits on top of is
 * not what these images are for, so the banner is composed on its own.
 */
@Composable
private fun NoticePreview(notice: NoticeGate) {
    LaunchNotice(notice = notice, onAction = {})
}

private fun ComposeContentTestRule.capture(
    name: String,
    content: @Composable () -> Unit,
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

/** Pinned rather than tracking `compileSdk`, as the other harnesses do. */
internal const val ROBOLECTRIC_SDK = 34
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"
