package com.dangerfield.drop2048.libraries.ui.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.components.game.BoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.BoardWell
import com.dangerfield.drop2048.libraries.ui.components.game.CoachMark
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
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.TIER_VALUES
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A golden for every piece of the design the handoff specifies.
 *
 * The set is chosen so that a change to any one *token* moves at least one image.
 * The tile ramp catches the palette, the well catches the ring and the inner
 * shadow, the controls catch the shadow depths and the quiet centre button, the
 * overlays catch the three scrims and the blur, and the ghost catches the two
 * states that are easiest to collapse into one by accident.
 *
 * [GraphicsMode.Mode.NATIVE] is required rather than preferred: Robolectric's
 * legacy graphics stack draws nothing at all, so every golden would be a
 * correctly-sized transparent rectangle and every comparison would pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class GameSurfaceScreenshotTest : ScreenshotTest() {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The coach mark, in both placements it has.
     *
     * The flip is the whole reason this has a golden. A card that always sits
     * below its anchor runs off the bottom of the screen the moment a lesson
     * points at the control row, which is where most of them point, and nothing
     * that is not a picture notices.
     */
    @Test
    fun coachMarkBelowAnchor() = compose.capture("coach-mark") {
        CoachMarkFrame(anchor = Rect(60f, 180f, 300f, 260f))
    }

    @Test
    fun coachMarkFlippedAboveAnchor() = compose.capture("coach-mark-flipped") {
        CoachMarkFrame(anchor = Rect(60f, 1180f, 300f, 1260f))
    }

    @Test
    fun tileRamp() = compose.capture("tile-ramp") {
        CompositionLocalProvider(LocalBoardScale provides CaptureScale) {
            TileGrid()
        }
    }

    /**
     * The same eleven tiles in each accessibility palette.
     *
     * One test per palette rather than one test with a loop, and not by choice:
     * `ComposeContentTestRule.setContent` can be called once per rule, so a loop
     * over four palettes fails on the second. Worth every byte regardless — this
     * is the only place the ramps are *looked at* rather than measured, and
     * `BlockPaletteTest` proves separations while proving nothing about whether
     * the result is a board anybody wants to look at.
     */
    @Test
    fun tileRampDeuteranopia() = captureRamp(BlockPaletteChoice.Deuteranopia)

    @Test
    fun tileRampProtanopia() = captureRamp(BlockPaletteChoice.Protanopia)

    @Test
    fun tileRampTritanopia() = captureRamp(BlockPaletteChoice.Tritanopia)

    @Test
    fun tileRampHighContrast() = captureRamp(BlockPaletteChoice.HighContrast)

    private fun captureRamp(choice: BlockPaletteChoice) =
        compose.capture("tile-ramp-${choice.name.lowercase()}", palette = choice) {
            CompositionLocalProvider(LocalBoardScale provides CaptureScale) {
                TileGrid()
            }
        }

    @Test
    fun boardResting() = compose.capture("board-resting") { MiniBoard(danger = false) }

    @Test
    fun boardInDanger() = compose.capture("board-danger") { MiniBoard(danger = true) }

    @Test
    fun cells() = compose.capture("cells") {
        CompositionLocalProvider(LocalBoardScale provides CaptureScale) {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                TileSlot()
                TileSlot(active = true)
                LandingGhost(willMerge = false)
                LandingGhost(willMerge = true)
                Tile(value = 8, lifted = true)
            }
        }
    }

    @Test
    fun controls() = compose.capture("controls") {
        Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
            Box(Modifier.width(ControlWidth)) {
                GameControlRow(
                    onLeft = {},
                    onNudge = {},
                    onRight = {},
                    leftDescription = "Move left",
                    nudgeDescription = "Drop faster",
                    rightDescription = "Move right",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                PauseButton(onClick = {}, contentDescription = "Pause")
                GamePrimaryButton(label = "Play", onClick = {})
            }
        }
    }

    @Test
    fun wordmark() = compose.capture("wordmark") { Wordmark() }

    @Test
    fun toasts() = compose.capture("toasts") {
        CompositionLocalProvider(LocalBoardScale provides CaptureScale) {
            Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
                GameToast(text = "CHAIN ×3")
                GameToast(text = "ROW BUST!")
                GameToast(text = "LEVEL 4")
            }
        }
    }

    @Test
    fun overlayStart() = captureOverlay(OverlayKind.Start)

    @Test
    fun overlayPaused() = captureOverlay(OverlayKind.Paused)

    @Test
    fun overlayGameOver() = captureOverlay(OverlayKind.GameOver)

    private fun captureOverlay(kind: OverlayKind) =
        compose.capture("overlay-${kind.name.lowercase()}") {
            Box(Modifier.width(OverlayWidth).height(OverlayHeight)) {
                GameOverlay(
                    kind = kind,
                    modifier = Modifier.width(OverlayWidth).height(OverlayHeight),
                    behind = { MiniBoard(danger = false) },
                ) {
                    Wordmark()
                    GamePrimaryButton(label = "Play", onClick = {})
                }
            }
        }

    /**
     * Reduce motion, captured on its own.
     *
     * The setting changes what a still frame looks like — the wordmark stops
     * rocking, so it is drawn upright rather than at -3° — which makes it the one
     * accessibility setting a screenshot can actually assert. It is also the
     * setting most likely to be broken by someone adding an animation and
     * forgetting D4.
     */
    @Test
    fun wordmarkWithReducedMotion() =
        compose.capture("wordmark-reduce-motion", reduceMotion = true) { Wordmark() }
}

