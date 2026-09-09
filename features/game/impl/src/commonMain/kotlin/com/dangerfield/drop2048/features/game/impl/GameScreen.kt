package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonGhost
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSecondary
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD800
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.game_best
import drop2048.libraries.resources.generated.resources.game_biggest
import drop2048.libraries.resources.generated.resources.game_board
import drop2048.libraries.resources.generated.resources.game_chain
import drop2048.libraries.resources.generated.resources.game_drop
import drop2048.libraries.resources.generated.resources.game_drop_again
import drop2048.libraries.resources.generated.resources.game_falling_block
import drop2048.libraries.resources.generated.resources.game_left_handed
import drop2048.libraries.resources.generated.resources.game_move_left
import drop2048.libraries.resources.generated.resources.game_move_right
import drop2048.libraries.resources.generated.resources.game_paused
import drop2048.libraries.resources.generated.resources.game_quit
import drop2048.libraries.resources.generated.resources.game_restart
import drop2048.libraries.resources.generated.resources.game_resume
import drop2048.libraries.resources.generated.resources.game_score
import drop2048.libraries.resources.generated.resources.game_stacked_out
import drop2048.libraries.resources.generated.resources.game_stats
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The run, on screen.
 *
 * A pure render of [GameUiState]: every rule, every timer and the whole of
 * transcript playback lives in [GameViewModel], so this file has no idea what a
 * cascade is. The one thing it decides is what the player can touch, and that is
 * a single `enabled` flag derived from the phase.
 */
@Composable
fun GameScreen(
    state: GameUiState,
    onAction: (GameAction) -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(reduceMotion) { onAction(GameAction.SetReduceMotion(reduceMotion)) }

    val live = state.phase == GamePhase.Playing

    Screen(contentWindowInsets = WindowInsets.systemBars) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimension.D500, vertical = Dimension.D300),
                verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            ) {
                GameHud(
                    state = state,
                    onPause = { onAction(GameAction.Pause) },
                    pauseEnabled = state.phase == GamePhase.Playing ||
                        state.phase == GamePhase.Resolving,
                )

                BoardView(
                    state = state,
                    chainLabel = stringResource(Res.string.game_chain, state.chainStep),
                    boardDescription = stringResource(
                        Res.string.game_board,
                        state.board.cols,
                        state.board.rows,
                    ),
                    fallingDescription = state.falling?.let { falling ->
                        stringResource(
                            Res.string.game_falling_block,
                            falling.block.spoken(),
                            falling.cell.col + 1,
                            (state.ghost?.row ?: falling.cell.row) + 1,
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .blur(if (state.phase == GamePhase.Paused) PauseBlur else 0.dp),
                )

                ControlBar(
                    leftHanded = state.leftHanded,
                    enabled = live,
                    onMoveLeft = { onAction(GameAction.MoveLeft) },
                    onMoveRight = { onAction(GameAction.MoveRight) },
                    onHardDrop = { onAction(GameAction.HardDrop) },
                    onSoftDropStart = { onAction(GameAction.SoftDropStart) },
                    onSoftDropEnd = { onAction(GameAction.SoftDropEnd) },
                    labels = ControlLabels(
                        moveLeft = stringResource(Res.string.game_move_left),
                        moveRight = stringResource(Res.string.game_move_right),
                        drop = stringResource(Res.string.game_drop),
                    ),
                )
            }

            when (state.phase) {
                GamePhase.Paused -> PauseOverlay(leftHanded = state.leftHanded, onAction = onAction)
                GamePhase.StackedOut -> StackedOutSheet(state = state, onAction = onAction)
                GamePhase.Playing, GamePhase.Resolving -> Unit
            }
        }
    }
}

/**
 * SPEC 8.4. The board is blurred by the screen above; this is the scrim and the
 * options over it, and it deliberately does not draw a second copy of the board.
 */
