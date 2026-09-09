package com.dangerfield.drop2048.libraries.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.components.game.BoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.BoardWell
import com.dangerfield.drop2048.libraries.ui.components.game.GameControlRow
import com.dangerfield.drop2048.libraries.ui.components.game.GameOverlay
import com.dangerfield.drop2048.libraries.ui.components.game.GamePrimaryButton
import com.dangerfield.drop2048.libraries.ui.components.game.GameToast
import com.dangerfield.drop2048.libraries.ui.components.game.LandingGhost
import com.dangerfield.drop2048.libraries.ui.components.game.LocalBoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.OverlayKind
import com.dangerfield.drop2048.libraries.ui.components.game.PauseButton
import com.dangerfield.drop2048.libraries.ui.components.game.Tile
import com.dangerfield.drop2048.libraries.ui.components.game.TileSlot
import com.dangerfield.drop2048.libraries.ui.components.game.Wordmark
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.libraries.ui.system.color.TIER_VALUES
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.typography.FredokaFontFamily
import com.dangerfield.drop2048.system.typography.NunitoFontFamily

/*
 * The game surface, as catalog pages.
 *
 * These live in the real catalog rather than in a parallel one — Sodogku forked
 * its game catalog into a file `DesignSystemPreview` never aggregates, and the
 * result was a design system half of which was browsable only by someone who
 * already knew the file existed. The previews that render these are in
 * `DesignSystemPreview.kt` with all the others.
 *
 * Every page draws on [GameColors.BoardWell] rather than on the catalog's light
 * background, because a chunky dark surface judged against white is judged
 * against the wrong thing. The screenshot harness captures exactly these.
 */

/** The board surface: the well, its ring, the danger state, the slots and the tiles. */
@Composable
internal fun GameBoardSection() {
    CatalogSection(
        title = "The board",
        description = "The well is a near-black panel with an inner shadow and a 4dp ring. " +
            "Danger swaps the ring to red and adds a glow outside it, over 300ms.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D800)) {
            MiniBoard(danger = false, caption = "resting")
            MiniBoard(danger = true, caption = "danger — a tile in the top two rows")
        }
    }
}

/** The eleven tiers as the design draws them, plus the landing ghost's two states. */
@Composable
internal fun GameTileSection() {
    CatalogSection(
        title = "Tiles",
        description = "One hue per tier at a constant lightness, a hard shadow at L-0.22, " +
            "an inset top highlight, and a numeral in the tier's own hue. Radius is 22% of " +
            "the tile, so it scales with the board rather than with the phone.",
    ) {
        CompositionLocalProvider(LocalBoardScale provides CatalogScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D500)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                    TIER_VALUES.take(TilesPerRow).forEach { Tile(value = it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D300)) {
                    TIER_VALUES.drop(TilesPerRow).forEach { Tile(value = it) }
                }
            }
        }
    }
}

/** Empty slot, active column, and the two ghosts. */
@Composable
internal fun GameCellSection() {
    CatalogSection(
        title = "Slots and the landing ghost",
        description = "The ghost has two states and they are not the same thing with a " +
            "different opacity: a landing that will merge says so with a ×2.",
    ) {
        CompositionLocalProvider(LocalBoardScale provides CatalogScale) {
            OnBoard {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D400)) {
                    Captioned("empty slot") { TileSlot() }
                    Captioned("active column") { TileSlot(active = true) }
                    Captioned("will not merge") { LandingGhost(willMerge = false) }
                    Captioned("will merge") { LandingGhost(willMerge = true) }
                    Captioned("falling") { Tile(value = 8, lifted = true) }
                }
            }
        }
    }
}

/** The three controls, the pause button, and the primary CTA. */
@Composable
internal fun GameControlSection() {
    CatalogSection(
        title = "Controls",
        description = "Hard shadows, no blur, and a press that moves the face down by the " +
            "shadow delta. The centre nudge button is a step quieter than the arrows on " +
            "purpose: it is an accelerator, not the main verb.",
    ) {
        OnBoard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D600)) {
                Box(Modifier.width(ControlSampleWidth)) {
                    GameControlRow(
                        onLeft = {},
                        onNudge = {},
                        onRight = {},
                        leftDescription = "Move left",
                        nudgeDescription = "Drop faster",
                        rightDescription = "Move right",
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimension.D600),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Captioned("pause") { PauseButton(onClick = {}, contentDescription = "Pause") }
                    Captioned("primary") { GamePrimaryButton(label = "Play", onClick = {}) }
                }
            }
        }
    }
}

