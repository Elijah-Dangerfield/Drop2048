package com.dangerfield.drop2048.features.paywall.impl.screenshot

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dangerfield.drop2048.features.paywall.impl.paywallGraph
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The sheet *as a sheet*: scrim, drag handle, and how much of the screen it
 * leaves showing.
 *
 * ### The gap this closes
 *
 * `PaywallScreenshotTest` captures `PaywallSheetContent`, which is every pixel of
 * the sheet **except the two things that make it one**. That was not laziness.
 * `ModalBottomSheet` on Android draws into a `Dialog` window of its own, so the
 * sheet is simply not in the tree that a node-scoped `captureRoboImage` walks.
 * Measured rather than assumed: with the sheet open there are two Compose roots
 * and one shown `Dialog`, and the drag handle and scrim are in the second root.
 *
 * `captureScreenRoboImage` is the way out. It composites every Robolectric window
 * root rather than one node's layer, so a golden taken through it contains the
 * screen underneath, the scrim over it and the sheet on top: the frame a player
 * actually sees.
 *
 * ### Why it goes through the nav graph rather than calling `PaywallSheet`
 *
 * Because the window is the subject. A sheet composed directly is still drawn in
 * its own window, but it is drawn over nothing, and a scrim over an empty
 * background is a golden that cannot tell a working scrim from a missing one.
 * Opening the real destination over a real screen means the golden moves if the
 * scrim colour changes, if the scrim stops being drawn, if the drag handle
 * disappears, or if the sheet starts covering the whole screen.
 *
 * ### The frame, not the states
 *
 * One golden, priced. The three product states are already pinned by
 * `PaywallScreenshotTest` at content level, and repeating them here would be
 * three more images that move for the same reasons. The lower layer wins.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class PaywallSheetWindowScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openOverAScreen() {
        compose.paywallGraph().navigateToPaywall()

        captureScreenRoboImage(File(GoldenDirectory, "paywall-sheet-window.png"))
    }
}

private const val GoldenDirectory = "screenshots"
