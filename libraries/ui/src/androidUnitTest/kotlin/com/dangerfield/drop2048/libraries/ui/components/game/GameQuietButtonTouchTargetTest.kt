package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.screenshot.ROBOLECTRIC_QUALIFIERS
import com.dangerfield.drop2048.libraries.ui.screenshot.ROBOLECTRIC_SDK
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [GameQuietButton] is a button to a screen reader and 48dp to a finger, and it
 * still takes up exactly its label.
 *
 * ### What was wrong, and what was only assumed to be wrong
 *
 * The component was a bare `pointerInput` with no role and no click action. The
 * role was plainly missing. The touch target was the part worth measuring
 * rather than assuming, and measuring it said two different things at once.
 *
 * Alone in a `Box`, the pointer target was already 48 x 48dp:
 * `ViewConfiguration.minimumTouchTargetSize` inflates the touch bounds of *any*
 * pointer input node, and that is compose-ui's behaviour rather than Material's.
 * On the paused overlay, where a full-board dismiss scrim claims every pixel
 * outright, the same component measured 58.25 x 25.25dp for `Settings` and
 * 33.75 x 25.25dp for `Quit`, its drawn box to within the probe's resolution.
 * An inflated bound is only ever a near-miss, and any sibling with a real hit
 * takes the pointer off it.
 *
 * [theTouchAreaSurvivesASiblingThatClaimsEveryPixel] is the test that tells
 * those two worlds apart, and the only one of these that would have failed
 * against the old component. A version of it without the scrim passes against
 * code with no touch target of its own at all.
 *
 * ### Why the drawn size is asserted too
 *
 * The obvious fix is to put a 48dp box around the label, which is what
 * `minimumInteractiveComponentSize` does, and it would move the copy on every
 * overlay in the app. [theExpansionCostsNoLayout] pins that the parent measures
 * the same thing whether the touch area grows or not, so nobody makes that fix
 * by accident later.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class GameQuietButtonTouchTargetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun itIsAButtonToAScreenReader() {
        compose.host {
            GameQuietButton(text = Label, onClick = {})
        }

        compose.onNodeWithText(Label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
    }

    @Test
    fun theClickActionReachesOnClick() {
        var clicks = 0
        compose.host {
            GameQuietButton(text = Label, onClick = { clicks++ })
        }

        compose.onNodeWithText(Label).performClick()

        assertEquals(1, clicks, "activating the semantics action did not call onClick")
    }

    /**
     * The narrowest label in the design is 48dp of target in both axes, even
     * with a sibling underneath it that would otherwise take every near-miss.
     */
    @Test
    fun theTouchAreaSurvivesASiblingThatClaimsEveryPixel() {
        var clicks = 0
        compose.host {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = null,
                indication = null,
                onClick = {},
            ))
            GameQuietButton(text = ShortLabel, onClick = { clicks++ })
        }

        val target = compose.hitExtent(ShortLabel) { clicks }

        assertTrue(
            target.width + ProbeTolerance >= Floor && target.height + ProbeTolerance >= Floor,
            "touch target measured ${target.width} x ${target.height}, floor is $Floor",
        )
    }

    @Test
    fun theExpansionCostsNoLayout() {
        var grown = IntSize.Zero
        var ungrown = IntSize.Zero
        compose.host {
            Box(Modifier.onSizeChanged { grown = it }) {
                GameQuietButton(text = Label, onClick = {})
            }
            Box(Modifier.onSizeChanged { ungrown = it }) {
                GameQuietButton(text = Label, onClick = {}, maxTouchExpansion = 0.dp)
            }
        }

        assertEquals(ungrown, grown, "the grown touch area leaked into the layout")
        assertTrue(
            with(compose.density) { grown.height.toDp() } < Floor,
            "the drawn box is already $Floor tall, so this test is not proving anything",
        )
    }
}

/**
 * Walk outward from the centre of [label] until a tap stops reaching it, and
 * report how wide and tall the region that does reach it is.
 *
 * Probing is the only honest way to ask. No rectangle on the node answers it:
 * Compose expands a small node's touch bounds during hit testing and then gives
 * them up to any sibling that claims the pixel first, and neither half of that
 * shows up in the tree.
 */
private fun ComposeContentTestRule.hitExtent(label: String, hits: () -> Int): DpSize {
    val centre = onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center

    fun tapped(x: Float, y: Float): Boolean {
        val before = hits()
        onRoot().performTouchInput { click(Offset(x, y)) }
        waitForIdle()
        return hits() > before
    }

    fun edge(dx: Float, dy: Float): Float {
        var lo = 0f
        var hi = 1f
        while (hi < ProbeLimit && tapped(centre.x + dx * hi, centre.y + dy * hi)) hi *= 2f
        while (hi - lo > ProbeStep) {
            val mid = (lo + hi) / 2f
            if (tapped(centre.x + dx * mid, centre.y + dy * mid)) lo = mid else hi = mid
        }
        return lo
    }

    val width = edge(-1f, 0f) + edge(1f, 0f)
    val height = edge(0f, -1f) + edge(0f, 1f)
    return with(density) { DpSize(width.toDp(), height.toDp()) }
}

/**
 * [LocalInspectionMode] is forced on for the reason the screenshot harness does
 * it: the looping animations in this module never let Compose go idle, and the
 * probe waits for idle after every tap.
 */
private fun ComposeContentTestRule.host(content: @Composable () -> Unit) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
            }
        }
    }
}

private const val Label = "Settings"
private const val ShortLabel = "Hi"
private val Floor: Dp = 48.dp

/** Half a pixel, so the probe lands within a quarter of a dp at xhdpi. */
private const val ProbeStep = 0.5f
private const val ProbeLimit = 400f

/**
 * The probe reports the last point that hit, which sits up to [ProbeStep] inside
 * the real edge. Without this the assertion would be about the search rather
 * than about the target.
 */
private val ProbeTolerance: Dp = 1.dp
