package com.dangerfield.drop2048.libraries.ads

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The seam through which a build may contain a **house** ad network: one that
 * draws its own obviously-fake placeholder instead of asking an SDK for
 * inventory, so that every ad surface in the app can be looked at on a device
 * without a fill, a network, or three days of waiting out SPEC 12's install
 * grace.
 *
 * ### Why this is a seam and not a flag
 *
 * A house network can answer [AdShowResult.Rewarded], which is the one answer
 * L66 says a stand-in must never be able to give: `NotWiredAdNetwork` was safe
 * *because its type could not express a grant*. This one can, so the safety has
 * to come from somewhere else, and the somewhere else is the build.
 *
 * [network] is `null` in [NoHouseAds], and [NoHouseAds] is the only binding that
 * exists unless `:libraries:ads:fake` is on the compilation's classpath. On
 * Android that module is a `debugImplementation` of `:apps:compose`, so a
 * release APK does not contain the class, the binding, or the drawing code —
 * there is nothing to switch on. The check is in Gradle
 * (`verifyNoHouseAdsInRelease`), where a wrong answer fails the build rather
 * than shipping.
 *
 * iOS has no per-build-type source set to hang that on, so the same exclusion is
 * decided one level up: `:apps:compose` puts the fake module on the iOS compile
 * classpath only when Xcode's `CONFIGURATION` says Debug, and
 * `verifyNoHouseAdsInIosRelease` plus `verifyNoHouseAdsIn…ReleaseFramework`
 * check the graph and the linked `ComposeApp` respectively. Before that the
 * module was in every iOS binary and this was the platform where the guarantee
 * was a runtime check alone.
 *
 * [HouseAds.isSelected] still refuses to latch unless `BuildInfo.isDebug`, which
 * on Native is `Platform.isDebugBinary`, a property of the Xcode configuration
 * rather than of anything the app can set. It is the third layer now rather than
 * the only one, and it is what covers a build that somehow contains the class
 * anyway.
 *
 * ### [Surface] is a composable on the interface
 *
 * So that `App` can draw the house placeholder without naming the module that
 * implements it. The alternative — an overlay in `apps/compose` keyed on a state
 * flow — would put the drawing code in a source set that compiles into the
 * release binary, which is exactly the property this design is buying.
 */
interface HouseAds {

    /**
     * The house network, or null in a build that contains none.
     *
     * Read by the gates in place of the platform network when [isSelected]. Null
     * is not "switched off": it is "this binary has no such thing".
     */
    val network: AdNetwork?

    /** Whether the gates are currently routed to [network]. Always false when it is null. */
    val isSelected: StateFlow<Boolean>

    /** QA's switch between the house network and the platform one. */
    fun select(useHouseAds: Boolean)

    /**
     * What the house network answers **instead of drawing anything**, or null to
     * draw the placeholder and let the player decide.
     *
     * Here rather than buried in the fake because the surrounding logic is the
     * thing worth exercising and most of it only runs on the answers nobody can
     * produce by hand: a rewarded continue that is granted anyway on a `NoFill`,
     * an interstitial skipped on a `NotShown`, a `Failed` carrying a kind into
     * telemetry. A dial that can only ever be reached from a module the release
     * build does not contain is still a dial the debug menu has to be able to
     * turn, and the debug menu may only see this api.
     */
    val forcedOutcome: StateFlow<AdShowResult?>

    fun forceOutcome(result: AdShowResult?)

    /**
     * What the house network answers to [AdNetwork.isReady].
     *
     * The one SPEC 12.3 gate with no config key behind it: "preloaded, and
     * skipped silently if not ready". Without this there is no way to see
     * `not_ready` on a device at all, because the house network is always ready
     * by construction.
     */
    val reportsReady: StateFlow<Boolean>

    fun reportReady(ready: Boolean)

    /** Drawn at the root of the app, above everything. Nothing at all in a release build. */
    @Composable
    fun Surface()
}

/**
 * The answer in a build with no house network, which is every release build.
 *
 * There is deliberately no state here and no way to acquire any. A reviewer
 * asking "could this ship enabled" gets to read four lines and stop.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoHouseAds : HouseAds {
    override val network: AdNetwork? = null

    override val isSelected: StateFlow<Boolean> = MutableStateFlow(false)

    override fun select(useHouseAds: Boolean) = Unit

    override val forcedOutcome: StateFlow<AdShowResult?> = MutableStateFlow(null)

    override fun forceOutcome(result: AdShowResult?) = Unit

    override val reportsReady: StateFlow<Boolean> = MutableStateFlow(false)

    override fun reportReady(ready: Boolean) = Unit

    @Composable
    override fun Surface() = Unit
}

/**
 * The network the gates should actually talk to: the house one when a build has
 * it and QA has chosen it, and the platform one otherwise.
 *
 * An extension rather than a wrapper type so that the gates keep their existing
 * `AdNetwork` field and the routing is one readable expression at each point of
 * use. Resolved per call for the same reason `ads.enabled` is: a network cached
 * in a field is a switch that works next launch.
 */
fun HouseAds.networkOr(platform: AdNetwork): AdNetwork =
    network?.takeIf { isSelected.value } ?: platform
