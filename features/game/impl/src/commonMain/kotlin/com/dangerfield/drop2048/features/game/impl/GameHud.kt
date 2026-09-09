package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.components.LevelProgressBar
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.icon.IconButton
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.game_best
import drop2048.libraries.resources.generated.resources.game_level
import drop2048.libraries.resources.generated.resources.game_pause
import drop2048.libraries.resources.generated.resources.game_score
import org.jetbrains.compose.resources.stringResource

/**
 * The header. Score, best, level, pause.
 *
 * SPEC 8.1's conflict is gone rather than resolved: it was "the mockups leave
 * nowhere for the next-two preview or the hold slot", and decision D11 cut both.
 * The two rows stay, because score, best and level are three numbers of
 * unpredictable width and a level bar that slides sideways as the score rolls
 * from 9,999 to 10,240 is a bar nobody can read.
 *
 * This is deliberately *not* the handoff's header — that restyle is C3b. What
 * changed here is only what the ruling removed.
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

private val LevelBarHeight = 10.dp
