package com.dangerfield.drop2048

import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.profile.BugReportRoute
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.ShakeDialogRoute
import com.dangerfield.drop2048.libraries.navigation.dialog
import com.dangerfield.drop2048.libraries.navigation.toRouteOrNull
import com.dangerfield.drop2048.libraries.networking.NetworkInspector
import com.dangerfield.drop2048.libraries.ui.components.dialog.ShakeDialog
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class ShakeDialogEntryPoint(
    private val networkInspector: NetworkInspector,
    private val shakeHandler: ShakeHandler,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        dialog<ShakeDialogRoute> { backStackEntry, dialogState ->
            val route = backStackEntry.toRouteOrNull<ShakeDialogRoute>()

            // The handler's "a dialog is already up" flag is owned here, by the
            // destination, rather than by the act of asking to navigate.
            //
            // Set on enter, because a navigation the router drops (it refuses
            // to move while a blocking error screen is up) must not suppress
            // the gesture for a dialog that never appeared.
            //
            // Cleared on dispose, because there are five ways out of here and
            // only three are callbacks: the two CTAs, dismiss, the system back
            // gesture and a tap on the scrim. Wiring the three would leave the
            // other two latched, which is how this broke in the first place —
            // nothing called `onDialogDismissed` at all and the shake worked
            // exactly once per process. Leaving composition is the one event
            // every route out shares.
            DisposableEffect(Unit) {
                shakeHandler.onDialogShown()
                onDispose { shakeHandler.onDialogDismissed() }
            }

            ShakeDialog(
                state = dialogState,
                headline = route?.headline ?: "I felt that.",
                subtext = route?.subtext,
                onDismiss = { router.goBack() },
                onReportBug = {
                    router.goBack()
                    router.navigate(
                        BugReportRoute(contextMessage = "Triggered via shake")
                    )
                },
                // Debug-only: reuse the shake gesture to also open the
                // WiretapKMP network inspector. Hidden in release (and the
                // inspector itself is the noop there).
                onOpenNetworkInspector = if (BuildInfo.isDebug) {
                    {
                        router.goBack()
                        networkInspector.open()
                    }
                } else {
                    null
                },
            )
        }
    }
}
