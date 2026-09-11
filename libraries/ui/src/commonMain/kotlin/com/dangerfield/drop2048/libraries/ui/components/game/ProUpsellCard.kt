package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.system.DeepSurface
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.Radius
import com.dangerfield.drop2048.system.typography.FredokaFontFamily
import com.dangerfield.drop2048.system.typography.NunitoFontFamily

/**
 * SPEC 12's upsell: *one non-modal card on the stacked-out screen at most once
 * per session*.
 *
 * Every word of that sentence is a constraint this component has to honour and
 * three of them are visual.
 *
 * **Non-modal** means it is a card in the flow of the sheet, not a dialog over
 * it. It takes no scrim, it traps no back press, and it has no close button —
 * there is nothing to close, because it is not in the way. Ignoring it costs one
 * glance.
 *
 * **On the stacked-out screen** means it sits *below* Drop again. SPEC 8.4 is
 * explicit that continue and any ad offer sit below the primary button and never
 * above, and the same rule is what keeps this from moving the one control the
 * player is already reaching for.
 *
 * **At most once per session** is not this component's to enforce — the
 * coordinator owns the cap — but it is why the card is quiet. A thing seen once
 * a session can afford to be a muted surface with one line of copy; a thing seen
 * every run has to shout, and then it is a nag.
 *
 * Drawn on the muted surface rather than the accent, deliberately. The accent
 * yellow belongs to Drop again, and a sales card in the same colour as the
 * primary action is a card competing with the thing the player came here to do.
 */
@Composable
fun ProUpsellCard(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DeepSurface(
        color = GameColors.Control,
        shadow = GameColors.ControlShadow,
        highlight = GameColors.TopHighlight,
        shape = Radius(CornerSize(CardRadius)),
        depth = CardDepth,
        pressedDepth = CardPressedDepth,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CardGap),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardPaddingX, vertical = CardPaddingY),
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    fontFamily = FredokaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = TitleSize,
                    color = GameColors.Ink,
                    textAlign = TextAlign.Center,
                ),
            )
            BasicText(
                text = body,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = BodySize,
                    color = GameColors.InkMuted,
                    textAlign = TextAlign.Center,
                ),
            )
            BasicText(
                text = action,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = ActionSize,
                    color = GameColors.AccentYellow,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

private val CardRadius: Dp = 16.dp
private val CardDepth: Dp = 3.dp
private val CardPressedDepth: Dp = 1.dp
private val CardGap: Dp = 4.dp
private val CardPaddingX: Dp = 16.dp
private val CardPaddingY: Dp = 15.dp
private val TitleSize = 16.sp
private val BodySize = 13.sp
private val ActionSize = 14.sp
