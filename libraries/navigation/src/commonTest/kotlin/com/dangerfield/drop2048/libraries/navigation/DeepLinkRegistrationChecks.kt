package com.dangerfield.drop2048.libraries.navigation

import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavUri
import androidx.navigation.NavigatorProvider
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.navigation
import com.dangerfield.drop2048.libraries.navigation.floatingwindow.FloatingWindowNavigator
import kotlinx.serialization.Serializable
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Asserts that every destination builder in `NavGraphBuilderExt` puts the
 * `deepLinks` it was handed on **its own destination**.
 *
 * ### The bug these exist for
 *
 * `NavGraphBuilder.destination(...)` returns `Unit`. Written as
 * `destination(Builder(...)).apply { deepLinks.forEach { deepLink(it) } }` the
 * receiver inside the `apply` is that `Unit`, so `deepLink(...)` resolves
 * against the enclosing [androidx.navigation.NavGraphBuilder] instead. Every
 * link is added to the *graph*, and the destination gets none. [bottomSheet] was
 * written that way and [screen] / [dialog] were not, which is why all three are
 * asserted rather than the sheet alone: the two correct builders are the control
 * that proves the check can tell the difference.
 *
 * Nothing about the bug fails loudly. It compiles, the graph builds, the
 * destination resolves, and a deep link into a sheet simply never arrives.
 *
 * ### Why [androidx.navigation.NavDestination.hasDeepLink] and not a match
 *
 * `NavGraph.matchDeepLink` searches the graph's own links *and* its children's,
 * so it answers `true` under the bug too. Asking the destination is the only
 * public question whose answer differs.
 *
 * ### Why these are functions and not a test class
 *
 * They need a real URI, and [NavUri] is `android.net.Uri` on the Android target,
 * which is an unmocked stub in a plain JVM unit test. So the same three checks
 * are run twice from their own source sets: `DeepLinkRegistrationNativeTest`
 * (`iosTest`, the run that matters, since the typeMap and graph-build landmines
 * this module guards are Native-only) and `DeepLinkRegistrationRobolectricTest`
 * (`androidUnitTest`, where Robolectric supplies the real `Uri` and the check
 * lands in the fast `testDebugUnitTest` gate).
 */
internal fun assertScreenOwnsItsDeepLink() = assertOwnsItsDeepLink<DeepLinkScreenRoute>(ScreenPath)

internal fun assertDialogOwnsItsDeepLink() = assertOwnsItsDeepLink<DeepLinkDialogRoute>(DialogPath)

internal fun assertSheetOwnsItsDeepLink() = assertOwnsItsDeepLink<DeepLinkSheetRoute>(SheetPath)

private inline fun <reified T : Route> assertOwnsItsDeepLink(path: String) {
    val destination = assertNotNull(
        buildGraph().findNode(T::class),
        "${T::class.simpleName} did not resolve to a destination",
    )

    assertTrue(
        destination.hasDeepLink(NavUri(path)),
        "$path is not a deep link of ${T::class.simpleName}; " +
            "the builder added it to the graph instead of the destination",
    )
}

private fun buildGraph(): NavGraph = NavigatorProvider()
    .apply {
        addNavigator(NavGraphNavigator(this))
        addNavigator(ComposeNavigator())
        addNavigator(FloatingWindowNavigator())
    }
    .navigation(startDestination = DeepLinkScreenRoute()) {
        screen<DeepLinkScreenRoute>(
            deepLinks = listOf(routeDeepLink<DeepLinkScreenRoute>(basePath = ScreenPath)),
        ) { }

        dialog<DeepLinkDialogRoute>(
            deepLinks = listOf(routeDeepLink<DeepLinkDialogRoute>(basePath = DialogPath)),
        ) { _, _ -> }

        bottomSheet<DeepLinkSheetRoute>(
            deepLinks = listOf(routeDeepLink<DeepLinkSheetRoute>(basePath = SheetPath)),
        ) { _, _ -> }
    }

private const val ScreenPath = "drop2048://deeplink-test/screen"
private const val DialogPath = "drop2048://deeplink-test/dialog"
private const val SheetPath = "drop2048://deeplink-test/sheet"

@Serializable
private class DeepLinkScreenRoute : Route()

@Serializable
private class DeepLinkDialogRoute : Route()

@Serializable
private class DeepLinkSheetRoute : Route()
