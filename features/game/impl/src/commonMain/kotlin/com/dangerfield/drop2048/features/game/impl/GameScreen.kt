package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.game.BoardRadius
import com.dangerfield.drop2048.libraries.ui.components.game.CoachMark
import com.dangerfield.drop2048.libraries.ui.components.game.FixedWidthDigits
import com.dangerfield.drop2048.libraries.ui.components.game.GameControlRow
import com.dangerfield.drop2048.libraries.ui.components.game.GameOverlay
import com.dangerfield.drop2048.libraries.ui.components.game.GamePrimaryButton
import com.dangerfield.drop2048.libraries.ui.components.game.LevelMeter
import com.dangerfield.drop2048.libraries.ui.components.game.OverlayKind
import com.dangerfield.drop2048.libraries.ui.components.game.PauseButton
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.game.StatLabel
import com.dangerfield.drop2048.libraries.ui.components.game.WellPadding
import com.dangerfield.drop2048.libraries.ui.components.game.Wordmark
import com.dangerfield.drop2048.libraries.ui.components.game.gameBackdrop
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
import drop2048.libraries.resources.generated.resources.game_left_handed
import drop2048.libraries.resources.generated.resources.game_level_label
import drop2048.libraries.resources.generated.resources.game_move_left
import drop2048.libraries.resources.generated.resources.game_move_right
import drop2048.libraries.resources.generated.resources.game_new_best
import drop2048.libraries.resources.generated.resources.game_nudge
import drop2048.libraries.resources.generated.resources.game_pause
import drop2048.libraries.resources.generated.resources.game_paused
import drop2048.libraries.resources.generated.resources.game_play
import drop2048.libraries.resources.generated.resources.game_quit
import drop2048.libraries.resources.generated.resources.game_restart
import drop2048.libraries.resources.generated.resources.game_score
import drop2048.libraries.resources.generated.resources.game_stacked_out
import drop2048.libraries.resources.generated.resources.game_start_body
import drop2048.libraries.resources.generated.resources.game_stats
import drop2048.libraries.resources.generated.resources.game_tap_to_resume
import drop2048.libraries.resources.generated.resources.tutorial_begin
import drop2048.libraries.resources.generated.resources.tutorial_burst_body
import drop2048.libraries.resources.generated.resources.tutorial_burst_title
import drop2048.libraries.resources.generated.resources.tutorial_first_merge_body
import drop2048.libraries.resources.generated.resources.tutorial_first_merge_title
import drop2048.libraries.resources.generated.resources.tutorial_first_nudge_body
import drop2048.libraries.resources.generated.resources.tutorial_first_nudge_title
import drop2048.libraries.resources.generated.resources.tutorial_got_it
import drop2048.libraries.resources.generated.resources.tutorial_handoff_body
import drop2048.libraries.resources.generated.resources.tutorial_handoff_title
import drop2048.libraries.resources.generated.resources.tutorial_keep_nudging_body
import drop2048.libraries.resources.generated.resources.tutorial_ok
import drop2048.libraries.resources.generated.resources.tutorial_second_body
import drop2048.libraries.resources.generated.resources.tutorial_second_title
import drop2048.libraries.resources.generated.resources.tutorial_skip
import drop2048.libraries.resources.generated.resources.tutorial_steer_body
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
 * **The paused overlay carries three secondary actions the handoff does not
 * draw.** Its paused state is "Paused / tap to resume" and nothing else, because
 * its prototype has no restart, no handedness setting and nowhere to quit to.
 * SPEC 8.4 has all three. They are drawn as quiet text under the design's own
 * copy and they consume their own taps, so tap-anywhere-to-resume still works
 * everywhere else on the overlay.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(reduceMotion) { onAction(GameAction.SetReduceMotion(reduceMotion)) }

    val live = state.phase == GamePhase.Playing
    val covered = state.phase != GamePhase.Playing && state.phase != GamePhase.Resolving

    val registry = remember { FocusRegistry() }

    CompositionLocalProvider(LocalFocusRegistry provides registry) {
        Box(modifier = Modifier.fillMaxSize()) {
            Screen(contentWindowInsets = WindowInsets.systemBars) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .gameBackdrop()
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
                        covered = covered,
                        onAction = onAction,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )

                    GameControlRow(
                        onLeft = { onAction(GameAction.MoveLeft) },
                        onNudge = { onAction(GameAction.Nudge) },
                        onRight = { onAction(GameAction.MoveRight) },
                        leftDescription = stringResource(Res.string.game_move_left),
                        nudgeDescription = stringResource(Res.string.game_nudge),
                        rightDescription = stringResource(Res.string.game_move_right),
                        enabled = live,
                        mirrored = state.leftHanded,
                        nudgeModifier = Modifier
                            .softDropOnHold(
                                enabled = live,
                                onStart = { onAction(GameAction.SoftDropStart) },
                                onEnd = { onAction(GameAction.SoftDropEnd) },
                            )
                            .focusTarget(NudgeFocusKey),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            TutorialLayer(state = state, onAction = onAction)
        }
    }
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

    FocusScrim(spotlight = frame?.spotlight()) { anchor ->
        if (frame != null) {
            CoachMark(
                anchor = anchor,
                title = tutorialTitle(frame.step),
                body = tutorialBody(frame.step),
                confirmLabel = if (frame.awaitsTap) tutorialConfirm(frame.step) else null,
                onConfirm = { onAction(GameAction.TutorialAdvance) },
                skipLabel = if (frame.canSkip) stringResource(Res.string.tutorial_skip) else null,
                onSkip = { onAction(GameAction.TutorialSkip) },
            )
        }
    }
}

