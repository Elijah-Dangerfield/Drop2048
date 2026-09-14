package com.dangerfield.drop2048.features.paywall.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.paywall.PaywallRoute
import com.dangerfield.drop2048.libraries.billing.PaywallCoordinator
import com.dangerfield.drop2048.libraries.billing.PaywallRequest
import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.bottomSheet
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class PaywallFeatureEntryPoint(
    private val paywallViewModelFactory: () -> PaywallViewModel,
) : FeatureEntryPoint {

    /**
     * A `bottomSheet<>` rather than a `screen<>` (C14). The paywall is a
     * transient overlay over somewhere the player expects to come straight back
     * to, which is exactly what `AGENTS.md` reserves the sheet builder for: the
     * backstack stays one entry deep and the screen underneath stays visible
     * under the scrim.
     *
     * The two halves of leaving are deliberately separate. The view model's
     * `Leave` event asks the *sheet* to close — it is emitted when the player
     * taps away or the purchase lands — and `onDismissRequest` fires once it has
     * finished sliding out, which is the only safe moment to pop the entry. Doing
     * both in one place cuts the exit animation in half.
     */
    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        bottomSheet<PaywallRoute> { _, sheetState ->
            val viewModel: PaywallViewModel = viewModel { paywallViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    PaywallEvent.Leave -> sheetState.dismiss()
                }
            }

            PaywallSheet(
                state = state,
                onAction = viewModel::takeAction,
                onDismissRequest = { router.goBack() },
                sheetState = sheetState,
            )
        }
    }
}

/**
 * Turns a [PaywallRequest] into a navigation.
 *
 * This is the only place in the app that knows both that a paywall exists and
 * where it lives. The callers ask a library for one from places that must not
 * know a route exists; this feature owns the route and does the
 * asking-to-showing translation.
 *
 * [AutoInit] is load-bearing rather than a performance choice: the coordinator's
 * bus has no replay, so a request made before this collector attaches is simply
 * lost. Boot-warming it means the subscription exists before anything can ask.
 *
 * `launchSingleTop` because a double tap on the upsell card must collapse into
 * one screen rather than two copies of it on the back stack.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class PaywallNavigator(
    coordinator: PaywallCoordinator,
    router: Router,
    appScope: AppCoroutineScope,
) : AutoInit {

    init {
        appScope.launch {
            coordinator.requests.collect { request ->
                when (request) {
                    is PaywallRequest.Offer -> router.navigate(
                        PaywallRoute(),
                        NavigationOptions(launchSingleTop = true),
                    )
                }
            }
        }
    }
}
