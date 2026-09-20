package com.dangerfield.drop2048.features.paywall.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.dangerfield.drop2048.features.paywall.PaywallRoute
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.ProductIds
import com.dangerfield.drop2048.libraries.billing.PurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.billing.StoreBilling
import com.dangerfield.drop2048.libraries.billing.StoreOwnership
import com.dangerfield.drop2048.libraries.billing.StorePurchaseOutcome
import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.baseRouteTypeMap
import com.dangerfield.drop2048.libraries.navigation.floatingwindow.FloatingWindowHost
import com.dangerfield.drop2048.libraries.navigation.floatingwindow.FloatingWindowNavigator
import com.dangerfield.drop2048.libraries.navigation.screen
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * The repository's first navigation-level test harness, and deliberately the
 * smallest one that is still the real wiring.
 *
 * Every other UI test here calls a screen's content composable directly, which
 * means `bottomSheet<>` registration, [FloatingWindowNavigator] and
 * [FloatingWindowHost] had no coverage at all before the paywall, the app's
 * first sheet destination, gave them a reason to. What runs here is production
 * code apart from the two billing seams and the [RecordingRouter]: the graph
 * comes from [PaywallFeatureEntryPoint], the controller carries the same
 * floating-window navigator `App.kt` installs, and the sheet is drawn by the same
 * host.
 *
 * It is a harness for this feature, not a framework. Copy and adapt it for the
 * second sheet; factor it out at the third.
 */
internal class PaywallHarness(
    val navController: NavHostController,
    val router: RecordingRouter,
    private val compose: AndroidComposeTestRule<*, *>,
) {
    fun navigateToPaywall() {
        compose.runOnUiThread { navController.navigate(PaywallRoute()) }
        compose.waitForIdle()
    }
}

/**
 * [LocalInspectionMode] is on for the reason every screenshot test in the repo
 * turns it on: the game's looping animations never let Compose go idle, and a
 * capture that waits for an idle that never arrives hangs the build with no error
 * rather than failing it. It also makes [FloatingWindowHost] treat every
 * back-stack entry as visible, which is what lets the sheet compose without a
 * device's window transitions.
 *
 * The view model is built over hand-rolled doubles rather than stubbed away: the
 * destination composable resolves one through `viewModel {}`, so a factory that
 * threw would fail at the exact moment this is trying to reach.
 */
internal fun AndroidComposeTestRule<*, *>.paywallGraph(): PaywallHarness {
    val router = RecordingRouter()
    lateinit var navController: NavHostController
    setContent {
        val floatingWindowNavigator = remember { FloatingWindowNavigator() }
        navController = rememberNavController(floatingWindowNavigator)
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = UnderRoute(),
                        typeMap = baseRouteTypeMap,
                    ) {
                        screen<UnderRoute> { BasicText(UnderLabel) }
                        with(paywallEntryPoint()) { buildNavGraph(router) }
                    }
                    FloatingWindowHost(floatingWindowNavigator)
                }
            }
        }
    }
    waitForIdle()
    return PaywallHarness(navController, router, this)
}

private fun paywallEntryPoint() = PaywallFeatureEntryPoint(
    paywallViewModelFactory = {
        PaywallViewModel(entitlements = FreeEntitlements(), store = SignedInStore())
    },
)

/** Stands in for whatever the player was doing when the upsell came up. */
@Serializable
internal class UnderRoute : Route()

internal const val UnderLabel = "under the sheet"

private class FreeEntitlements : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)

    override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Cancelled

    override suspend fun restore(): RestoreOutcome = RestoreOutcome.NothingToRestore
}

private class SignedInStore : StoreBilling {
    override suspend fun ownership(productId: String): StoreOwnership = StoreOwnership.NotOwned

    override suspend fun purchase(productId: String): StorePurchaseOutcome =
        error("the sheet purchases through Entitlements")

    override suspend fun restore(productId: String): StoreOwnership = StoreOwnership.NotOwned

    override suspend fun priceLabel(productId: String): String? =
        "$2.99".takeIf { productId == ProductIds.pro }
}
