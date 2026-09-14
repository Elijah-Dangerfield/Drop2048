package com.dangerfield.drop2048.libraries.devfeedback.tester

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
import com.dangerfield.drop2048.libraries.devfeedback.DefaultFabPlacement
import com.dangerfield.drop2048.libraries.devfeedback.FabPlacement
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
 * The floating button and the panel it opens.
 *
 * Two things a picture catches here that review does not (L39a). The button is a
 * ring drawn in the background colour over an accent fill, which exists so it
 * does not disappear into an accent-coloured tile — a change to either token
 * makes it vanish into the app and nothing else notices. And the panel is the
 * only screen in the app that is drawn *over* a live board, so it has to be
 * fully opaque edge to edge; a `Surface` that stopped painting its background
 * would let the board show through and nothing would fail.
 *
 * The button is captured in two placements because the whole point of it is that
 * it moves, and the corner it is dragged into is where its clamp and its
 * `safeDrawingPadding` are load-bearing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class DevFeedbackScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /** Where a fresh install puts it: right edge, clear of both bars. */
    @Test
    fun fabAtItsDefaultPlacement() = compose.capture("dev-feedback-fab") {
        DevFeedbackFab(placement = DefaultFabPlacement, onSettled = {}, onClick = {})
    }

    /**
     * Dragged as far as it goes. `1f` is flush against the edge of the travel,
     * not off it, and the golden is what says the clamp still means that.
     */
    @Test
    fun fabDraggedIntoTheCorner() = compose.capture("dev-feedback-fab-corner") {
        DevFeedbackFab(placement = FabPlacement(x = 1f, y = 1f), onSettled = {}, onClick = {})
    }

    /** The form, with the screenshot row absent — there is no frame to attach. */
    @Test
    fun panelWaitingForADirective() = compose.capture("dev-feedback-panel") {
        DevFeedbackPanel(
            state = DevFeedbackState(
                isOpen = true,
                message = "The chain callout should hold until the board settles, not with it.",
            ),
            onAction = {},
        )
    }

    /** After filing. The one screen in the app whose only job is to say "yes". */
    @Test
    fun panelAfterFiling() = compose.capture("dev-feedback-panel-sent") {
        DevFeedbackPanel(state = DevFeedbackState(isOpen = true, sent = true), onAction = {})
    }
}

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

internal const val ROBOLECTRIC_SDK = 34
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"
