package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors

/**
 * The screen the game is played on: the handoff's
 * `radial-gradient(120% 70% at 50% -10%, …)`, sized to whatever it is drawn into.
 *
 * A modifier rather than a `Box` with a background, because the brush depends on
 * the measured size and a `Brush` built at composition time is built before
 * anything has been measured. Bake it once and it stretches wrongly on the next
 * phone — which is a bug that only appears on hardware nobody on the team owns.
 */
fun Modifier.gameBackdrop(): Modifier = drawBehind {
    drawRect(GameColors.backdrop(size.width, size.height))
}