/** The wordmark, the toast, and the three overlays. */
@Composable
internal fun GameOverlaySection() {
    CatalogSection(
        title = "Wordmark, toast and overlays",
        description = "All three overlays are the same scrim over a 6dp blur, clipped to the " +
            "board's own corner so the score and the controls stay visible behind them.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimension.D600)) {
            OnBoard { Wordmark() }
            OnBoard {
                CompositionLocalProvider(LocalBoardScale provides CatalogScale) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D900)) {
                        GameToast(text = "CHAIN ×3")
                        GameToast(text = "ROW BUST!")
                        GameToast(text = "LEVEL 4")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimension.D600)) {
                OverlaySample(OverlayKind.Start, "Start") {
                    Wordmark()
                    GamePrimaryButton(label = "Play", onClick = {})
                }
                OverlaySample(OverlayKind.Paused, "Paused") {
                    OverlayHeadline("Paused")
                    OverlayLabel("tap to resume")
                }
                OverlaySample(OverlayKind.GameOver, "Game over") {
                    OverlayHeadline("Stacked out")
                    OverlayLabel("score 12,340 · biggest 1024")
                }
            }
        }
    }
}

/** The two faces, at the sizes the design actually uses them. */
@Composable
internal fun GameTypeSection() {
    CatalogSection(
        title = "The two faces",
        description = "Fredoka for numerals, the wordmark and buttons. Nunito for labels and " +
            "body. Fredoka's figures are proportional — eight distinct digit widths and no " +
            "tnum — which is why a ticking score needs fixed-width slots.",
    ) {
        OnBoard {
            Column(verticalArrangement = Arrangement.spacedBy(Dimension.D400)) {
                SampleLine("Fredoka 700 · 32sp", FredokaSampleSize, FontWeight.Bold, true)
                SampleLine("1111  →  2222", FredokaSampleSize, FontWeight.Bold, true)
                SampleLine("Nunito 800 · label", LabelSampleSize, FontWeight.ExtraBold, false)
                SampleLine("1111  →  2222", FredokaSampleSize, FontWeight.ExtraBold, false)
            }
        }
    }
}

@Composable
private fun SampleLine(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, fredoka: Boolean) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = if (fredoka) FredokaFontFamily else NunitoFontFamily,
            fontWeight = weight,
            fontSize = size,
            color = GameColors.Ink,
        ),
    )
}

@Composable
private fun MiniBoard(danger: Boolean, caption: String) {
    Captioned(caption) {
        CompositionLocalProvider(LocalBoardScale provides MiniScale) {
            BoardWell(
                danger = danger,
                modifier = Modifier.width(MiniBoardWidth).aspectRatio(BoardColumns / BoardRows),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(MiniScale.gutter)) {
                    repeat(BoardRows.toInt()) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(MiniScale.gutter)) {
                            repeat(BoardColumns.toInt()) { column ->
                                val value = MiniBoardContents[row][column]
                                if (value == 0) {
                                    TileSlot(active = column == ActiveColumn)
                                } else {
                                    Tile(value = value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySample(
    kind: OverlayKind,
    caption: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Captioned(caption) {
        Box(
            modifier = Modifier
                .width(OverlaySampleWidth)
                .height(OverlaySampleHeight)
                .background(GameColors.BoardWell),
        ) {
            GameOverlay(kind = kind, modifier = Modifier.fillMaxWidth().height(OverlaySampleHeight)) {
                content()
            }
        }
    }
}

@Composable
private fun OverlayHeadline(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = FredokaFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = OverlayHeadlineSize,
            color = GameColors.Ink,
        ),
    )
}

@Composable
private fun OverlayLabel(text: String) {
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = OverlayLabelSize,
            color = GameColors.InkMuted,
        ),
    )
}

/** Anything drawn on the game's own dark surface, so it is judged against it. */
@Composable
private fun OnBoard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .background(GameColors.BackdropMid)
            .padding(Dimension.D700),
    ) { content() }
}

@Composable
private fun Captioned(caption: String, content: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimension.D200),
    ) {
        content()
        Text(
            text = caption,
            typography = AppTheme.typography.Caption.C200,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/** Big enough that the 22% radius, the shadow and the highlight are all legible on a page. */
private val CatalogScale = BoardScale(em = 22.dp, cell = 78.dp)

/** A whole 5×7 board at a size that fits beside a second one. */
private val MiniScale = BoardScale(em = 10.dp, cell = 34.dp)

private const val BoardColumns = 5f
private const val BoardRows = 7f
private const val ActiveColumn = 2
private const val TilesPerRow = 6

/**
 * A board mid-run: a stack with a gap the player is steering into, and one tier
 * of every temperature so the ramp is visible in situ rather than only as a strip.
 */
private val MiniBoardContents = listOf(
    listOf(0, 0, 0, 0, 0),
    listOf(0, 0, 0, 0, 0),
    listOf(0, 0, 0, 0, 0),
    listOf(0, 0, 0, 0, 2),
    listOf(4, 0, 0, 8, 16),
    listOf(32, 0, 64, 128, 256),
    listOf(512, 1024, 2048, 4, 2),
)

private val MiniBoardWidth = 240.dp
private val ControlSampleWidth = 370.dp
private val OverlaySampleWidth = 260.dp
private val OverlaySampleHeight = 300.dp
private val FredokaSampleSize = 32.sp
private val LabelSampleSize = 14.sp
private val OverlayHeadlineSize = 34.sp
private val OverlayLabelSize = 13.sp
