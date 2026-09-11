package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.daily.DailyRoute
import com.dangerfield.drop2048.features.debug.Diagnostics
import com.dangerfield.drop2048.features.debug.DiagnosticsSettings
import com.dangerfield.drop2048.features.debug.describe
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
import com.dangerfield.drop2048.libraries.ui.debug.BoardDiagnostics
import com.dangerfield.drop2048.libraries.ui.debug.DiagnosticsOverlay
import com.dangerfield.drop2048.libraries.ui.debug.LocalBoardDiagnostics
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
    private val diagnostics: Diagnostics,
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

            val diagnosticsSettings = diagnostics.settings.collectAsStateWithLifecycle().value
            CompositionLocalProvider(
                LocalBoardDiagnostics provides BoardDiagnostics(
                    showCellCoordinates = diagnosticsSettings.showCellCoordinates,
                    showMergeArrows = diagnosticsSettings.showMergeArrows,
                ),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GameScreen(state = state, onAction = viewModel::takeAction)
                    DiagnosticsPanel(
                        settings = diagnosticsSettings,
                        cascadeStep = state.chainStep,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        }
    }

    /**
     * SPEC 19's overlay, drawn over the board rather than inside it.
     *
     * Over, because everything it reports is about the *frame*: the rate it is
     * arriving at, how far the drop clock has drifted, which cascade step is on
     * screen, and what the engine said happened. None of it is a property of a
     * cell, so none of it belongs in `GameBoard` — the two things that are
     * (coordinates and merge arrows) are drawn there instead.
     *
     * **Bottom-aligned, and that was a correction.** It started at the top, where
     * it covered SCORE and LEVEL — which is the wrong half to lose, because "what
     * did that cascade pay" is most of what somebody reading a transcript wants
     * to know, and it is only on screen while the run is alive. At the bottom it
     * covers the three control buttons instead, and the board's own drag control
     * (`ControlScheme.Both` is the default) still steers and still flicks, so the
     * run stays playable with the overlay up. Found by turning it on and looking
     * at it.
     *
     * Composed unconditionally and drawn only when something is switched on, so
     * the state collection lives for the life of the screen rather than being
     * torn down and rebuilt every time a tester flips a switch.
     */
    @Composable
    private fun DiagnosticsPanel(
        settings: DiagnosticsSettings,
        cascadeStep: Int,
        modifier: Modifier = Modifier,
    ) {
        if (!settings.anythingOn) return
        val tick = diagnostics.tick.collectAsStateWithLifecycle().value
        val transcript = diagnostics.lastResolution.collectAsStateWithLifecycle().value
        DiagnosticsOverlay(
            modifier = modifier,
            showFrameRate = settings.showFrameRate,
            intendedTickMs = tick.intendedMs.takeIf { settings.showTick },
            actualTickMs = tick.actualMs.takeIf { settings.showTick },
            cascadeStep = cascadeStep.takeIf { settings.showTranscript },
            transcript = if (settings.showTranscript) {
                transcript.describe().map { "${it.step}  ${it.text}  +${it.points}" }
            } else {
                emptyList()
            },
        )
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
