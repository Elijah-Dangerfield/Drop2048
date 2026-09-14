package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.components.rememberLoopingFloat
import com.dangerfield.drop2048.libraries.ui.system.DeepSurface
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.libraries.ui.system.deepFace
import com.dangerfield.drop2048.system.Radius
import com.dangerfield.drop2048.system.typography.FredokaFontFamily
import com.dangerfield.drop2048.system.typography.NunitoFontFamily

/**
 * Which overlay is showing. They differ only in how dark the scrim is — and
 * [Continue] differs in how *light* it is, which is the only interesting entry.
 */
enum class OverlayKind(internal val scrim: Color) {
    Start(GameColors.ScrimStart),
    Paused(GameColors.ScrimPaused),
    GameOver(GameColors.ScrimGameOver),

    /**
     * SPEC 8.4 and 12.2's rewarded continue offer, and the reason its scrim is
     * the lightest in the app.
     *
     * **The player must be able to see exactly what they are saving.** This is
     * the one overlay drawn over a board that still matters: a run they built
     * over ten minutes, one row from the top, with the merge that killed them
     * still on it. A scrim at the game-over weight would dim the thing the offer
     * is about, and the caller deliberately does not blur underneath it either
     * (which is also why `behind` is left null here — see L43: `Modifier.blur`
     * clips to bounds at any radius, so "blur by zero" is not a no-op).
     */
    Continue(GameColors.ScrimContinue),
}

/**
 * The scrim the three overlays share: a dark wash over a 6dp blur, clipped to the
 * board's own 24dp corner so it covers the board and nothing else.
 *
 * Covering only the board is the design decision here, and it is easy to lose.
 * A full-screen scrim would hide the score and the controls too, and the design
 * deliberately keeps them visible and dimmed — a paused game still shows you what
 * you had. It also means "tap to resume" has an obvious target.
 *
 * The blur is applied to a sibling that draws the content underneath rather than
 * to the content itself, so [content] stays sharp. Callers pass what is behind
 * as [behind].
 *
 * @param onTap non-null makes the whole overlay tappable, which is what the
 *   paused state wants and what the other two must not have.
 */
@Composable
fun GameOverlay(
    kind: OverlayKind,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    behind: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(BoardRadius)
    Box(modifier = modifier.graphicsLayer { clip = true; this.shape = shape }) {
        if (behind != null) {
            Box(Modifier.matchParentSize().blur(ScrimBlur)) { behind() }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OverlayGap, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .background(kind.scrim)
                .then(if (onTap == null) Modifier else Modifier.tapToDismiss(onTap))
                .padding(OverlayPadding),
            content = content,
        )
    }
}

/**
 * The primary call to action: `Play`, `Drop again`.
 *
 * The only fully saturated thing on the screen when an overlay is up, which is
 * the whole job. It presses 4dp rather than the controls' 3dp — the design gives
 * the biggest button the biggest travel, and it is the difference between a
 * button that feels expensive and one that feels like a link.
 *
 * @param enabled false holds the face up and swallows the click, for the moment
 *   a purchase is in flight and the store dialog owns the screen. It is
 *   deliberately not dimmed: the button is unusable for the length of one modal
 *   and greying the only saturated thing on the sheet for that long reads as a
 *   broken button rather than as a busy one.
 */
@Composable
fun GamePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = PrimaryFontSize,
    horizontalPadding: Dp = PrimaryPaddingX,
    verticalPadding: Dp = PrimaryPaddingY,
) {
    DeepSurface(
        color = GameColors.AccentYellow,
        shadow = GameColors.AccentYellowShadow,
        highlight = GameColors.AccentHighlight,
        shape = Radius(CornerSize(PillPercent)),
        enabled = enabled,
        depth = PrimaryDepth,
        pressedDepth = PrimaryPressedDepth,
        onClick = onClick,
        modifier = modifier,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                color = GameColors.OnAccentYellow,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
        )
    }
}

