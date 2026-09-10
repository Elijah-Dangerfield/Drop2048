package com.dangerfield.drop2048.system.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.dangerfield.drop2048.libraries.ui.system.LocalContentColor
import com.dangerfield.drop2048.libraries.ui.system.color.ColorResource
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors

@Immutable
@Suppress("LongParameterList")
interface Colors {

    val accentPrimary: ColorResource
    val onAccentPrimary: ColorResource
    val accentSecondary: ColorResource
    val onAccentSecondary: ColorResource

    /* Backgrounds */
    val shadow: ColorResource
    val background: ColorResource
    val backgroundOverlay: ColorResource
    val onBackground: ColorResource
    val border: ColorResource

    val borderSecondary: ColorResource
    val borderDisabled: ColorResource

    /* Texts */
    val text: ColorResource
    val textSecondary: ColorResource
    val textDisabled: ColorResource
    val danger: ColorResource

    val status: StatusColor

    /* Surfaces */
    val surfacePrimary: ColorResource
    val onSurfacePrimary: ColorResource
    val surfaceSecondary: ColorResource
    val onSurfaceSecondary: ColorResource
    val surfaceTertiary: ColorResource
    val onSurfaceTertiary: ColorResource

    val surfaceDisabled: ColorResource
    val onSurfaceDisabled: ColorResource

}

interface StatusColor {
    val okay: ColorResource
    val warning: ColorResource
    val bad: ColorResource
}

private fun token(color: Color, name: String) = ColorResource.FromColor(color, name)

/**
 * The one role ramp every screen in the app renders against, and it is dark.
 *
 * ### Why this file changed rather than the meta screens
 *
 * Through C11 this was the template's light ramp — `Gray50` background, `Gray900`
 * text, white surfaces — while the game screen was the handoff's dark board. Two
 * halves of one app that did not look like one app, and a white quit-confirm
 * dialog over a dark board that C11 itself called wrong.
 *
 * There were two ways out. The meta screens could each reach for [GameColors]
 * directly, which is faster and leaves this ramp light, so every template surface
 * nobody rewrote — dialogs, bottom sheets, the snackbar, form fields, the launch
 * gates, the bug reporter — stays light and the next screen anyone adds is light
 * again. That is a third theme waiting to happen.
 *
 * So the design system grew the dark set instead, here, at the one place both
 * halves already read from. No feature module changed a line to get it: they were
 * all already asking for `AppTheme.colors.surfacePrimary` and friends and simply
 * get a different answer.
 *
 * ### Where the values come from
 *
 * [GameColors] is still the source of truth and is still named for the board
 * rather than for a role — see its KDoc for why the two objects do not merge.
 * What happens here is a **mapping**: the backdrop's mid stop becomes the
 * background, the well becomes the deepest surface, the ring becomes the border,
 * the handoff's three-step ink ramp becomes the three text roles, and the accent
 * yellow becomes the primary accent because it is already the colour of every
 * primary button in the game.
 *
 * The four values with no handoff equivalent are the status triple and the danger
 * red, which are lifted to their 400-weight cousins: a `Red600` that reads on
 * white is nearly invisible on `#141021`.
 */
val defaultColors = object : Colors {
    override val accentPrimary = token(GameColors.AccentYellow, "accent-yellow")
    override val onAccentPrimary = token(GameColors.OnAccentYellow, "on-accent-yellow")
    override val accentSecondary = token(GameColors.AccentViolet, "accent-violet")
    override val onAccentSecondary = token(GameColors.BackdropFar, "on-accent-violet")

    override val shadow = token(GameColors.ControlShadow, "control-shadow")
    override val danger = ColorResource.Red400

    override val background = token(GameColors.BackdropMid, "backdrop-mid")
    override val onBackground = token(GameColors.Ink, "ink")
    override val backgroundOverlay = token(GameColors.ScrimPaused, "scrim-paused")

    override val surfacePrimary = token(GameColors.Control, "control")
    override val onSurfacePrimary = token(GameColors.Ink, "ink")
    override val surfaceSecondary = token(GameColors.ControlQuiet, "control-quiet")
    override val onSurfaceSecondary = token(GameColors.Ink, "ink")
    override val surfaceTertiary = token(GameColors.BoardWell, "board-well")
    override val onSurfaceTertiary = token(GameColors.InkFaint, "ink-faint")

    override val surfaceDisabled = token(GameColors.ControlRaised, "control-raised")
    override val onSurfaceDisabled = token(GameColors.InkMuted, "ink-muted")

    override val border = token(GameColors.BoardRing, "board-ring")
    override val borderSecondary = token(GameColors.ControlQuietShadow, "control-quiet-shadow")
    override val borderDisabled = token(GameColors.ControlQuiet, "control-quiet")

    override val text = token(GameColors.Ink, "ink")
    override val textSecondary = token(GameColors.InkFaint, "ink-faint")
    override val textDisabled = token(GameColors.InkMuted, "ink-muted")

    override val status = object : StatusColor {
        override val okay = ColorResource.Green400
        override val warning = ColorResource.Amber500
        override val bad = ColorResource.Red400
    }
}


@Composable
fun ProvideContentColor(color: ColorResource, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides color,
        androidx.compose.material3.LocalContentColor provides color.color,
        content = content
    )
}