package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.ui.components.LevelProgressBar
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.icon.IconButton
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.game_best
import drop2048.libraries.resources.generated.resources.game_hold
import drop2048.libraries.resources.generated.resources.game_level
import drop2048.libraries.resources.generated.resources.game_next
import drop2048.libraries.resources.generated.resources.game_pause
import drop2048.libraries.resources.generated.resources.game_score
import org.jetbrains.compose.resources.stringResource

/**
 * The header, resolving SPEC 8.1's conflict.
 *
 * The mockups draw score, best, level and a pause button and leave nowhere for
 * the next-two preview or the hold slot, and SPEC 5.4 makes the preview
 * non-optional. Two rows rather than one is what buys the space: the numbers a
 * player *reads* on the first row, the blocks a player *plans against* on the
 * second, at chip size beside the board they will end up on.
 *
 * They are not merged into one row. Score, best and level are three numbers of
 * unpredictable width, and a preview chip pushed sideways by a score rolling
 * from 9,999 to 10,240 is a preview that moves while the player is reading it.
 */
@Composable
fun GameHud(
    state: GameUiState,
    onPause: () -> Unit,
    pauseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimension.D300),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Label(stringResource(Res.string.game_score))
                ScoreCounter(
                    score = state.score.toInt(),
                    abbreviated = true,
                    typography = AppTheme.typography.Display.D800,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Label(stringResource(Res.string.game_best))
                ScoreCounter(
                    score = state.best.toInt(),
                    abbreviated = true,
                    typography = AppTheme.typography.Heading.H500,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Label(stringResource(Res.string.game_level, state.level))
                LevelProgressBar(fraction = state.levelFraction, height = LevelBarHeight)
            }

            ChipSlot(
                label = stringResource(Res.string.game_hold),
                slots = 1,
                dimmed = !state.canHold,
                blocks = listOfNotNull(state.hold),
            )

            ChipSlot(
                label = stringResource(Res.string.game_next),
                slots = PreviewChips,
                blocks = state.next.take(PreviewChips),
            )

            IconButton(
                icon = Icons.Pause(stringResource(Res.string.game_pause)),
                onClick = onPause,
                enabled = pauseEnabled,
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        typography = AppTheme.typography.Label.L500,
        color = AppTheme.colors.textSecondary,
    )
}

/**
 * One labelled well holding preview blocks.
 *
 * Recessed rather than floating, so an empty hold slot reads as a slot with
 * nothing in it rather than as a missing element. [dimmed] is the "already used
 * this drop" state from SPEC 5.4's one-swap rule — the block stays visible,
 * because it is still information, and only stops looking available.
 */
@Composable
private fun ChipSlot(
    label: String,
    slots: Int,
    blocks: List<Block>,
    dimmed: Boolean = false,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Label(label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimension.D100),
            modifier = Modifier
                .clip(Radii.R300.shape)
                .background(AppTheme.colors.surfaceSecondary.color)
                .padding(Dimension.D100)
                .alpha(if (dimmed) DimmedAlpha else 1f),
        ) {
            repeat(slots) { index ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ChipSize)) {
                    blocks.getOrNull(index)?.let { BlockCell(block = it, size = ChipSize) }
                }
            }
        }
    }
}

/** SPEC 5.4: two, and not configurable. One is not a plan and three is a spoiler. */
private const val PreviewChips = 2

private val ChipSize = 26.dp
private val LevelBarHeight = 10.dp
private const val DimmedAlpha = 0.35f