/**
 * The board is lit alongside ▼, not instead of it.
 *
 * A beat that lit the control alone would dim the one thing the player has to
 * aim at while asking them to aim — every ▼ beat here also asks for a placement.
 * The card still hangs off ▼, which is what `Spotlight.anchor` is for.
 */
private fun TutorialFrame.spotlight(): Spotlight = when (focus) {
    TutorialFocus.None -> Spotlight(emptySet())
    TutorialFocus.Board -> Spotlight(setOf(BoardFocusKey), anchor = BoardFocusKey)
    TutorialFocus.Nudge -> Spotlight(setOf(BoardFocusKey, NudgeFocusKey), anchor = NudgeFocusKey)
}

@Composable
private fun tutorialTitle(step: TutorialStep): String? = when (step) {
    TutorialStep.Steer -> stringResource(Res.string.tutorial_steer_title)
    TutorialStep.FirstNudge -> stringResource(Res.string.tutorial_first_nudge_title)
    TutorialStep.FirstMerge -> stringResource(Res.string.tutorial_first_merge_title)
    TutorialStep.SecondDrop -> stringResource(Res.string.tutorial_second_title)
    TutorialStep.ThirdDrop -> stringResource(Res.string.tutorial_third_title)
    TutorialStep.WatchThis -> stringResource(Res.string.tutorial_watch_title)
    TutorialStep.BurstIntro -> stringResource(Res.string.tutorial_burst_title)
    TutorialStep.Handoff -> stringResource(Res.string.tutorial_handoff_title)
    else -> null
}

