package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.libraries.ads.LocalBannerSurface
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.core.doNothing
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.dialog.BasicDialog
import com.dangerfield.drop2048.libraries.ui.components.game.BoardRadius
import com.dangerfield.drop2048.libraries.ui.components.game.CoachMark
import com.dangerfield.drop2048.libraries.ui.components.game.CountdownRing
import com.dangerfield.drop2048.libraries.ui.components.game.GameQuietButton
import com.dangerfield.drop2048.libraries.ui.components.game.ProUpsellCard
import com.dangerfield.drop2048.libraries.ui.components.game.FixedWidthDigits
import com.dangerfield.drop2048.libraries.ui.components.game.GameControlRow
import com.dangerfield.drop2048.features.achievements.AchievementCopy
import com.dangerfield.drop2048.libraries.ui.components.feedback.UnlockToastItem
import com.dangerfield.drop2048.libraries.ui.components.feedback.UnlockToasts
import com.dangerfield.drop2048.libraries.ui.components.game.GameOverlay
import com.dangerfield.drop2048.libraries.ui.components.game.GamePrimaryButton
import com.dangerfield.drop2048.libraries.ui.components.game.LevelMeter
import com.dangerfield.drop2048.libraries.ui.components.game.OverlayKind
import com.dangerfield.drop2048.libraries.ui.components.game.PauseButton
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.game.StatLabel
import com.dangerfield.drop2048.libraries.ui.components.game.WellPadding
import com.dangerfield.drop2048.libraries.ui.components.game.Wordmark
import com.dangerfield.drop2048.libraries.ui.components.game.groupThousands
import com.dangerfield.drop2048.libraries.ui.system.FocusRegistry
import com.dangerfield.drop2048.libraries.ui.system.FocusScrim
import com.dangerfield.drop2048.libraries.ui.system.FocusTargetKey
import com.dangerfield.drop2048.libraries.ui.system.LocalFocusRegistry
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.Spotlight
import com.dangerfield.drop2048.libraries.ui.system.focusTarget
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.thenIf
import com.dangerfield.drop2048.system.typography.FredokaFontFamily
import com.dangerfield.drop2048.system.typography.NunitoFontFamily
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.continue_body
import drop2048.libraries.resources.generated.resources.continue_body_second
import drop2048.libraries.resources.generated.resources.continue_countdown_description
import drop2048.libraries.resources.generated.resources.continue_decline
import drop2048.libraries.resources.generated.resources.continue_from_results
import drop2048.libraries.resources.generated.resources.continue_pro
import drop2048.libraries.resources.generated.resources.continue_title
import drop2048.libraries.resources.generated.resources.continue_watch
import drop2048.libraries.resources.generated.resources.game_best_score
import drop2048.libraries.resources.generated.resources.game_biggest
import drop2048.libraries.resources.generated.resources.game_board
import drop2048.libraries.resources.generated.resources.game_callout_board_cleared
import drop2048.libraries.resources.generated.resources.game_callout_detonation
import drop2048.libraries.resources.generated.resources.game_callout_level
import drop2048.libraries.resources.generated.resources.game_callout_row_bust
import drop2048.libraries.resources.generated.resources.game_callout_wildcard
import drop2048.libraries.resources.generated.resources.game_chain
import drop2048.libraries.resources.generated.resources.game_drop_again
import drop2048.libraries.resources.generated.resources.game_falling_block
import drop2048.libraries.resources.generated.resources.game_quit_confirm_action
import drop2048.libraries.resources.generated.resources.game_quit_confirm_body
import drop2048.libraries.resources.generated.resources.game_quit_confirm_cancel
import drop2048.libraries.resources.generated.resources.game_quit_confirm_title
import drop2048.libraries.resources.generated.resources.game_settings
import drop2048.libraries.resources.generated.resources.achievements_unlocked
import drop2048.libraries.resources.generated.resources.game_level_label
import drop2048.libraries.resources.generated.resources.game_move_left
import drop2048.libraries.resources.generated.resources.game_move_right
import drop2048.libraries.resources.generated.resources.game_new_best
import drop2048.libraries.resources.generated.resources.game_hard_drop
import drop2048.libraries.resources.generated.resources.game_pause
import drop2048.libraries.resources.generated.resources.game_paused
import drop2048.libraries.resources.generated.resources.game_play
import drop2048.libraries.resources.generated.resources.game_quit
import drop2048.libraries.resources.generated.resources.game_restart
import drop2048.libraries.resources.generated.resources.game_score
import drop2048.libraries.resources.generated.resources.game_stacked_out
import drop2048.libraries.resources.generated.resources.game_target_cell
import drop2048.libraries.resources.generated.resources.game_start_body
import drop2048.libraries.resources.generated.resources.game_stats
import drop2048.libraries.resources.generated.resources.share_run
import drop2048.libraries.resources.generated.resources.upsell_card_action
import drop2048.libraries.resources.generated.resources.upsell_card_body
import drop2048.libraries.resources.generated.resources.upsell_card_title
import drop2048.libraries.resources.generated.resources.game_tap_to_resume
import drop2048.libraries.resources.generated.resources.tutorial_begin
import drop2048.libraries.resources.generated.resources.tutorial_burst_body
import drop2048.libraries.resources.generated.resources.tutorial_burst_title
import drop2048.libraries.resources.generated.resources.tutorial_first_merge_body
import drop2048.libraries.resources.generated.resources.tutorial_first_merge_title
import drop2048.libraries.resources.generated.resources.tutorial_first_drop_body_arrows
import drop2048.libraries.resources.generated.resources.tutorial_first_drop_body_drag
import drop2048.libraries.resources.generated.resources.tutorial_first_drop_title
import drop2048.libraries.resources.generated.resources.tutorial_got_it
import drop2048.libraries.resources.generated.resources.tutorial_handoff_body
import drop2048.libraries.resources.generated.resources.tutorial_handoff_title
import drop2048.libraries.resources.generated.resources.tutorial_ok
import drop2048.libraries.resources.generated.resources.tutorial_second_body_arrows
import drop2048.libraries.resources.generated.resources.tutorial_second_body_drag
import drop2048.libraries.resources.generated.resources.tutorial_second_title
import drop2048.libraries.resources.generated.resources.tutorial_skip
import drop2048.libraries.resources.generated.resources.tutorial_steer_body_arrows
import drop2048.libraries.resources.generated.resources.tutorial_steer_body_drag
import drop2048.libraries.resources.generated.resources.tutorial_steer_title
import drop2048.libraries.resources.generated.resources.tutorial_third_body
import drop2048.libraries.resources.generated.resources.tutorial_third_title
import drop2048.libraries.resources.generated.resources.tutorial_watch_body
import drop2048.libraries.resources.generated.resources.tutorial_watch_title
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The run, on screen, at the design handoff's fidelity.
 *
 * A pure render of [GameUiState]: every rule, every timer and the whole of
 * transcript playback lives in [GameViewModel], so this file has no idea what a
 * cascade is. What it owns is the layout, the copy, and which of the five phases
 * the player can touch.
 *
 * ### Where this deliberately differs from the handoff
 *
 * **The board is bounded by height as well as by width.** The design hardcodes
 * `max-width: 370px` for a 5x7 board, and 5x8 is taller by a whole row (SPEC 3,
 * settled on a device in C3 — L25). On a short phone that extra row is enough to
 * push the control row off the bottom, and the handoff's `flex: 1; min-height:
 * 8px` spacer collapses to eight pixels and then keeps going. So the board takes
 * the smaller of 370dp, the available width, and the width that fits the height
 * it is given. The spacer is what is left over, which is the handoff's intent
 * rather than its arithmetic.
 *
 * **The paused overlay carries the secondary actions the handoff does not
 * draw.** Its paused state is "Paused / tap to resume" and nothing else, because
 * its prototype has no restart, no handedness setting, nowhere to quit to and no
 * other screens at all. SPEC 8.4 has all of them. They are drawn as quiet text
 * under the design's own copy and they consume their own taps, so
 * tap-anywhere-to-resume still works everywhere else on the overlay.
 *
 * **The start and paused overlays are the app's menu.** C5 deleted the home
 * screen, so this is the launch destination and there is nowhere else for Stats
 * and Settings to be reached from. Until C11 they were reachable *only* from the
 * stacked-out sheet, which meant a player had to lose a run to open either.
 *
 * **[ControlScheme] is honoured here rather than only stored.** `Drag` hides the
 * control row and is the default since the owner's 2026-09-20 ruling, `Buttons`
 * stops [GameBoard] accepting a drag, `Both` is what the game shipped with until
 * then and is what the settings switch turns back on. Decision D11 made drag
 * primary and the buttons the secondary path, and since D21 `Drag` loses
 * **nothing at all**: the downward flick is the same hard drop the ▼ button
 * fires, and there is no held mode left that a gesture cannot express.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    focusRegistry: FocusRegistry = remember { FocusRegistry() },
) {
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(reduceMotion) { onAction(GameAction.SetReduceMotion(reduceMotion)) }

    val live = state.phase == GamePhase.Playing
    val covered = state.phase != GamePhase.Playing && state.phase != GamePhase.Resolving

    // SPEC 12.2: the continue offer keeps the board visible behind its scrim,
    // because the board is the entire argument for taking the offer. It is the
    // one covered phase that is deliberately left sharp — and the modifier is
    // applied conditionally rather than with a zero radius, because
    // `Modifier.blur` clips to its bounds at any radius including zero (L43) and
    // would shave the well's ring off three sides.
    val blurred = covered && state.phase != GamePhase.ContinueOffer

    GameBackHandler(phase = state.phase, onAction = onAction)

    CompositionLocalProvider(LocalFocusRegistry provides focusRegistry) {
        Box(modifier = Modifier.fillMaxSize()) {
            Screen(contentWindowInsets = WindowInsets.systemBars) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = RootPaddingX, vertical = RootPaddingY),
                    verticalArrangement = Arrangement.spacedBy(RootGap),
                ) {
                    GameHeader(
                        state = state,
                        onPause = { onAction(GameAction.Pause) },
                        enabled = state.phase == GamePhase.Playing || state.phase == GamePhase.Resolving,
                    )

                    BoardArea(
                        state = state,
                        blurred = blurred,
                        onAction = onAction,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )

                    if (state.arrowsOnScreen) {
                        GameControlRow(
                            onLeft = { onAction(GameAction.MoveLeft) },
                            onDrop = { onAction(GameAction.HardDrop) },
                            onRight = { onAction(GameAction.MoveRight) },
                            leftDescription = stringResource(Res.string.game_move_left),
                            dropDescription = stringResource(Res.string.game_hard_drop),
                            rightDescription = stringResource(Res.string.game_move_right),
                            enabled = live,
                            dropModifier = Modifier.focusTarget(DropFocusKey),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    } else if (state.bannerAllowed) {
                        BannerStrip(onAction = onAction)
                    }
                }
            }

            TutorialLayer(state = state, onAction = onAction)

            UnlockLayer(state = state, onAction = onAction)

            if (state.confirmingQuit) {
                QuitConfirmDialog(onAction = onAction)
            }
        }
    }
}

/**
 * What the system back gesture does to a run in progress, which until C3a was
 * "close the app".
 *
 * This is the launch destination, so back on it popped an empty stack and the
 * game vanished mid-drop with the run only saved as far as its last lock. Owner
 * ruling: **a game does not close from its play surface.** While a block is
 * falling or a cascade is playing, back does nothing at all — not pause, because
 * a gesture the player made by accident should not also stop the clock and put a
 * menu in front of them.
 *
 * Paused is the exception and the obvious one: an overlay is up, so back closes
 * it, which is what back means everywhere else in the app.
 *
 * `Ready` and `StackedOut` are deliberately absent. Neither is the board — the
 * start overlay is the app's menu (C5 deleted the home screen) and the
 * stacked-out sheet sits over a run that is finished and already written to
 * `run_record`. Leaving the app from either is a decision the player can
 * reasonably be making, and swallowing back there would leave the one screen the
 * app opens on with no way out.
 *
 * The quit confirmation is not handled here. It is a `BasicDialog`, which
 * registers its own handler while it is up, and a nested handler composed later
 * takes precedence — so back dismisses the dialog rather than reaching this.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GameBackHandler(phase: GamePhase, onAction: (GameAction) -> Unit) {
    BackHandler(enabled = phase == GamePhase.Playing || phase == GamePhase.Resolving) {
        doNothing()
    }
    BackHandler(enabled = phase == GamePhase.Paused) {
        onAction(GameAction.Resume)
    }
}

/**
 * SPEC 15's badge announcement, over the stacked-out sheet.
 *
 * It sits at the top of the window rather than inside the sheet, because the
 * sheet is where Drop again lives and a toast that pushed the buttons down would
 * move a target under a thumb that was already on its way to it. It dismisses on
 * its own timer and on a tap; either way it clears the list on the state, so a
 * rotation cannot re-announce it.
 *
 * The copy is resolved here, not in the ViewModel: `AchievementCopy` returns
 * `StringResource`s and a `stringResource` needs a composition. Same split as
 * `GameCallout`.
 */
@Composable
private fun BoxScope.UnlockLayer(state: GameUiState, onAction: (GameAction) -> Unit) {
    if (state.unlocked.isEmpty()) return

    val label = stringResource(Res.string.achievements_unlocked)
    val items = state.unlocked.map { id ->
        UnlockToastItem(
            glyph = AchievementCopy.glyph(id),
            label = label,
            title = stringResource(AchievementCopy.name(id)),
        )
    }

    UnlockToasts(
        items = items,
        onDismiss = { onAction(GameAction.DismissUnlocks) },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(top = UnlockToastTopInset),
    )
}

/**
 * SPEC 11's confirm-before-quit, and the reason it defaults on.
 *
 * Quit ends the run, the run is recorded as it stands, and there is no undo.
 * It also sits one thumb-width from Restart on the same row. Whether this
 * appears at all is the player's, from the settings screen.
 */
@Composable
private fun QuitConfirmDialog(onAction: (GameAction) -> Unit) {
    BasicDialog(
        title = stringResource(Res.string.game_quit_confirm_title),
        description = stringResource(Res.string.game_quit_confirm_body),
        primaryButtonText = stringResource(Res.string.game_quit_confirm_action),
        secondaryButtonText = stringResource(Res.string.game_quit_confirm_cancel),
        onDismissRequest = { onAction(GameAction.DismissQuitConfirm) },
        onPrimaryButtonClicked = { onAction(GameAction.ConfirmQuit) },
        onSecondaryButtonClicked = { onAction(GameAction.DismissQuitConfirm) },
    )
}

/**
 * The guided run's scrim and its card (SPEC 13), drawn over the whole screen.
 *
 * **The scrim takes no touches.** It dims and it points; every tap and every drag
 * reaches the real control underneath, which is what lets the first lesson ask
 * the player to drag the board while the board is the thing that is lit.
 *
 * A silent beat — the drops where the player is simply playing — draws nothing at
 * all, so the board is never dimmed while somebody is aiming at it.
 */
@Composable
private fun BoxScope.TutorialLayer(state: GameUiState, onAction: (GameAction) -> Unit) {
    val frame = state.tutorial
        ?.takeIf { state.phase == GamePhase.Playing || state.phase == GamePhase.Resolving }
        ?.takeIf { it.speaks }
    val arrows = state.arrowsOnScreen

    FocusScrim(spotlight = frame?.spotlight(arrows)) { anchor ->
        if (frame != null) {
            CoachMark(
                anchor = anchor,
                title = tutorialTitle(frame.step),
                body = tutorialBody(frame.step, arrows),
                confirmLabel = if (frame.awaitsTap) tutorialConfirm(frame.step) else null,
                onConfirm = { onAction(GameAction.TutorialAdvance) },
                skipLabel = if (frame.canSkip) stringResource(Res.string.tutorial_skip) else null,
                onSkip = { onAction(GameAction.TutorialSkip) },
            )
        }
    }
}

/**
 * The board is lit alongside the drop control, not instead of it.
 *
 * A beat that lit the control alone would dim the one thing the player has to
 * aim at while asking them to aim — every drop beat here also asks for a
 * placement. The card still hangs off the control, which is what
 * `Spotlight.anchor` is for.
 *
 * ### [arrows] is the fix for a beat that pointed at nothing
 *
 * `TutorialFocus.Drop` resolved to `DropFocusKey` unconditionally, and that key
 * is registered by [GameControlRow] alone. Under `ControlScheme.Drag` there is
 * no control row, so nothing ever reported a rectangle for it: the scrim punched
 * no hole, the card anchored on `Rect.Zero` in the top-left corner, and the one
 * beat whose whole job is to point at the drop control pointed at the corner of
 * the screen. It was reachable before drag became the default — switch the
 * scheme, replay the tutorial — and nothing failed, because the downward flick
 * still works and the run still completes.
 *
 * With no arrows there is no rectangle to light: a flick is a gesture over the
 * board, so the board is what the beat lights and what the card hangs off.
 */
private fun TutorialFrame.spotlight(arrows: Boolean): Spotlight {
    val keys = focus.spotlightKeys(arrows)
    return Spotlight(keys, anchor = keys.lastOrNull())
}

/**
 * The things a [TutorialFocus] lights, given whether the arrow row is on screen.
 *
 * Ordered, and the last one is the anchor: a card hangs off the most specific
 * thing lit, which is the control when there is one and the board when the
 * control is a gesture.
 *
 * Internal so `TutorialFocusKeysTest` can ask the script what it wants and check
 * it against what the screen actually registers, for every scheme. That test is
 * the general form of the bug above — any future focus that resolves to a key
 * some scheme never draws fails it.
 */
internal fun TutorialFocus.spotlightKeys(arrows: Boolean): Set<FocusTargetKey> = when (this) {
    TutorialFocus.None -> emptySet()
    TutorialFocus.Board -> setOf(BoardFocusKey)
    TutorialFocus.Target -> setOf(BoardFocusKey, TargetFocusKey)
    TutorialFocus.Drop -> if (arrows) setOf(BoardFocusKey, DropFocusKey) else setOf(BoardFocusKey)
}

@Composable
private fun tutorialTitle(step: TutorialStep): String? = when (step) {
    TutorialStep.Steer -> stringResource(Res.string.tutorial_steer_title)
    TutorialStep.FirstDrop -> stringResource(Res.string.tutorial_first_drop_title)
    TutorialStep.FirstMerge -> stringResource(Res.string.tutorial_first_merge_title)
    TutorialStep.SecondDrop -> stringResource(Res.string.tutorial_second_title)
    TutorialStep.ThirdDrop -> stringResource(Res.string.tutorial_third_title)
    TutorialStep.WatchThis -> stringResource(Res.string.tutorial_watch_title)
    TutorialStep.BurstIntro -> stringResource(Res.string.tutorial_burst_title)
    TutorialStep.Handoff -> stringResource(Res.string.tutorial_handoff_title)
    else -> null
}

/**
 * The beat's copy, in the control scheme the player is actually holding.
 *
 * Three beats name a control and therefore have two versions. The script is one
 * script — the drops, the boards and the order are the same however the game is
 * driven — because what SPEC 13 teaches is a *habit*, and the habit is "you are
 * the one who puts the block down", not a button. Only the sentence that names
 * the input changes.
 *
 * Adapting rather than writing it once for drag is not a nicety. Under
 * `ControlScheme.Buttons` the board refuses drags outright ([GameBoard]), so
 * "drag anywhere on the board" there is an instruction to do something the game
 * will ignore, on the one beat that cannot be finished any other way. Under
 * `Both` the arrows are on screen and the flick still works, so naming the
 * visible control is the safe half of a true statement.
 */
@Composable
private fun tutorialBody(step: TutorialStep, arrows: Boolean): String = when (step) {
    TutorialStep.Steer -> stringResource(
        if (arrows) Res.string.tutorial_steer_body_arrows else Res.string.tutorial_steer_body_drag,
    )

    TutorialStep.FirstDrop -> stringResource(
        if (arrows) Res.string.tutorial_first_drop_body_arrows else Res.string.tutorial_first_drop_body_drag,
    )

    TutorialStep.SecondDrop -> stringResource(
        if (arrows) Res.string.tutorial_second_body_arrows else Res.string.tutorial_second_body_drag,
    )

    TutorialStep.FirstMerge -> stringResource(Res.string.tutorial_first_merge_body)
    TutorialStep.ThirdDrop -> stringResource(Res.string.tutorial_third_body)
    TutorialStep.WatchThis -> stringResource(Res.string.tutorial_watch_body)
    TutorialStep.BurstIntro -> stringResource(Res.string.tutorial_burst_body)
    TutorialStep.Handoff -> stringResource(Res.string.tutorial_handoff_body)
    else -> stringResource(Res.string.tutorial_watch_body)
}

@Composable
private fun tutorialConfirm(step: TutorialStep): String = when (step) {
    TutorialStep.Handoff -> stringResource(Res.string.tutorial_begin)
    TutorialStep.FirstMerge -> stringResource(Res.string.tutorial_got_it)
    else -> stringResource(Res.string.tutorial_ok)
}

/**
 * The two things a lesson can point at, keyed as strings.
 *
 * Minted here rather than in `:libraries:ui` on purpose: the design system knows
 * how to light a rectangle and nothing about what a board or a ▼ button is.
 *
 * [DropFocusKey] is registered by the arrow row and therefore exists only under
 * the schemes that draw one, which is the asymmetry [spotlightKeys] is for.
 */
internal val BoardFocusKey = FocusTargetKey("game-board")
internal val DropFocusKey = FocusTargetKey("game-drop")

/**
 * The outlined cell, which is a rectangle inside the board rather than a
 * control.
 *
 * Registered by [GameBoard] whenever a beat is outlining a cell. Lighting it
 * inside an already-lit board changes nothing on the scrim — what it is for is
 * the *anchor*, so the card sits above the cell instead of on top of it.
 */
internal val TargetFocusKey = FocusTargetKey("game-target-cell")

/**
 * Whether the player has the arrow row on screen.
 *
 * One reading of the scheme, because three things branch on it — the copy, the
 * spotlight and the row itself — and a tutorial that names a control the screen
 * is not drawing is the bug this whole change is about.
 */
internal val GameUiState.arrowsOnScreen: Boolean get() = controlScheme != ControlScheme.Drag

/**
 * The board and whichever overlay is up, in whatever space the column has left.
 *
 * ### One sizing rule, and the board is centred in what it does not use
 *
 * The board is the largest 5:8 rectangle that fits: the narrower of the width it
 * is given, the width its height allows, and [BoardMaxWidth]. It is then
 * **centred** in the space, which is the whole of the owner's 2026-09-20 "it
 * should have a GONE behaviour, not INVISIBLE".
 *
 * It already behaved like GONE in one sense: hiding the arrow row does hand this
 * composable the row's height, and the board does grow into it until one of the
 * other two bounds binds. What it did next is where the complaint came from. The
 * board was top-aligned under a `Spacer(weight(1f))`, so every dp of height the
 * board could not use became a gutter *at the bottom*, under the board, in
 * exactly the place the player had just been told the arrows were not. On a tall
 * phone that is over 100dp of nothing.
 *
 * Centring cannot be avoided by making the board bigger, and that is worth
 * writing down because it looks like it should be: a 5x8 board is 0.625 wide per
 * tall and a phone is nearer 0.45, so on any phone the board is width-bound and
 * there is *always* height left over. The leftover is real and the only question
 * is where it goes. Split evenly it reads as the board sitting in the screen;
 * all at the bottom it reads as something missing.
 *
 * ### The overlay is a sibling at exactly the board's size
 *
 * The design decision most easily lost in a port: a paused game still shows its
 * score and its controls, dimmed, and "tap to resume" has an obvious target. The
 * board itself is blurred underneath rather than a second copy of it being drawn
 * behind the scrim.
 */
@Composable
private fun BoardArea(
    state: GameUiState,
    blurred: Boolean,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = state.board.cols
    val rows = state.board.rows

    BoxWithConstraints(modifier = modifier) {
        val fromHeight = (maxHeight - WellPadding * 2) * cols / rows + WellPadding * 2
        val width = minOf(maxWidth, BoardMaxWidth, fromHeight)

        Box(
            modifier = Modifier
                .width(width)
                .align(Alignment.Center)
                .focusTarget(BoardFocusKey),
        ) {
            GameBoard(
                state = state,
                boardDescription = stringResource(Res.string.game_board, cols, rows),
                fallingDescription = state.falling?.let { falling ->
                    stringResource(
                        Res.string.game_falling_block,
                        falling.block.spoken(),
                        falling.cell.col + 1,
                        (state.ghost?.row ?: falling.cell.row) + 1,
                    )
                },
                targetDescription = state.tutorial?.target?.let {
                    stringResource(Res.string.game_target_cell, it.col + 1)
                },
                calloutText = state.callout?.let { calloutText(it) },
                onSteerTo = { col -> onAction(GameAction.SteerTo(col)) },
                onFlickDown = { onAction(GameAction.HardDrop) },
                modifier = Modifier
                    .fillMaxWidth()
                    .thenIf(blurred) { blur(OverlayBlur) },
            )

            when (state.phase) {
                GamePhase.Ready -> StartOverlay(
                    onPlay = { onAction(GameAction.Start) },
                    onAction = onAction,
                    modifier = Modifier.matchParentSize(),
                )

                GamePhase.Paused -> PauseOverlay(
                    onAction = onAction,
                    modifier = Modifier.matchParentSize(),
                )

                GamePhase.ContinueOffer -> ContinueOverlay(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.matchParentSize(),
                )

                GamePhase.StackedOut -> StackedOutOverlay(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.matchParentSize(),
                )

                GamePhase.Playing, GamePhase.Resolving -> Unit
            }
        }
    }
}

/**
 * The strip under the board, when the player is not using it for arrows (D28).
 *
 * ### It is one rule, and the rule is "whatever is really there"
 *
 * The board above has `weight(1f)` and this has none, so the board is sized from
 * what is left after this has measured. [com.dangerfield.drop2048.libraries.ads.BannerSurface]
 * emits **nothing at all** until an ad is on screen, which means every reason the
 * banner can be absent produces the identical layout without any of them being
 * enumerated here: no fill, a network error, `ads.banner.enabled` off, ads off,
 * Pro, or iOS, where nothing binds a surface at all. There is no `if` to keep in
 * step with a list of failure modes, because there is no list.
 *
 * `state.bannerAllowed` is not that list either. It is the policy half, decided
 * at a run boundary, and switching it off is only a way to stop the slot asking
 * the network in the first place. The board's size does not depend on it.
 */
@Composable
private fun ColumnScope.BannerStrip(onAction: (GameAction) -> Unit) {
    LocalBannerSurface.current.Banner(
        onFilled = { filled -> onAction(GameAction.BannerFilled(filled)) },
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

/**
 * Score, best, level and pause, in the design's three-part row.
 *
 * The level bar's fill is `blocksDropped % blocksPerLevel`, computed in
 * [GameViewModel] — **not** the handoff's `(merges % 10) * 10`. That formula sits
 * in the handoff's *visual* section, which is exactly why it is worth naming:
 * SPEC 5.5's clock is blocks dropped, deliberately, because a merge-driven clock
 * accelerates the good player into the speed cap for playing well.
 */
@Composable
private fun GameHeader(
    state: GameUiState,
    onPause: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RootGap),
        modifier = modifier.fillMaxWidth().heightIn(min = HeaderHeight),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            StatLabel(stringResource(Res.string.game_score).uppercase())
            ScoreCounter(
                score = state.score.toInt(),
                style = TextStyle(
                    fontFamily = FredokaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = ScoreSize,
                    color = GameColors.Ink,
                ),
                grouped = true,
            )
            BasicText(
                text = stringResource(Res.string.game_best_score, groupThousands(state.best.toInt())),
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = BestSize,
                    color = GameColors.InkMuted,
                ),
                maxLines = 1,
                modifier = Modifier.padding(top = BestGap),
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(LevelGap),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(LevelLabelGap),
            ) {
                StatLabel(stringResource(Res.string.game_level_label).uppercase())
                FixedWidthDigits(
                    text = state.level.toString(),
                    style = TextStyle(
                        fontFamily = FredokaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = LevelSize,
                        color = GameColors.Ink,
                    ),
                )
            }
            LevelMeter(fraction = state.levelFraction)
        }

        PauseButton(
            onClick = onPause,
            contentDescription = stringResource(Res.string.game_pause),
            enabled = enabled,
        )
    }
}

/**
 * The first thing a player sees, and — since C5 deleted the home screen — the
 * only menu the app has before a run starts.
 *
 * Play is the one primary action and the other three sit under it as quiet text,
 * in the same treatment the paused overlay uses, so the design's start card is
 * still a start card rather than a list of destinations.
 */
@Composable
private fun StartOverlay(
    onPlay: () -> Unit,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameOverlay(kind = OverlayKind.Start, modifier = modifier) {
        Wordmark()
        OverlayBody(stringResource(Res.string.game_start_body))
        GamePrimaryButton(label = stringResource(Res.string.game_play), onClick = onPlay)
        MenuOptions(onAction = onAction)
    }
}

/**
 * Stats and Settings, drawn wherever the player is not mid-drop.
 *
 * One composable rather than three call sites because the set is the app's whole
 * navigation surface and it must not drift between the overlays that offer it: a
 * destination added to one and not the other is a screen that exists on Monday
 * and not on Tuesday.
 *
 * A `FlowRow` rather than a `Row`, because the set grows. Four options on a
 * 360dp board (L45) do not fit one line, and a `Row` does not fail by
 * overflowing — it fails by hyphenating, which on device read as "Setti / ngs"
 * under the stacked-out sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MenuOptions(
    onAction: (GameAction) -> Unit,
    share: Boolean = false,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(OptionGap, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(OptionGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (share) {
            OverlayOption(stringResource(Res.string.share_run)) { onAction(GameAction.Share) }
        }
        OverlayOption(stringResource(Res.string.game_stats)) { onAction(GameAction.ShowStats) }
        OverlayOption(stringResource(Res.string.game_settings)) { onAction(GameAction.OpenSettings) }
    }
}


/**
 * SPEC 12.2's rewarded continue, over a board that is still legible.
 *
 * ### Everything about this screen is arguing one point
 *
 * The player is about to lose a run they have been building for ten minutes, and
 * the offer only makes sense if they can see it. So the scrim is the lightest in
 * the app, the board underneath is **not blurred** (the one covered phase where
 * it is not), and the copy is two short lines so it does not take the space the
 * argument lives in.
 *
 * ### The countdown is drawn, not animated
 *
 * [CountdownRing] renders the integer the ViewModel publishes. That keeps the
 * ring honest — it can never sit at 1.2 seconds after the rule has already
 * fired — and it keeps this composable clear of the two hazards that meet
 * exactly here: reading an animated value during composition fails the repo's
 * detekt rule, and an animation left running under `LocalInspectionMode` hangs
 * screenshot capture rather than failing it, so the golden would never finish.
 *
 * ### No tap-to-dismiss
 *
 * Unlike the pause overlay. A stray tap anywhere on the board must not decline
 * an offer that ends the run — "No thanks" is a target the player has to mean to
 * hit, and the eight seconds are there for the case where they mean nothing at
 * all.
 */
@Composable
private fun ContinueOverlay(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameOverlay(kind = OverlayKind.Continue, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OfferGap),
            modifier = Modifier
                .background(GameColors.BackdropMid.copy(alpha = OfferPanelAlpha), OfferPanelShape)
                .padding(horizontal = OfferPanelPaddingX, vertical = OfferPanelPaddingY),
        ) {
            CountdownRing(
                secondsLeft = state.continueSecondsLeft,
                totalSeconds = ContinueCountdownSeconds,
                contentDescription = stringResource(
                    Res.string.continue_countdown_description,
                    state.continueSecondsLeft,
                ),
            )
            OverlayHeadline(stringResource(Res.string.continue_title))
            OverlayBody(
                stringResource(
                    if (state.continueAvailable) Res.string.continue_body_second
                    else Res.string.continue_body,
                ),
            )
            GamePrimaryButton(
                label = stringResource(Res.string.continue_watch),
                onClick = { onAction(GameAction.ContinueAccept) },
            )
            OverlayOption(stringResource(Res.string.continue_decline)) {
                onAction(GameAction.ContinueDecline)
            }
            // SPEC 12's third paywall surface (`PaywallTrigger.Continue`, D28).
            // Under "No thanks" rather than beside it: the question on this
            // screen is whether to save the run, and Pro is a different
            // conversation the player may or may not want to have. Drawn only
            // when the coordinator would accept the tap.
            if (state.proOnContinue) {
                OverlayOption(stringResource(Res.string.continue_pro)) {
                    onAction(GameAction.OpenProFromContinue)
                }
            }
        }
    }
}

/**
 * SPEC 8.4, drawn as the handoff draws it plus the three options the prototype
 * has nowhere to put.
 *
 * Tapping the overlay resumes. The secondary actions sit under the design's
 * "tap to resume" and are their own tap targets, so reaching for Quit cannot
 * resume the game by accident.
 *
 * That last sentence was a claim rather than a fact until the dismiss tap moved
 * off the container that holds them (see [GameOverlay]). The options were their
 * own targets to a finger and to nothing else, so all five of them were one
 * "resume" button to a screen reader. `OverlayMenuTapTest` is what keeps the
 * sentence true.
 *
 */
@Composable
private fun PauseOverlay(
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameOverlay(
        kind = OverlayKind.Paused,
        modifier = modifier,
        onTap = { onAction(GameAction.Resume) },
        onTapLabel = stringResource(Res.string.game_tap_to_resume),
    ) {
        OverlayHeadline(stringResource(Res.string.game_paused))
        OverlayHint(stringResource(Res.string.game_tap_to_resume))
        Row(horizontalArrangement = Arrangement.spacedBy(OptionGap)) {
            OverlayOption(stringResource(Res.string.game_restart)) { onAction(GameAction.Restart) }
            OverlayOption(stringResource(Res.string.game_quit)) { onAction(GameAction.Quit) }
        }
        MenuOptions(onAction = onAction)
    }
}

/**
 * The handoff's game-over overlay: "Stacked out", the two stats, "new best!" when
 * it was, and one primary button.
 *
 * BIGGEST is decision D7 — the highest tier *reached during the run*, not the
 * highest at rest. A 2048 bursts its own row, so the literal reading reports zero
 * for the game's defining moment.
 */
@Composable
private fun StackedOutOverlay(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameOverlay(kind = OverlayKind.GameOver, modifier = modifier) {
        OverlayHeadline(stringResource(Res.string.game_stacked_out))
        Row(horizontalArrangement = Arrangement.spacedBy(StatGap)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatLabel(stringResource(Res.string.game_score).uppercase(), fontSize = FinalLabelSize)
                ScoreCounter(
                    score = state.score.toInt(),
                    countFrom = 0,
                    grouped = true,
                    style = finalStatStyle(),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StatLabel(stringResource(Res.string.game_biggest).uppercase(), fontSize = FinalLabelSize)
                FixedWidthDigits(text = state.biggestTier.toString(), style = finalStatStyle())
            }
        }
        if (state.newBest) {
            BasicText(
                text = stringResource(Res.string.game_new_best),
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = NewBestSize,
                    color = GameColors.AccentYellow,
                ),
            )
        }
        GamePrimaryButton(
            label = stringResource(Res.string.game_drop_again),
            onClick = { onAction(GameAction.Restart) },
            fontSize = DropAgainSize,
            horizontalPadding = DropAgainPaddingX,
            verticalPadding = DropAgainPaddingY,
        )
        // SPEC 8.4: continue and any ad offer sit below the primary button,
        // never above. Both of these are that, and the order is the order of how
        // much they are worth to a player who has just lost a board.
        //
        // The label is not the offer's own "Watch an ad, keep this run" (owner,
        // 2026-09-20). On the offer, the countdown, the headline and two lines
        // of body have already said what an ad buys; here the player is looking
        // at a results sheet with a final score on it, and a bare "watch and
        // continue" reads as an advertisement for an advertisement. So this one
        // says the thing the screen has not: the run is not actually over.
        //
        // The terms are deliberately *not* repeated under it. This option can
        // only be drawn when a continue is still available, and a continue is
        // only still available on a sheet the player reached through the offer,
        // which spent four lines saying exactly what an ad buys. Saying it twice
        // costs a line of a sheet that already overflows a short phone when the
        // Pro card is up.
        if (state.continueAvailable) {
            OverlayOption(stringResource(Res.string.continue_from_results)) {
                onAction(GameAction.ContinueAgain)
            }
        }
        MenuOptions(onAction = onAction, share = true)
        if (state.showUpsell) {
            ProUpsellCard(
                title = stringResource(Res.string.upsell_card_title),
                body = stringResource(Res.string.upsell_card_body),
                action = stringResource(Res.string.upsell_card_action),
                onClick = { onAction(GameAction.OpenPro) },
            )
        }
    }
}

/**
 * SPEC 8.4's eight seconds, as the ring needs them.
 *
 * Duplicated from `GameViewModel` rather than published on the state, because it
 * is the *denominator* of a fraction the screen draws and never a fact about the
 * run. Putting it on `GameUiState` would mean every test that builds a state has
 * an opinion about it, and every golden pins it.
 */
private const val ContinueCountdownSeconds = 8

/**
 * The copy sits on its own plate rather than straight on the board.
 *
 * The offer has two requirements that pull against each other: the board must
 * stay readable (SPEC 12.2 — it is the whole argument) and the offer must be
 * readable too. A scrim dark enough for white text over 1024s takes the board
 * away; a scrim light enough to show the board puts "Keep going?" on top of a
 * tile face. So the scrim stays light and the *text* gets a panel, which leaves
 * the stack visible all around it and behind the well's own margins.
 *
 * It is a plain translucent surface rather than a `DeepSurface`: nothing here is
 * pressable, and the chunky treatment belongs to things the player touches.
 */
private val OfferPanelShape = RoundedCornerShape(20.dp)
private const val OfferPanelAlpha = 0.93f
private val OfferPanelPaddingX: Dp = 20.dp
private val OfferPanelPaddingY: Dp = 18.dp
private val OfferGap: Dp = 12.dp

@Composable
private fun finalStatStyle() = TextStyle(
    fontFamily = FredokaFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = FinalStatSize,
    color = GameColors.Ink,
)

@Composable
private fun OverlayHeadline(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = FredokaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = HeadlineSize,
            color = GameColors.Ink,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun OverlayBody(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = BodySize,
            lineHeight = BodyLineHeight,
            color = GameColors.InkFaint,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun OverlayHint(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = HintSize,
            color = GameColors.InkMuted,
        ),
    )
}

/**
 * A secondary action on an overlay: quiet copy that takes a tap.
 *
 * Drawn rather than built from the template's `ButtonGhost` because these sit on
 * a scrim over the board, where a ghost button's border and its role-based ink
 * would be the loudest thing in the frame. The primary button is the only
 * saturated object an overlay is allowed.
 *
 * ### Why the touch area is capped at half [OptionGap]
 *
 * [GameQuietButton] grows its touch area toward 48dp without taking any layout
 * space, which is only safe as long as it does not grow into the option beside
 * it. On an overlay the options are the tightest-packed things in the app:
 * [OptionGap] horizontally in both the `Row` and the `FlowRow`, the same again
 * between the `FlowRow`'s lines when it wraps, and the overlay column's own 16dp
 * between the two rows. Half of the smallest of those is the most any one option
 * may take, and it makes adjacent targets meet rather than overlap.
 *
 * It buys the full 48dp horizontally. `Quit`, the narrowest label, is 34dp
 * drawn and 7dp a side is exactly the 14dp it is short. Vertically it does not:
 * the options are 25.5dp tall, so they land at 39.5dp against a floor of 48dp.
 * Closing that last 8.5dp means giving the overlay more room between its option
 * rows, which is a design change and not this component's to make. Overlapping
 * them instead is not the trade: overlapping targets are settled by draw order,
 * not by which label is nearer, so the row drawn second would take the whole
 * contested band and a tap just under `Quit` would open `Stats`.
 */
@Composable
private fun OverlayOption(text: String, onClick: () -> Unit) {
    GameQuietButton(text = text, onClick = onClick, maxTouchExpansion = OptionGap / 2)
}

@Composable
private fun calloutText(callout: GameCallout): String = when (callout) {
    is GameCallout.Chain -> stringResource(Res.string.game_chain, callout.step)
    GameCallout.RowBust -> stringResource(Res.string.game_callout_row_bust)
    GameCallout.Detonation -> stringResource(Res.string.game_callout_detonation)
    GameCallout.Wildcard -> stringResource(Res.string.game_callout_wildcard)
    GameCallout.BoardCleared -> stringResource(Res.string.game_callout_board_cleared)
    is GameCallout.LevelUp -> stringResource(Res.string.game_callout_level, callout.level)
}

/** SPEC 16: the falling block is named, not described as "block". */
private fun Block.spoken(): String = when (this) {
    is NumberBlock -> value.points.toString()
    is SpecialBlock -> special.name.lowercase()
}

/** Clear of the header row, so a badge never lands on the live score. */
private val UnlockToastTopInset: Dp = 72.dp

/** The handoff's `padding: 56px 16px 44px`, averaged onto one vertical value. */
private val RootPaddingX: Dp = 16.dp
private val RootPaddingY: Dp = 12.dp
private val RootGap: Dp = 12.dp
/**
 * The handoff's "fixed height, ~60px" header, as a **minimum**.
 *
 * Fixed, it clips: the left stack is a 10px label over a 32px numeral over an
 * 11px best line, which is already past 60 once line heights are counted, and
 * Fredoka is a tall face. The design gets away with it because CSS lets the row
 * overflow visibly; Compose clips instead, and the first thing to disappear is
 * the best score.
 */
private val HeaderHeight: Dp = 60.dp

/**
 * A ceiling that is meant never to bind on a phone, which is the opposite of
 * what the number it replaces did.
 *
 * It was 370dp, which is the handoff's `max-width: 370px`: a CSS number for a
 * 5x7 board, not a measurement of anything. On a 412dp phone the usable width is
 * 380dp, so 370 clipped 10dp off the board for no reason anyone could state, and
 * it did it while more than 100dp of height sat unused below (owner ruling,
 * 2026-09-20).
 *
 * 480dp is wider than the usable width of any phone this ships to, so on a phone
 * the board is bounded by the screen and by the height it is given, and never by
 * this. What it is still for is a tablet or a desktop window, where those two
 * bounds are both enormous and a board allowed to follow them would be a 700dp
 * grid of tiny numbers in the middle of a wall of backdrop.
 */
private val BoardMaxWidth: Dp = 480.dp

/**
 * The overlay's own `backdrop-filter: blur(6px)`, applied to the board it covers.
 *
 * Applied conditionally rather than as `blur(0.dp)` when there is no overlay.
 * `Modifier.blur` defaults to `BlurredEdgeTreatment.Rectangle`, which **clips to
 * bounds at any radius including zero**, and the board well draws its 4dp ring
 * and its danger glow *outside* its own bounds. An unconditional blur therefore
 * silently shaves the ring off three sides of the board with the game running
 * perfectly otherwise, which is exactly the class of bug a golden catches and a
 * code review does not.
 */
private val OverlayBlur: Dp = 6.dp

private val ScoreSize = 32.sp
private val BestSize = 11.sp
private val BestGap: Dp = 2.dp
private val LevelSize = 20.sp
private val LevelGap: Dp = 5.dp
private val LevelLabelGap: Dp = 6.dp

private val HeadlineSize = 34.sp
private val BodySize = 14.sp
private val BodyLineHeight = 22.sp
private val HintSize = 13.sp
private val OptionGap: Dp = 14.dp
private val StatGap: Dp = 26.dp
private val FinalLabelSize = 11.sp
private val FinalStatSize = 28.sp
private val NewBestSize = 14.sp
private val DropAgainSize = 18.sp
private val DropAgainPaddingX: Dp = 30.dp
private val DropAgainPaddingY: Dp = 13.dp

/**
 * A toggle drawn as a mark rather than a switch, because the pause overlay is not
 * the settings screen and C11 will move this line there wholesale.
 */

@Preview
@Composable
private fun GameScreenPreview() {
    PreviewContent {
        GameScreen(
            state = GameUiState(
                board = Board.empty(5, 8)
                    .with(Cell(2, 7), NumberBlock(BlockValue.V16))
                    .with(Cell(1, 7), NumberBlock(BlockValue.V4)),
                falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(2, 2)),
                ghost = Cell(2, 6),
                phase = GamePhase.Playing,
                score = 4_896,
                best = 130_450,
                level = 7,
                levelFraction = 0.4f,
            ),
            onAction = {},
        )
    }
}