/**
 * `DROP` beside a rocking `2048` chip.
 *
 * The rock is 3 seconds and ±3°, which is slow enough to read as a toy rather
 * than as a loading spinner. It holds still under `LocalInspectionMode` and under
 * reduce motion — this one is decoration, so unlike the landing ghost it can stop
 * entirely.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val rock = rememberLoopingFloat(
        initialValue = -RockDegrees,
        targetValue = RockDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = RockMillis),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wordmark-rock",
        previewValue = -RockDegrees,
    )
    val still = LocalReduceMotion.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WordmarkGap),
        modifier = modifier,
    ) {
        BasicText(
            text = WordmarkText,
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = WordmarkSize,
                color = GameColors.Ink,
                shadow = Shadow(
                    color = GameColors.WordmarkShadow,
                    offset = Offset(0f, WordmarkShadowDrop),
                    blurRadius = 0f,
                ),
            ),
        )
        BasicText(
            text = ChipText,
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = ChipSize,
                color = GameColors.OnAccentYellow,
            ),
            modifier = Modifier
                .graphicsLayer { rotationZ = if (still) 0f else rock.value }
                .deepFace(
                    color = GameColors.AccentYellow,
                    shape = Radius(CornerSize(ChipRadius)),
                    depth = ChipDepth,
                    shadow = GameColors.AccentYellowShadow,
                    highlight = GameColors.AccentHighlight,
                )
                .padding(horizontal = ChipPaddingX, vertical = ChipPaddingY),
        )
    }
}

/**
 * The quiet second answer under a [GamePrimaryButton]: `Watch an ad and keep
 * going`, `Restore purchase`, `Main menu`.
 *
 * Nunito rather than Fredoka, muted rather than inked, and no surface at all.
 * That is what makes it read as the *other* option rather than as a second
 * button — a chunky secondary under a chunky primary gives the player two things
 * of equal weight to choose between, which is exactly what an overlay with one
 * obvious action should not do.
 *
 * The gesture is a raw pointer handler rather than `clickable` so it takes no
 * ripple, no minimum touch-target inflation and no indication. The design has
 * none of those, and Material's defaults would give the text a 48dp box that
 * overlaps whatever sits above it.
 */
@Composable
fun GameQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = QuietFontSize,
            color = GameColors.InkMuted,
        ),
        modifier = modifier
            .pointerInput(onClick) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    if (waitForUpOrCancellation() != null) onClick()
                }
            }
            .padding(QuietPadding),
    )
}

private fun Modifier.tapToDismiss(onTap: () -> Unit): Modifier = clickable(
    interactionSource = null,
    indication = null,
    onClick = onTap,
)

/** The board's own corner. The overlay is clipped to it so it covers the board exactly. */
val BoardRadius: Dp = 24.dp

private val ScrimBlur: Dp = 6.dp
private val OverlayGap: Dp = 16.dp
private val OverlayPadding: Dp = 24.dp

private const val PillPercent = 50
private val PrimaryDepth: Dp = 5.dp
private val PrimaryPressedDepth: Dp = 1.dp
private val PrimaryFontSize = 20.sp
private val PrimaryPaddingX: Dp = 34.dp
private val PrimaryPaddingY: Dp = 14.dp

private val QuietFontSize = 13.sp
private val QuietPadding: Dp = 4.dp

private const val WordmarkText = "DROP"
private const val ChipText = "2048"
private val WordmarkGap: Dp = 10.dp
private val WordmarkSize = 36.sp
private const val WordmarkShadowDrop = 4f
private val ChipSize = 25.sp
private val ChipRadius: Dp = 15.dp
private val ChipDepth: Dp = 5.dp
private val ChipPaddingX: Dp = 13.dp
private val ChipPaddingY: Dp = 8.dp
private const val RockDegrees = 3f
private const val RockMillis = 3000
