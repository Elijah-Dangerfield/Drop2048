package com.dangerfield.drop2048.features.settings.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.achievements.AchievementsRoute
import com.dangerfield.drop2048.features.debug.DebugRoute
import com.dangerfield.drop2048.features.debug.QaToolsRoute
import com.dangerfield.drop2048.features.game.GameRoute
import com.dangerfield.drop2048.features.home.FeedbackRoute
import com.dangerfield.drop2048.features.settings.LicensesRoute
import com.dangerfield.drop2048.features.settings.SettingsRoute
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.screen
import com.dangerfield.drop2048.libraries.ui.snackbar.showSnackBar
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.settings_debug_unlocked
import drop2048.libraries.resources.generated.resources.settings_delete_done
import drop2048.libraries.resources.generated.resources.settings_reset_done
import me.tatarka.inject.annotations.Inject
import org.jetbrains.compose.resources.stringResource
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Settings' two destinations, and the navigation that hangs off them.
 *
 * **`GameRoute(replayTutorial = true)` is called from here, and this is its only
 * caller.** C5 built the replay path and left it unreachable, which is why SPEC
 * 13 names Settings as its entry point. The back stack is cleared on the way in:
 * the replay opens a board, and leaving the settings screen (and the board it was
 * opened from) underneath it would let a back press land the player in the middle
 * of a tutorial they just left.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class SettingsFeatureEntryPoint(
    private val settingsViewModelFactory: () -> SettingsViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<SettingsRoute> {
            val viewModel: SettingsViewModel = viewModel { settingsViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            val resetDone = stringResource(Res.string.settings_reset_done)
            val deleteDone = stringResource(Res.string.settings_delete_done)
            val debugUnlocked = stringResource(Res.string.settings_debug_unlocked)

            viewModel.ObserveEvents { event ->
                when (event) {
                    SettingsEvent.NavigateBack -> router.goBack()
                    SettingsEvent.OpenAchievements -> router.navigate(AchievementsRoute())
                    SettingsEvent.OpenDebugMenu -> router.navigate(DebugRoute())
                    SettingsEvent.OpenQaTools -> router.navigate(QaToolsRoute())
                    SettingsEvent.OpenFeedback -> router.navigate(FeedbackRoute())
                    SettingsEvent.OpenLicenses -> router.navigate(LicensesRoute())
                    is SettingsEvent.OpenLink -> router.openWebLink(event.url)
                    SettingsEvent.ReplayTutorial -> router.navigate(
                        route = GameRoute(replayTutorial = true),
                        options = NavigationOptions(clearBackStack = true),
                    )

                    SettingsEvent.ProgressReset -> showSnackBar(message = resetDone)
                    SettingsEvent.LocalDataDeleted -> showSnackBar(message = deleteDone)
                    SettingsEvent.DebugMenuUnlocked -> showSnackBar(message = debugUnlocked)
                }
            }

            SettingsScreen(state = state, onAction = viewModel::takeAction)
        }

        screen<LicensesRoute> {
            LicensesScreen(onBack = { router.goBack() })
        }
    }
}
