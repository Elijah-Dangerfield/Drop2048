package com.dangerfield.drop2048.features.stats.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.stats.StatsRoute
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.screen
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class StatsFeatureEntryPoint(
    private val statsViewModelFactory: () -> StatsViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<StatsRoute> {
            val viewModel: StatsViewModel = viewModel { statsViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    StatsEvent.Leave -> router.goBack()
                }
            }

            StatsScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