@Composable
private fun PauseOverlay(leftHanded: Boolean, onAction: (GameAction) -> Unit) {
    Overlay {
        Text(
            text = stringResource(Res.string.game_paused),
            typography = AppTheme.typography.Display.D900,
            textAlign = TextAlign.Center,
        )
        VerticalSpacerD800()
        ButtonPrimary(
            onClick = { onAction(GameAction.Resume) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_resume))
        }
        VerticalSpacerD500()
        ButtonSecondary(
            onClick = { onAction(GameAction.Restart) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_restart))
        }
        VerticalSpacerD500()
        ButtonGhost(
            onClick = { onAction(GameAction.ToggleHandedness) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row {
                Text(stringResource(Res.string.game_left_handed))
                Text(if (leftHanded) OnMark else OffMark)
            }
        }
        VerticalSpacerD500()
        ButtonGhost(
            onClick = { onAction(GameAction.Quit) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_quit))
        }
    }
}

/**
 * SPEC 8.4's "Stacked out", with the score, the biggest tier reached (decision
 * D7 — reached, not at rest, because a 2048 bursts its own row) and one primary
 * button.
 *
 * Continue and any ad offer belong **below** the primary button when they land
 * in C10. Not beside it, and never above it.
 */
@Composable
private fun StackedOutSheet(state: GameUiState, onAction: (GameAction) -> Unit) {
    Overlay {
        Text(
            text = stringResource(Res.string.game_stacked_out),
            typography = AppTheme.typography.Display.D900,
            textAlign = TextAlign.Center,
        )
        VerticalSpacerD800()
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.game_score).uppercase(),
                    typography = AppTheme.typography.Label.L500,
                    color = AppTheme.colors.textSecondary,
                )
                ScoreCounter(score = state.score.toInt(), countFrom = 0)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(Res.string.game_biggest).uppercase(),
                    typography = AppTheme.typography.Label.L500,
                    color = AppTheme.colors.textSecondary,
                )
                BlockCell(
                    block = NumberBlock(BlockValue.ofPoints(state.biggestTier) ?: BlockValue.V2),
                    size = BiggestTierSize,
                )
            }
        }
        VerticalSpacerD800()
        ButtonPrimary(
            onClick = { onAction(GameAction.Restart) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_drop_again))
        }
        VerticalSpacerD500()
        ButtonGhost(
            onClick = { onAction(GameAction.ShowStats) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.game_stats))
        }
        VerticalSpacerD500()
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D200)) {
            Text(
                text = stringResource(Res.string.game_best).uppercase(),
                typography = AppTheme.typography.Label.L500,
                color = AppTheme.colors.textSecondary,
            )
            Text(
                text = state.best.toString(),
                typography = AppTheme.typography.Label.L500,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * The scrim, and a barrier.
 *
 * `pointerInput { awaitEachGesture { awaitFirstDown() } }` is not decoration: a
 * scrim that only *looks* modal leaves the board and the pause button live under
 * it, and on device the pause control was still reachable from the pause menu
 * that was covering it.
 */
@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } }
            .background(AppTheme.colors.background.color.copy(alpha = ScrimAlpha)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(Dimension.D800)
                .background(AppTheme.colors.surfacePrimary.color, Radii.R600.shape)
                .padding(Dimension.D800),
            content = { content() },
        )
    }
}

private fun Block.spoken(): String = when (this) {
    is NumberBlock -> value.points.toString()
    is SpecialBlock -> special.name.lowercase()
}

/** Enough that the stack cannot be studied while paused (SPEC 8.4), not so much it disappears. */
private val PauseBlur = 16.dp

/**
 * Dark enough to make the sheet the only thing to read, light enough that the
 * blurred board behind it is still there — the mockup pauses *over* the board,
 * not instead of it.
 */
private const val ScrimAlpha = 0.7f
private val BiggestTierSize = 48.dp

/**
 * A toggle drawn as a mark rather than a switch, because the pause menu is not
 * the settings screen and C11 will move this line there wholesale.
 */
private const val OnMark = "  ●"
private const val OffMark = "  ○"

@Preview
@Composable
private fun GameScreenPreview() {
    PreviewContent {
        GameScreen(
            state = GameUiState(
                board = Board.empty(5, 8).with(Cell(2, 7), NumberBlock(BlockValue.V16)),
                falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(2, 2)),
                ghost = Cell(2, 6),
                score = 4_896,
                best = 130_450,
                level = 7,
                levelFraction = 0.4f,
                next = listOf(NumberBlock(BlockValue.V2), NumberBlock(BlockValue.V8)),
                hold = NumberBlock(BlockValue.V32),
            ),
            onAction = {},
        )
    }
}
