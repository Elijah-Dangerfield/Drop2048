package com.dangerfield.drop2048.libraries.ui.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.AppThemeProvider
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File

/**
 * The one way anything in this module gets a golden image.
 *
 * **Why this exists.** Every visual regression so far has been found by building
 * the app, launching it on a device and looking at it — a 90-second loop that
 * only runs when somebody thinks to run it. C3 shipped three device bugs it found
 * that way, and measured that two of the three were visible in a single static
 * frame. This is the harness that would have caught those two.
 *
 * **What it actually is.** Roborazzi driving Compose under Robolectric: a real
 * composition, laid out and drawn, on the JVM, with no emulator and no device.
 *
 * **What it does not prove, and this matters.** Robolectric implements the
 * *Android* framework, so this captures the Android target and nothing else. A
 * component that renders correctly here can still be wrong on iOS: the text
 * shaping is Skia's on both, but font fallback, density rounding and the
 * platform's own blur are not. Read a green run as "the composition and its
 * colours are unchanged", never as "it looks right everywhere".
 *
 * **The two commands, and the trap between them.**
 *
 * - `./gradlew :libraries:ui:recordRoborazziDebug` re-records every golden under
 *   `libraries/ui/screenshots/`. Run it when a change to the design is intended,
 *   and read the diff before committing it.
 * - `./gradlew :libraries:ui:verifyRoborazziDebug` compares without writing. This
 *   is the one that gates.
 *
 * **Plain `testDebugUnitTest` does not compare anything.** With no Roborazzi flag
 * set, `captureRoboImage` is a deliberate no-op: these tests run, pass, and never
 * look at a pixel. That was measured rather than assumed — a golden was replaced
 * with a different image and `testDebugUnitTest` stayed green. The build file
 * hangs `verifyRoborazziDebug` off `check` for exactly that reason. If you are
 * running the standard verification command by hand, add the verify task to it.
 */
abstract class ScreenshotTest {

    /**
     * Capture [content] as `<name>.png`.
     *
     * [LocalInspectionMode] is forced on for the whole tree, and it is the single
     * most important line in this file. Every looping animation in this module —
     * the ghost pulse, the wordmark rock, `Modifier.pulsate` — checks it and
     * holds a fixed value. Without it Compose never goes idle, the capture waits
     * for an idle that never arrives, and the build **hangs** rather than
     * failing: no error, no timeout, and no clue which component did it.
     *
     * The background is the game's own backdrop rather than white. A chunky dark
     * design judged against white is judged against the wrong thing, and a golden
     * of a tile on white is a golden nobody looks at twice.
     *
     * The capture is the tagged wrapper rather than the root, so an image is the
     * size of the component and nothing else. Capturing the root pads every
     * golden out to the emulated window and makes the interesting part a tenth of
     * a mostly-empty PNG, which is how a reviewer stops opening them.
     */
    protected fun ComposeContentTestRule.capture(
        name: String,
        palette: BlockPaletteChoice = BlockPaletteChoice.Default,
        reduceMotion: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppThemeProvider(palette = palette, reduceMotion = reduceMotion) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize()
                            .testTag(CaptureTag)
                            .background(GameColors.BackdropMid)
                            .padding(CapturePadding),
                        content = { content() },
                    )
                }
            }
        }
        onNodeWithTag(CaptureTag).captureRoboImage(File(GoldenDirectory, "$name.png"))
    }
}

/**
 * Committed, and deliberately outside `build/`. A golden that lives in a build
 * directory disappears on a clean and cannot be reviewed in a pull request, which
 * removes both of the things it was for.
 */
private const val GoldenDirectory = "screenshots"

private const val CaptureTag = "capture"

private val CapturePadding = 16.dp
