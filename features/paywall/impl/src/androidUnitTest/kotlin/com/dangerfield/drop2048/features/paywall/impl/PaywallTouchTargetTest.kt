package com.dangerfield.drop2048.features.paywall.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.paywall.impl.screenshot.ROBOLECTRIC_QUALIFIERS
import com.dangerfield.drop2048.features.paywall.impl.screenshot.ROBOLECTRIC_SDK
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `Restore purchase` is `GameQuietButton`'s second home, and the one nobody was
 * thinking about when it was written.
 *
 * It inherited a 13sp label and 4dp of padding as its whole touch target. The
 * component now grows that toward 48dp without taking any layout space, and this
 * sheet is the tighter of its two homes: `Buy` sits 12dp above against 11.25dp
 * of growth, which is under a dp of daylight. That is close enough to deserve a
 * test rather than an assurance.
 *
 * The quiet button is found by its [Role.Button], which on this sheet only it
 * declares. `GamePrimaryButton` goes through `DeepSurface`, whose `clickable`
 * passes no role. If that changes, this stops finding one node and fails loudly
 * rather than quietly measuring the wrong thing.
 *
 * [noControlReachesIntoAnother] deliberately asks about every control on the
 * sheet rather than naming these two, so whatever is added next is held to the
 * same rule without anyone remembering to come back here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class PaywallTouchTargetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun restoreMeetsTheFloorInBothAxes() {
        compose.sheet()

        val quiet = compose
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .fetchSemanticsNodes()
        assertEquals(1, quiet.size, "expected exactly one quiet button on the sheet")

        val bounds = quiet.single().boundsInRoot
        with(compose.density) {
            assertTrue(
                bounds.width.toDp() >= Floor && bounds.height.toDp() >= Floor,
                "restore is ${bounds.width.toDp()} x ${bounds.height.toDp()}, floor is $Floor",
            )
        }
    }

    @Test
    fun noControlReachesIntoAnother() {
        compose.sheet()

        val controls = compose.onAllNodes(hasClickAction())
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }

        assertTrue(controls.size > 1, "found ${controls.size} controls, so this proves nothing")

        controls.forEachIndexed { index, rect ->
            controls.drop(index + 1).forEach { other ->
                assertFalse(rect.overlaps(other), "$rect overlaps $other")
            }
        }
    }
}

private fun ComposeContentTestRule.sheet() {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(Modifier.width(SheetWidth).wrapContentHeight()) {
                    PaywallSheetContent(state = PaywallState(price = "$2.99"), onAction = {})
                }
            }
        }
    }
}

private val SheetWidth = 360.dp
private val Floor = 48.dp
