package com.dangerfield.drop2048.features.paywall.impl

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dangerfield.drop2048.features.paywall.PaywallRoute
import com.dangerfield.drop2048.features.paywall.impl.screenshot.ROBOLECTRIC_QUALIFIERS
import com.dangerfield.drop2048.features.paywall.impl.screenshot.ROBOLECTRIC_SDK
import com.dangerfield.drop2048.libraries.navigation.floatingwindow.FloatingWindowNavigator
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.paywall_title
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The paywall reached the way the app reaches it: by navigating to
 * [PaywallRoute] on a graph the real [PaywallFeatureEntryPoint] built.
 *
 * Its conversion was verified by hand on a device until this landed. What this
 * sees that the goldens cannot is the registration itself: that the route
 * resolves to a destination on a real `NavHostController`, that the destination
 * is the floating-window kind rather than a pushed screen, and that the sheet's
 * content composes once the entry is on the floating-window back stack. A golden
 * of `PaywallSheetContent` passes with the registration deleted outright.
 *
 * Proven rather than assumed: replacing `bottomSheet<PaywallRoute>` with
 * `screen<PaywallRoute>` fails all three of these, and gating the `PaywallSheet`
 * call behind a condition that is never true fails
 * [navigatingToTheRouteOpensTheSheet] alone.
 *
 * ### What lives elsewhere
 *
 * The same route asserted on Kotlin/Native, where the typeMap crash lives, is
 * `PaywallGraphTest`. The scrim and the drag handle, which `ModalBottomSheet`
 * draws into a window of its own, are `PaywallSheetWindowScreenshotTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class PaywallNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theRouteIsRegisteredOnTheRealGraph() {
        val harness = compose.paywallGraph()

        val destination = harness.navController.graph.findNode(PaywallRoute::class)

        assertNotNull(destination, "PaywallRoute is not on the graph the entry point built")
        assertTrue(
            destination is FloatingWindowNavigator.Destination,
            "the paywall is a ${destination::class.simpleName}, not a floating window",
        )
    }

    @Test
    fun navigatingToTheRouteOpensTheSheet() {
        val harness = compose.paywallGraph()

        compose.onNodeWithText(UnderLabel).assertIsDisplayed()

        harness.navigateToPaywall()

        val current = harness.navController.currentBackStackEntry?.destination
        assertTrue(
            current is FloatingWindowNavigator.Destination,
            "landed on ${current?.route} instead of the paywall sheet",
        )
        compose.onNodeWithText(runBlocking { getString(Res.string.paywall_title) })
            .assertIsDisplayed()
    }

    /**
     * The screen the sheet was opened over is still composed underneath it, and
     * nothing has popped anything. This is the behavioural difference between
     * `bottomSheet<>` and `screen<>` that a type assertion only implies, and it is
     * the reason C14 moved the paywall.
     */
    @Test
    fun theScreenUnderneathSurvives() {
        val harness = compose.paywallGraph()

        harness.navigateToPaywall()

        compose.onNodeWithText(UnderLabel).assertIsDisplayed()
        assertEquals(0, harness.router.backs)
    }

    /**
     * The whole exit chain, which is four hops and no single place to read it:
     * the sheet's back handler calls `sheetState.dismiss()`, the state hides the
     * sheet, the hide completing fires `onDismissRequest`, and only then does the
     * entry point pop the entry. The split is deliberate, because popping on
     * `dismiss()` instead would cut the slide-out in half, and it is the kind of
     * wiring that survives a refactor looking correct while doing nothing.
     *
     * `backs` rather than a back-stack assertion because [RecordingRouter] does
     * not pop: what is under test is that the route's own pop was *requested*,
     * once, after the sheet had gone.
     */
    @Test
    fun dismissingTheSheetPopsTheRoute() {
        val harness = compose.paywallGraph()
        harness.navigateToPaywall()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        assertEquals(1, harness.router.backs)
    }
}
