package com.dangerfield.drop2048.features.game.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.game.GameRoute
import com.dangerfield.drop2048.features.stats.StatsRoute
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.screen
import com.dangerfield.drop2048.libraries.ui.system.LocalCues
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Cues are played here rather than inside [GameScreen] because the event flow is
 * channel-backed: two collectors would split the stream between them and half
 * the merges in a cascade would land silently. One collector, and it is the one
 * that also owns navigation.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class GameFeatureEntryPoint(
    private val gameViewModelFactory: () -> GameViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<GameRoute> {
            val viewModel: GameViewModel = viewModel { gameViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            val cues = LocalCues.current

            viewModel.ObserveEvents { effect ->
                when (effect) {
                    is GameEffect.Play -> cues.play(effect.cue)
                    GameEffect.Leave -> router.goBack()
                    GameEffect.OpenStats -> router.navigate(StatsRoute())
                }
            }

            GameScreen(state = state, onAction = viewModel::takeAction)
        }
    }
}
