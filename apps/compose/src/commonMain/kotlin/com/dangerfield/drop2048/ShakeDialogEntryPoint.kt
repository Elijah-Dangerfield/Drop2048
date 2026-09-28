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

            // Releases the handler's "a dialog is already up" latch.
            //
            // On dispose rather than in the callbacks below, because there are
            // five ways out of here and only three of them are callbacks: the
            // two CTAs, dismiss, the system back gesture, and a tap on the
            // scrim. Wiring the three would have left the other two latched,
            // which is a subtler version of the bug this fixes — the shake
            // worked exactly once per process because nothing called
            // `onDialogDismissed` at all.
            //
            // Leaving composition is the one event every route out shares.
            DisposableEffect(Unit) {
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