@Composable
private fun tutorialBody(step: TutorialStep): String = when (step) {
    TutorialStep.Steer -> stringResource(Res.string.tutorial_steer_body)
    TutorialStep.FirstNudge -> stringResource(Res.string.tutorial_first_nudge_body)
    TutorialStep.FirstMerge -> stringResource(Res.string.tutorial_first_merge_body)
    TutorialStep.SecondDrop -> stringResource(Res.string.tutorial_second_body)
    TutorialStep.ThirdDrop -> stringResource(Res.string.tutorial_third_body)
    TutorialStep.WatchThis -> stringResource(Res.string.tutorial_watch_body)
    TutorialStep.BurstIntro -> stringResource(Res.string.tutorial_burst_body)
    TutorialStep.Handoff -> stringResource(Res.string.tutorial_handoff_body)
    else -> stringResource(Res.string.tutorial_keep_nudging_body)
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
 * how to light a rectangle and nothing about what a board or a nudge button is.
 */
private val BoardFocusKey = FocusTargetKey("game-board")
private val NudgeFocusKey = FocusTargetKey("game-nudge")

/**
 * The board, the flex spacer under it, and whichever overlay is up.
 *
 * The overlay is a sibling of the board at exactly the board's size rather than a
 * full-screen scrim, which is the design decision most easily lost in a port: a
 * paused game still shows its score and its controls, dimmed, and "tap to resume"
 * has an obvious target. The board itself is blurred underneath rather than a
 * second copy of it being drawn behind the scrim.
 */
@Composable
private fun BoardArea(
    state: GameUiState,
    covered: Boolean,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cols = state.board.cols
    val rows = state.board.rows

    Column(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fromHeight = (maxHeight - WellPadding * 2) * cols / rows + WellPadding * 2
            val width = minOf(maxWidth, BoardMaxWidth, fromHeight)

            Box(
                modifier = Modifier
                    .width(width)
                    .align(Alignment.TopCenter)
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
                    calloutText = state.callout?.let { calloutText(it) },
                    onSteerTo = { col -> onAction(GameAction.SteerTo(col)) },
                    onFlickDown = { onAction(GameAction.Nudge) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .thenIf(covered) { blur(OverlayBlur) },
                )

                when (state.phase) {
                    GamePhase.Ready -> StartOverlay(
                        onPlay = { onAction(GameAction.Start) },
                        modifier = Modifier.matchParentSize(),
                    )

                    GamePhase.Paused -> PauseOverlay(
                        leftHanded = state.leftHanded,
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

        Spacer(modifier = Modifier.weight(1f).heightIn(min = SpacerMin))
    }
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

@Composable
private fun StartOverlay(onPlay: () -> Unit, modifier: Modifier = Modifier) {
    GameOverlay(kind = OverlayKind.Start, modifier = modifier) {
        Wordmark()
        OverlayBody(stringResource(Res.string.game_start_body))
        GamePrimaryButton(label = stringResource(Res.string.game_play), onClick = onPlay)
    }
}

/**
 * SPEC 8.4, drawn as the handoff draws it plus the three options the prototype
 * has nowhere to put.
 *
 * Tapping the overlay resumes. The three secondary actions sit under the design's
 * "tap to resume" and are their own tap targets, so reaching for Quit cannot
 * resume the game by accident.
 */
@Composable
private fun PauseOverlay(
    leftHanded: Boolean,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    GameOverlay(
        kind = OverlayKind.Paused,
        modifier = modifier,
        onTap = { onAction(GameAction.Resume) },
    ) {
        OverlayHeadline(stringResource(Res.string.game_paused))
        OverlayHint(stringResource(Res.string.game_tap_to_resume))
        Row(horizontalArrangement = Arrangement.spacedBy(OptionGap)) {
            OverlayOption(stringResource(Res.string.game_restart)) { onAction(GameAction.Restart) }
            OverlayOption(
                text = stringResource(Res.string.game_left_handed) +
                    if (leftHanded) OnMark else OffMark,
            ) { onAction(GameAction.ToggleHandedness) }
            OverlayOption(stringResource(Res.string.game_quit)) { onAction(GameAction.Quit) }
        }
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
        OverlayOption(stringResource(Res.string.game_stats)) { onAction(GameAction.ShowStats) }
    }
}

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
 */
@Composable
private fun OverlayOption(text: String, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = OptionSize,
            color = GameColors.InkMuted,
        ),
        modifier = Modifier
            .pointerInput(onClick) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    if (waitForUpOrCancellation() != null) onClick()
                }
            }
            .padding(OptionPadding),
    )
}

/**
 * Tap to nudge, hold to soft drop (SPEC 6), on the one control that has both.
 *
 * The gesture only **watches**: it never consumes, so the button underneath still
 * detects its own click and still shows its own press state. That is also why
 * disarming the tap while soft-dropping is a flag rather than a consume — the
 * click has already fired by the time this sees the pointer come up.
 */
@Composable
private fun Modifier.softDropOnHold(
    enabled: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val holding = remember { mutableStateOf(false) }
    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            val quick = withTimeoutOrNull(SoftDropAfterMillis) { waitForUpOrCancellation() }
            if (quick == null) {
                holding.value = true
                onStart()
                waitForUpOrCancellation()
                onEnd()
                holding.value = false
            }
        }
    }
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

/** The design's 370px board, which is a ceiling and no longer the only bound. */
private val BoardMaxWidth: Dp = 370.dp

/** The handoff's `flex: 1; min-height: 8px`. */
private val SpacerMin: Dp = 8.dp

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
private val OptionSize = 13.sp
private val OptionPadding: Dp = 4.dp
private val OptionGap: Dp = 14.dp
private val StatGap: Dp = 26.dp
private val FinalLabelSize = 11.sp
private val FinalStatSize = 28.sp
private val NewBestSize = 14.sp
private val DropAgainSize = 18.sp
private val DropAgainPaddingX: Dp = 30.dp
private val DropAgainPaddingY: Dp = 13.dp

/**
 * Long enough that a decisive tap is never read as a hold, short enough that a
 * player who meant to nudge does not feel the control stick.
 */
private const val SoftDropAfterMillis = 160L

/**
 * A toggle drawn as a mark rather than a switch, because the pause overlay is not
 * the settings screen and C11 will move this line there wholesale.
 */
private const val OnMark = " ●"
private const val OffMark = " ○"

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
