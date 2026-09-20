package com.dangerfield.drop2048.libraries.ui.screenshot

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.system.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shared header, resting and lifted.
 *
 * **The regression these two exist for.** `TopBar`'s lift-on-scroll applied its
 * `graphicsLayer` *after* the bar's background and after its window-inset
 * padding, so the layer wrapped the title row rather than the bar. The elevation
 * landed on the title instead of under the header — reported by the owner as
 * "it's adding elevation to the text view in the header" — and it shipped on
 * every screen that scrolls: settings, licences, achievements, stats and the
 * three debug screens.
 *
 * **Two frames rather than one, and that is the point.** The resting frame alone
 * cannot fail: at scroll position zero the elevation animates to `0.dp` and the
 * bar draws identically whichever node the layer wraps. The pair is what pins
 * the difference, so the lifted frame is captured from a `ScrollState` that is
 * already scrolled.
 *
 * **The shadow does render here, and that was checked rather than hoped.** Under
 * [GraphicsMode.Mode.NATIVE] the elevation is drawn, and the two frames differ in
 * exactly one band: sixteen rows immediately below the bar's bottom edge, fading
 * out, with the bar itself byte-identical between them. Restoring the old
 * modifier order and re-running turns that into a hard rectangle *inside* the bar
 * around the title and nothing below it, and `verifyRoborazziDebug` fails — which
 * is the only evidence worth having that a golden guards anything.
 *
 * **What it still does not prove** is iOS. Robolectric implements the Android
 * framework, and a shadow is one of the things Skia does not draw the same way
 * on both.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class HeaderScreenshotTest : ScreenshotTest() {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun headerAtRest() = compose.capture("top-bar") {
        HeaderOverContent(scrollState = ScrollState(initial = 0))
    }

    /**
     * `ScrollState.canScrollBackward` is `value > 0`, so an already-scrolled
     * state lifts the bar without needing a gesture or a laid-out scroll
     * container.
     */
    @Test
    fun headerLiftedByScrolledContent() = compose.capture("top-bar-lifted") {
        HeaderOverContent(scrollState = ScrollState(initial = ScrolledPast))
    }

    /**
     * [Screen] rather than a hand-rolled column, because the lift is only ever
     * visible *against* the content and the stacking order is the thing under
     * test. Material's scaffold places the bar last, so the bar — and anything
     * it casts — draws over rows that have scrolled underneath it. A column with
     * the bar first paints the content on top of the lift and both frames come
     * out identical.
     */
    @Composable
    private fun HeaderOverContent(scrollState: ScrollState) {
        Box(modifier = Modifier.size(FrameWidth, FrameHeight)) {
            Screen(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopBar(
                        title = "Settings",
                        onNavigateBack = {},
                        scrollState = scrollState,
                    )
                },
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RowHeight)
                            .background(AppTheme.colors.surfacePrimary.color),
                    )
                }
            }
        }
    }
}

private val FrameWidth = 360.dp

private val FrameHeight = 180.dp

private val RowHeight = 72.dp

private const val ScrolledPast = 200
