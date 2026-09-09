package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.system.typography.FredokaFontFamily
import com.dangerfield.drop2048.system.typography.NunitoFontFamily
import kotlin.math.roundToInt
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The card a guided lesson speaks through, hung off whatever a spotlight is
 * lighting.
 *
 * Meant to be the `content` of a `FocusScrim`, which hands over the union of the
 * lit rectangles as [anchor]. It fills the scrim and places itself, so the caller
 * never does layout maths.
 *
 * Three placement rules, and each of them is a bug that has already been shipped
 * somewhere:
 *
 * - **Below the anchor by default, flipped above it when there is no room.** A
 *   card that always sits below runs off the bottom of the screen the moment the
 *   lesson points at something near the control row, which is where half of them
 *   point.
 * - **Never above [MinTop].** The status bar and the score are up there; a card
 *   clamped to zero sits under the clock. An anchor too tall for the card to
 *   clear in either direction — a whole board, which is most of the screen —
 *   drops to the bottom instead of riding up into the score, because the top of
 *   a board is where the next block appears and the bottom is where the buttons
 *   the lesson is not talking about are.
 * - **An empty [anchor] centres the card.** A lesson with nothing lit is a normal
 *   case here — most of Drop 2048's lessons talk about the board as a whole — and
 *   `Rect.Zero` would otherwise park the card in the top-left corner.
 */
@Composable
fun CoachMark(
    anchor: Rect,
    body: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    skipLabel: String? = null,
    onSkip: () -> Unit = {},
) {
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CardGap),
                modifier = Modifier
                    .widthIn(max = CardMaxWidth)
                    .background(GameColors.BackdropNear, RoundedCornerShape(CardRadius))
                    .padding(CardPadding),
            ) {
                if (title != null) {
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
                }
                BasicText(
                    text = body,
                    style = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = BodySize,
                        lineHeight = BodyLineHeight,
                        color = GameColors.InkFaint,
                        textAlign = TextAlign.Center,
                    ),
                )
                if (confirmLabel != null) {
                    GamePrimaryButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        fontSize = ConfirmSize,
                        horizontalPadding = ConfirmPaddingX,
                        verticalPadding = ConfirmPaddingY,
                    )
                }
                if (skipLabel != null) {
                    QuietAction(text = skipLabel, onClick = onSkip)
                }
            }
        },
    ) { measurables, constraints ->
        val card = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = Margin.roundToPx()
        val gap = AnchorGap.roundToPx()
        val minTop = MinTop.roundToPx()
        val floor = (constraints.maxHeight - card.height - margin).coerceAtLeast(minTop)

        val top = if (anchor.width <= 0f && anchor.height <= 0f) {
            ((constraints.maxHeight - card.height) / 2).coerceIn(minTop, floor)
        } else {
            val below = anchor.bottom.roundToInt() + gap
            val above = anchor.top.roundToInt() - gap - card.height
            when {
                below <= floor -> below
                above >= minTop -> above
                else -> floor
            }
        }

        val centred = (anchor.center.x - card.width / 2f).roundToInt()
        val left = if (anchor.width <= 0f && anchor.height <= 0f) {
            (constraints.maxWidth - card.width) / 2
        } else {
            centred.coerceIn(margin, (constraints.maxWidth - card.width - margin).coerceAtLeast(margin))
        }

        layout(constraints.maxWidth, constraints.maxHeight) { card.place(left, top) }
    }
}

/**
 * Quiet copy that takes a tap, matching the overlays' secondary actions.
 *
 * Drawn rather than built from the template's `ButtonGhost` for the same reason
 * the pause overlay's options are: on a dark scrim a ghost button's border and
 * its role-based ink would be the loudest thing in the frame, and the card
 * already has one saturated object.
 */
@Composable
private fun QuietAction(text: String, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = SkipSize,
            color = GameColors.InkMuted,
        ),
        modifier = Modifier
            .pointerInput(onClick) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    if (waitForUpOrCancellation() != null) onClick()
                }
            }
            .padding(SkipPadding),
    )
}

private val CardMaxWidth: Dp = 290.dp
private val CardRadius: Dp = 18.dp
private val CardPadding: Dp = 18.dp
private val CardGap: Dp = 8.dp
private val Margin: Dp = 16.dp
private val AnchorGap: Dp = 14.dp

/** Below the status bar and below the score row, which is where the card must never go. */
private val MinTop: Dp = 96.dp

private val TitleSize = 20.sp
private val BodySize = 14.sp
private val BodyLineHeight = 21.sp
private val ConfirmSize = 15.sp
private val ConfirmPaddingX: Dp = 26.dp
private val ConfirmPaddingY: Dp = 10.dp
private val SkipSize = 12.sp
private val SkipPadding: Dp = 4.dp

@Preview
@Composable
private fun CoachMarkBelowPreview() {
    PreviewContent {
        CoachMark(
            anchor = Rect(120f, 300f, 400f, 380f),
            title = "Bring it down",
            body = "Nothing falls on its own yet. Tap the arrow.",
            confirmLabel = "Got it",
        )
    }
}

@Preview
@Composable
private fun CoachMarkFlippedPreview() {
    PreviewContent {
        CoachMark(
            anchor = Rect(60f, 1500f, 500f, 1620f),
            title = "Now for real",
            body = "The clock starts now.",
            confirmLabel = "Play",
            skipLabel = "Skip tutorial",
        )
    }
}