@androidx.compose.runtime.Composable
private fun TileGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        TIER_VALUES.chunked(TilesPerRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                row.forEach { Tile(value = it) }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MiniBoard(danger: Boolean) {
    CompositionLocalProvider(LocalBoardScale provides MiniScale) {
        BoardWell(danger = danger, modifier = Modifier.width(MiniBoardWidth)) {
            Column(verticalArrangement = Arrangement.spacedBy(MiniScale.gutter)) {
                MiniBoardContents.forEachIndexed { row, values ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MiniScale.gutter)) {
                        values.forEachIndexed { column, value ->
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

/**
 * The SDK Robolectric renders against, and it is pinned rather than tracking
 * `compileSdk`. A golden is a pixel comparison, and a platform bump moves text
 * metrics — so the SDK moving should be a deliberate re-record with a diff to
 * look at, not something that silently reddens the build the day the module
 * upgrades.
 */
internal const val ROBOLECTRIC_SDK = 34

/**
 * A fixed density and size, for the same reason. `xhdpi` is 2x, which is a whole
 * number of pixels per dp and so has no rounding to argue about.
 */
internal const val ROBOLECTRIC_QUALIFIERS = "w900dp-h1600dp-xhdpi"

private val CaptureScale = BoardScale(em = 22.dp, cell = 78.dp)
private val MiniScale = BoardScale(em = 10.dp, cell = 34.dp)
private val Gap = 8.dp
private val MiniBoardWidth = 240.dp
private val ControlWidth = 370.dp
private val OverlayWidth = 260.dp
private val OverlayHeight = 300.dp
private const val TilesPerRow = 4
private const val ActiveColumn = 2

private val MiniBoardContents = listOf(
    listOf(0, 0, 0, 0, 0),
    listOf(0, 0, 0, 0, 0),
    listOf(0, 0, 0, 0, 0),
    listOf(0, 0, 0, 0, 2),
    listOf(4, 0, 0, 8, 16),
    listOf(32, 0, 64, 128, 256),
    listOf(512, 1024, 2048, 4, 2),
)


/**
 * The card needs a frame to place itself in, because placement is the thing
 * under test. The size is the harness's own short phone.
 */
@Composable
private fun CoachMarkFrame(anchor: Rect) {
    Box(modifier = Modifier.width(CoachFrameWidth).height(CoachFrameHeight)) {
        CoachMark(
            anchor = anchor,
            title = "Bring it down",
            body = "Nothing falls on its own yet. Tap the arrow.",
            confirmLabel = "Got it",
            skipLabel = "Skip tutorial",
        )
    }
}

private val CoachFrameWidth = 328.dp
private val CoachFrameHeight = 560.dp
