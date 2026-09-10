package com.dangerfield.drop2048.features.debug.impl

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.debug.DebugRoute
import com.dangerfield.drop2048.features.game.GameRoute
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.screen
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.ui.snackbar.showSnackBar
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The debug menu's one destination.
 *
 * No typeMap, because [DebugRoute] has no arguments — see the note on the route
 * for why a screen this full of enums keeps every one of them out of the graph.
 *
 * "Start a run with these" navigates to [GameRoute] rather than doing anything
 * itself. The overrides are already in `DebugController` by then, and
 * `RealRunFactory` reads them when the run starts, so the destination is exactly
 * the one the Home screen's Play button uses. There is no second way into the
 * game.
 *
 * The back stack is cleared on the way in, for the reason Settings clears it
 * before replaying the tutorial: leaving the debug menu underneath a live board
 * means a back press lands the tester back on a screen full of switches with a
 * run still going.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class DebugFeatureEntryPoint(
    private val debugViewModelFactory: () -> DebugViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<DebugRoute> {
            val viewModel: DebugViewModel = viewModel { debugViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            val clipboard = LocalClipboardManager.current

            viewModel.ObserveEvents { event ->
                when (event) {
                    DebugEvent.NavigateBack -> router.goBack()
                    DebugEvent.StartRun -> router.navigate(
                        route = GameRoute(),
                        options = NavigationOptions(clearBackStack = true),
                    )

                    is DebugEvent.Message -> showSnackBar(message = messageFor(event.message))
                    is DebugEvent.Copy -> clipboard.setText(AnnotatedString(event.text))
                }
            }

            DebugScreen(state = state, onAction = viewModel::takeAction)
        }
    }

    private fun messageFor(message: DebugMessage): String = when (message) {
        DebugMessage.OverridesCleared -> DebugCopy.MessageOverridesCleared
        DebugMessage.DailyCleared -> DebugCopy.MessageDailyCleared
        DebugMessage.TutorialReset -> DebugCopy.MessageTutorialReset
        DebugMessage.LocalDataReset -> DebugCopy.MessageLocalDataReset
    }
}
