package com.dangerfield.drop2048.features.daily.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.daily.DailyRoute
import com.dangerfield.drop2048.features.game.GameRoute
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.screen
import com.dangerfield.drop2048.libraries.progress.GameMode
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class DailyFeatureEntryPoint(
    private val dailyViewModelFactory: () -> DailyViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<DailyRoute> {
            val viewModel: DailyViewModel = viewModel { dailyViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    DailyEvent.Leave -> router.goBack()
                    DailyEvent.OpenDaily -> router.navigate(GameRoute(mode = GameMode.DAILY))
                }
            }

            DailyScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
