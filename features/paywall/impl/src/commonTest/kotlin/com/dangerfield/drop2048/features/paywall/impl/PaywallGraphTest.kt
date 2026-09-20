package com.dangerfield.drop2048.features.paywall.impl

import androidx.navigation.FloatingWindow
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.navigation
import com.dangerfield.drop2048.features.paywall.PaywallRoute
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.floatingwindow.FloatingWindowNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The paywall's registration, built as a real nav graph, on every target this
 * module compiles for, **including `iosSimulatorArm64`**.
 *
 * ### Why this is a `commonTest` and not another Robolectric test
 *
 * The failure it exists for is Native-only. Type-safe navigation turns each
 * serialized `val` of a [Route] into a nav argument, and a `NavDestination` is
 * assembled the moment `bottomSheet<PaywallRoute>` runs: the builder's
 * constructor calls `generateRoutePattern`, which needs a `NavType` for every
 * argument. On the JVM `enter` / `exit` / `popExit` resolve through a reflective
 * enum fallback whether or not `baseRouteTypeMap` is merged in. On Native that
 * fallback returns `UNKNOWN` and graph-build throws `could not find any NavType
 * for argument …`, frequently naming a *different* argument than the one that is
 * actually missing. Every JVM harness in this repo is structurally blind to it,
 * which is why `AGENTS.md` lists it as a production crash.
 *
 * Verified rather than reasoned about: deleting `+ baseRouteTypeMap` from the
 * `bottomSheet` builder leaves `:features:paywall:impl:testDebugUnitTest` green
 * and turns `:features:paywall:impl:iosSimulatorArm64Test` red on exactly that
 * message.
 *
 * ### What it asserts beyond "it did not throw"
 *
 * That the destination is a [FloatingWindow] one. This is the only automated
 * statement anywhere that the paywall is a *sheet* rather than a pushed screen.
 * A golden image cannot say it, because `ModalBottomSheet` draws into a window of
 * its own and the goldens capture the content composable. Swapping
 * `bottomSheet<PaywallRoute>` for `screen<PaywallRoute>` would leave every pixel
 * test in this module passing and fail here.
 *
 * ### Not covered here
 *
 * Anything that needs a composition: that the sheet draws, that tapping outside
 * dismisses it, that `sheetState.dismiss()` pops the entry. Those are
 * `PaywallNavigationTest`, which runs on Android only.
 */
class PaywallGraphTest {

    @Test
    fun theRouteResolvesToADestination() {
        val destination = buildGraph().findNode(PaywallRoute::class)

        assertNotNull(destination, "PaywallRoute did not resolve to a destination")
    }

    /**
     * `bottomSheet<>` and `screen<>` differ only in which navigator they register
     * against, and nothing else in the suite can tell the two apart.
     *
     * Asserted as a type rather than as `navigatorName`, which is not the same
     * string on both targets: JVM reads `@Navigator.Name` reflectively and gets
     * `floatingwindow`, Native has no reflection and falls back to a class name.
     */
    @Test
    fun theDestinationIsASheetAndNotAScreen() {
        val destination = assertNotNull(buildGraph().findNode(PaywallRoute::class))

        assertTrue(
            destination is FloatingWindowNavigator.Destination,
            "the paywall is registered as ${destination::class.simpleName}, not a floating window",
        )
        assertTrue(destination is FloatingWindow)
    }

    /**
     * The three [Route] base-class arguments, resolved. Their absence is the
     * shape the Native crash takes, so naming them here makes a failure legible
     * rather than leaving the reader with a route pattern to decode.
     */
    @Test
    fun theBaseRouteArgumentsResolveTheirNavTypes() {
        val destination = assertNotNull(buildGraph().findNode(PaywallRoute::class))

        assertEquals(
            setOf("enter", "exit", "popExit"),
            destination.arguments.keys,
        )
    }

    /**
     * The real entry point, through the real builder.
     *
     * The view model factory throws because nothing may call it: a destination is
     * a description of what to compose, and building the graph must not construct
     * the screen behind it. If this ever starts failing with "the graph must not
     * build a view model", the registration has grown eager work and every
     * paywall route on the back stack now costs a billing connection.
     */
    private fun buildGraph() = NavigatorProvider()
        .apply {
            addNavigator(NavGraphNavigator(this))
            addNavigator(FloatingWindowNavigator())
        }
        .navigation(startDestination = PaywallRoute()) {
            val entryPoint = PaywallFeatureEntryPoint(
                paywallViewModelFactory = { error("the graph must not build a view model") },
            )
            with(entryPoint) { buildNavGraph(RecordingRouter()) }
        }
}

/**
 * A [Router] that records rather than navigates, shared with the Android-only
 * `PaywallNavigationTest`, where `backs` is the whole point: it is how the sheet
 * having finished sliding out and popped its own entry becomes an assertion.
 */
internal class RecordingRouter : Router {
    var backs: Int = 0
        private set

    override fun navigate(route: Route, options: NavigationOptions) = Unit

    override fun goBack() {
        backs++
    }

    override fun popBackTo(route: Route, inclusive: Boolean) = Unit

    override fun openWebLink(url: String) = Unit
}
