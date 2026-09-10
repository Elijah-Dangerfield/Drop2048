package com.dangerfield.drop2048.features.game.impl

import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.daily.DailyRoute
import com.dangerfield.drop2048.features.game.GameRoute
import com.dangerfield.drop2048.features.game.GameRouteTypeMap
import com.dangerfield.drop2048.features.settings.SettingsRoute
import com.dangerfield.drop2048.features.stats.StatsRoute
import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.screen
import com.dangerfield.drop2048.libraries.navigation.toRouteOrNull
import com.dangerfield.drop2048.libraries.sharing.ShareLabels
import com.dangerfield.drop2048.libraries.sharing.ShareLauncher
import com.dangerfield.drop2048.libraries.sharing.ShareText
import com.dangerfield.drop2048.libraries.ui.system.LocalCues
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.share_footer
import drop2048.libraries.resources.generated.resources.share_title_daily
import drop2048.libraries.resources.generated.resources.share_title_endless
import org.jetbrains.compose.resources.stringResource
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Cues are played here rather than inside [GameScreen] because the event flow is
 * channel-backed: two collectors would split the stream between them and half
 * the merges in a cascade would land silently. One collector, and it is the one
 * that also owns navigation.
 *
 * **The share is assembled here too**, for the same reason and one more: the
 * words come from `:libraries:resources` and need a composition, and `ShareText`
 * deliberately cannot see one. `GameViewModel` sends numbers and a UTC day; this
 * is where they become a string and reach the platform's share sheet. Wiring the
 * call site in the same chunk as the module is the whole point — a launcher with
 * no caller looks exactly like coverage.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class GameFeatureEntryPoint(
    private val gameViewModelFactory: () -> GameViewModel,
    private val shareLauncher: ShareLauncher,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<GameRoute>(typeMap = GameRouteTypeMap) { backStackEntry ->
            val viewModel: GameViewModel = viewModel { gameViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value
            val cues = LocalCues.current
            val route = backStackEntry.toRouteOrNull<GameRoute>()
            val replay = route?.replayTutorial == true
            val daily = route?.mode == GameMode.DAILY
            val endlessTitle = stringResource(Res.string.share_title_endless)
            val dailyTitle = stringResource(Res.string.share_title_daily)
            val footer = stringResource(Res.string.share_footer)

            LaunchedEffect(replay) {
                if (replay) viewModel.takeAction(GameAction.ReplayTutorial)
            }

            LaunchedEffect(daily) {
                if (daily) viewModel.takeAction(GameAction.StartDaily)
            }

            viewModel.ObserveEvents { effect ->
                when (effect) {
                    is GameEffect.Play -> cues.play(effect.cue)
                    GameEffect.Leave -> router.goBack()
                    GameEffect.OpenStats -> router.navigate(StatsRoute())
                    GameEffect.OpenDaily -> router.navigate(DailyRoute())
                    GameEffect.OpenSettings -> router.navigate(SettingsRoute())
                    is GameEffect.Share -> shareLauncher.share(
                        ShareText.format(
                            result = effect.result,
                            labels = ShareLabels(
                                title = effect.day?.let { "$dailyTitle$TitleSeparator$it" }
                                    ?: endlessTitle,
                                footer = footer,
                            ),
                        )
                    )
                }
            }

            GameScreen(state = state, onAction = viewModel::takeAction)
        }
    }

    private companion object {
        /**
         * A middot between the mode and the UTC day.
         *
         * Concatenated rather than interpolated into the resource, because the
         * day is not known at composition time and `stringResource` cannot be
         * called from the event collector. It is punctuation between two things,
         * not a sentence, so there is nothing here for a translator to reorder.
         */
        const val TitleSeparator = " · "
    }
}
