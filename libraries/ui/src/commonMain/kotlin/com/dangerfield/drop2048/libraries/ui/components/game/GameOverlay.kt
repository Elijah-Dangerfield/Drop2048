package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
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
 * ### The dismiss tap is a layer *behind* the content, never its parent
 *
 * This is the shape of the whole composable and it is not a style preference.
 * `Modifier.clickable` reports `shouldMergeDescendantSemantics`, so hanging the
 * dismiss tap on the container that holds [content] collapses every control
 * inside it into **one** semantics node whose single action is the dismiss.
 * The paused overlay carries the app's entire menu (Restart, Quit, Stats,
 * Settings), and with the tap on the container those controls stopped existing as
 * far as the semantics tree was concerned: one node labelled with all of their
 * text, which resumed the game when activated. Every path that drives the UI
 * through semantics rather than through raw coordinates resumed the game when it
 * asked for Settings. That is TalkBack, Voice Access, Switch Access, and every UI
 * test.
 *
 * Drawn as a sibling under [content] it costs nothing and keeps both halves: the
 * options are their own nodes again, and a tap that lands anywhere they do not
 * claim still falls through to the scrim, because none of the text in [content]
 * takes pointer input of its own.
 *
 * @param onTap non-null makes the whole overlay tappable, which is what the
 *   paused state wants and what the other two must not have.
 * @param onTapLabel what [onTap] does, for the screen reader. The scrim is an
 *   unlabelled full-board control without it.
 */
@Composable
fun GameOverlay(
    kind: OverlayKind,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onTapLabel: String? = null,
    behind: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(BoardRadius)
    Box(modifier = modifier.graphicsLayer { clip = true; this.shape = shape }) {
        if (behind != null) {
            Box(Modifier.matchParentSize().blur(ScrimBlur)) { behind() }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(kind.scrim)
                .then(
                    if (onTap == null) Modifier else Modifier.tapToDismiss(onTap, onTapLabel),
                ),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(OverlayGap, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
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
 * ### `clickable` with no indication, rather than a raw pointer handler
 *
 * This used to be a raw `pointerInput`, on the reasoning that `clickable`
 * brings a ripple and a 48dp box. Half of that holds up. `clickable` does take
 * an [androidx.compose.foundation.Indication], and passing null is the whole of
 * being rid of it; the 48dp box is `minimumInteractiveComponentSize`, which is
 * Material's and which this does not go near.
 *
 * What the raw handler cost was the semantics. It carried no [Role.Button] and
 * no click action, so a screen reader read the label out and offered nothing to
 * activate. Worse, a semantics node is only given Compose's own
 * minimum-touch-target inflation when it carries `SemanticsActions.OnClick`, so
 * every assistive technology that aims at semantics bounds was aiming at the
 * bare 17.5dp-tall label. Adding the action is what moves that to 48dp, and it
 * is the framework that does it.
 *
 * The old KDoc claimed the raw handler took "no minimum touch-target
 * inflation". For the pointer that was never true on its own terms:
 * `ViewConfiguration.minimumTouchTargetSize` is compose-ui's, not Material's,
 * and it inflates any pointer input node. It was true in the place that
 * mattered: an inflated bound is only a near-miss, and any sibling that claims
 * the same pixel outright beats it. The paused overlay's full-board dismiss
 * scrim is exactly such a sibling, and measured there, every option's touch area
 * was its drawn box to the pixel.
 *
 * ### The touch area grows, the layout does not
 *
 * Drawn, this is the label plus [QuietPadding]: 58.5 x 25.5dp for `Settings`,
 * 34 x 25.5dp for `Quit`, both under the floor and the second in both axes.
 * Giving the label a 48dp box would fix it and move every overlay's copy: these
 * sit 14 to 16dp apart in a `Row`, a `FlowRow` and a `Column`, so 22.5dp of
 * extra height each would push the whole stack around.
 *
 * So the inner `layout` reports a box grown toward [QuietTouchFloor] around the
 * label, the pointer and semantics nodes hang off *that*, and the outer `layout`
 * reports the label's own size back to the parent and places the grown box
 * centred on it at a negative offset. The parent measures what it always
 * measured and nothing moves by a pixel.
 *
 * @param maxTouchExpansion how far the touch area may grow past the drawn box on
 *   any one side. The default is half the floor, which is the most reaching the
 *   floor can ever ask for, so it never binds. Pass half the gap to the nearest
 *   neighbour where there is one: two targets that overlap are settled by draw
 *   order rather than by which label is closer, so the one drawn second takes the
 *   whole contested band.
 */
@Composable
fun GameQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxTouchExpansion: Dp = QuietTouchFloor / 2,
) {
    val drawn = remember { DrawnSize() }
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = QuietFontSize,
            color = GameColors.InkMuted,
        ),
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(drawn.width, drawn.height) {
                    placeable.place(
                        x = -(placeable.width - drawn.width) / 2,
                        y = -(placeable.height - drawn.height) / 2,
                    )
                }
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                drawn.width = placeable.width
                drawn.height = placeable.height
                val floor = QuietTouchFloor.roundToPx()
                val cap = maxTouchExpansion.roundToPx()
                val width = placeable.width.grownToward(floor, cap)
                val height = placeable.height.grownToward(floor, cap)
                layout(width, height) {
                    placeable.place(
                        x = (width - placeable.width) / 2,
                        y = (height - placeable.height) / 2,
                    )
                }
            }
            .padding(QuietPadding),
    )
}

/**
 * The floor, unless [cap] runs out first.
 *
 * Deliberately not `this + 2 * min((floor - this) / 2, cap)`, which is the same
 * thing and is a pixel short whenever the difference is odd: 51px of label under
 * a 96px floor halves to 22 and comes back 95. The centring either side of this
 * is written the same way on both nodes, so a box that cannot be halved evenly
 * sits one pixel off centre and the label itself still does not move.
 */
private fun Int.grownToward(floor: Int, cap: Int): Int = minOf(maxOf(this, floor), this + 2 * cap)

/**
 * The label's own measured size, written by the inner `layout` and read by the
 * outer one later in the same measure pass.
 *
 * Deliberately not Compose state. It is one node handing a measurement to the
 * node above it, and making it observable would invalidate the very layout that
 * had just written it.
 */
private class DrawnSize {
    var width: Int = 0
    var height: Int = 0
}

private fun Modifier.tapToDismiss(onTap: () -> Unit, label: String?): Modifier = clickable(
    interactionSource = null,
    indication = null,
    onClickLabel = label,
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

/**
 * The 48dp accessibility floor, and the same number
 * `ViewConfiguration.minimumTouchTargetSize` defaults to on every target this
 * app builds for.
 */
private val QuietTouchFloor: Dp = 48.dp

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
