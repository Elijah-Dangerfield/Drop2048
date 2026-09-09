package com.dangerfield.drop2048.features.game.impl.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.game.impl.GameCallout
import com.dangerfield.drop2048.features.game.impl.GamePhase
import com.dangerfield.drop2048.features.game.impl.GameScreen
import com.dangerfield.drop2048.features.game.impl.GameUiState
import com.dangerfield.drop2048.features.game.impl.TutorialFocus
import com.dangerfield.drop2048.features.game.impl.TutorialFrame
import com.dangerfield.drop2048.features.game.impl.TutorialStep
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The whole game screen, at device size, in every state a still frame can hold.
 *
 * **This is the test the project has been missing.** C2c's harness covers the
 * pieces; nothing covered the composition of them, and every layout bug found so
 * far — the board overflowing its box, "Drop again" starting an invisible run
 * (L32), the HUD crowding the well — was a bug in how the pieces were *arranged*.
 * A golden of one tile could not have caught any of them.
 *
 * The set is chosen so that each image is the only one that would move for its
 * own concern: the resting board catches the header, the well and the control
 * row; the danger frame catches the ring and glow; the three overlays catch the
 * three scrims; and the four ghost frames catch the landing preview's four
 * shapes, which are the states most easily collapsed into one by accident because
 * three of them are undesigned.
 *
 * Two things are asserted only implicitly and are worth naming. The board is
 * bounded by height as well as width here, because [ShortPhoneHeight] is a real
 * small-phone frame and a board that ignored the height would push the control
 * row out of the image. And the screen is captured at a device size rather than
 * wrapped, because the layout under test *is* the arrangement.
 *
 * [GraphicsMode.Mode.NATIVE] is required rather than preferred: Robolectric's
 * legacy graphics stack draws nothing at all, so every golden would be a
 * correctly-sized transparent rectangle and every comparison would pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class GameScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playing() = compose.captureScreen("game-playing") {
        playingState()
    }

    @Test
    fun inDanger() = compose.captureScreen("game-danger") {
        playingState().copy(
            board = dangerBoard(),
            falling = FallingBlock(NumberBlock(BlockValue.V32), Cell(1, 1)),
            ghost = Cell(1, 2),
            inDanger = true,
        )
    }

    @Test
    fun callout() = compose.captureScreen("game-callout") {
        playingState().copy(
            phase = GamePhase.Resolving,
            falling = null,
            ghost = null,
            callout = GameCallout.Chain(step = 3),
            calloutNonce = 1,
        )
    }

    @Test
    fun start() = compose.captureScreen("game-start") {
        playingState().copy(phase = GamePhase.Ready, score = 0, falling = null, ghost = null)
    }

    @Test
    fun paused() = compose.captureScreen("game-paused") {
        playingState().copy(phase = GamePhase.Paused)
    }

    @Test
    fun stackedOut() = compose.captureScreen("game-stacked-out") {
        playingState().copy(
            phase = GamePhase.StackedOut,
            board = dangerBoard(),
            falling = null,
            ghost = null,
            biggestTier = 512,
            newBest = true,
        )
    }

    /**
     * The four landing previews, one per test because
     * `ComposeContentTestRule.setContent` may only be called once per rule.
     *
     * A value block over its twin is the design's own will-merge state. The other
     * three are this chunk's rulings and are the whole reason these exist: a
     * Stone that never merges, a Wildcard whose bright cell is the *neighbour*
     * that doubles, and a Bomb whose five-cell footprint carries no label at all.
     */
    @Test
    fun ghostWillMerge() = compose.captureScreen("game-ghost-merge") {
        ghostState(NumberBlock(BlockValue.V8))
    }

    @Test
    fun ghostStone() = compose.captureScreen("game-ghost-stone") {
        ghostState(SpecialBlock(Special.STONE))
    }

    @Test
    fun ghostWildcard() = compose.captureScreen("game-ghost-wildcard") {
        ghostState(SpecialBlock(Special.WILDCARD))
    }

    @Test
    fun ghostBomb() = compose.captureScreen("game-ghost-bomb") {
        ghostState(SpecialBlock(Special.BOMB))
    }

    @Test
    fun leftHanded() = compose.captureScreen("game-left-handed") {
        playingState().copy(leftHanded = true)
    }

    /**
     * The guided run (SPEC 13), in the two shapes its scrim has.
     *
     * The spotlight one is the more valuable of the two. Its hole is punched with
     * `BlendMode.Clear` into an offscreen layer, which is the kind of drawing
     * that fails by producing a plausible-looking rectangle in the wrong place,
     * and no test that is not a picture can tell.
     */
    @Test
    fun tutorialCoachMark() = compose.captureScreen("tutorial-coach") {
        tutorialState(TutorialStep.FirstNudge, TutorialFocus.Nudge, awaitsTap = false)
    }

    @Test
    fun tutorialSpotlight() = compose.captureScreen("tutorial-spotlight") {
        tutorialState(TutorialStep.Steer, TutorialFocus.Board, awaitsTap = false).copy(
            board = Board.empty(COLS, ROWS).with(Cell(1, 7), NumberBlock(BlockValue.V2)),
            falling = FallingBlock(NumberBlock(BlockValue.V2), Cell(2, 0)),
            ghost = Cell(2, 7),
            score = 0,
            level = 1,
            levelFraction = 0f,
            biggestTier = 2,
        )
    }

    /** Drop six: the 2048 has just cleared its row (SPEC 13 step 4). */
    @Test
    fun tutorialBurst() = compose.captureScreen("tutorial-burst") {
        playingState().copy(
            phase = GamePhase.Resolving,
            board = Board.empty(COLS, ROWS),
            falling = null,
            ghost = null,
            score = 12_500,
            biggestTier = 2048,
            callout = GameCallout.RowBust,
            calloutNonce = 1,
        )
    }
}

/**
 * Capture the screen for [state] as `<name>.png`.
 *
 * [LocalInspectionMode] is forced on for the whole tree, and it is the single
 * most important line in this file. Every looping animation the screen draws —
 * the ghost pulse, the wordmark rock, the toast — checks it and holds a fixed
 * value. Without it Compose never goes idle, the capture waits for an idle that
 * never arrives, and the build **hangs** rather than failing: no error, no
 * timeout, and no clue which component did it.
 */
private fun ComposeContentTestRule.captureScreen(
    name: String,
    state: () -> GameUiState,
) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(
                    modifier = Modifier
                        .width(ShortPhoneWidth)
                        .height(ShortPhoneHeight)
                        .testTag(CaptureTag),
                ) {
                    GameScreen(state = state(), onAction = {})
                }
            }
        }
    }
    onNodeWithTag(CaptureTag).captureRoboImage(File(GoldenDirectory, "$name.png"))
}

/**
 * A tutorial beat over a mid-tutorial board. The frame is what the screen draws
 * the scrim and the card from; the copy itself is resolved from [TutorialStep].
 */
private fun tutorialState(
    step: TutorialStep,
    focus: TutorialFocus,
    awaitsTap: Boolean,
) = playingState().copy(
    board = Board.empty(COLS, ROWS).with(Cell(1, 7), NumberBlock(BlockValue.V4)),
    falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(2, 2)),
    ghost = Cell(2, 7),
    score = 12,
    best = 0,
    level = 1,
    levelFraction = 0f,
    biggestTier = 4,
    tutorial = TutorialFrame(
        step = step,
        focus = focus,
        speaks = true,
        awaitsTap = awaitsTap,
        canSkip = false,
    ),
)

private fun playingState() = GameUiState(
    board = restingBoard(),
    falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(2, 2)),
    ghost = Cell(2, 5),
    phase = GamePhase.Playing,
    score = 4_896,
    best = 130_450,
    level = 7,
    levelFraction = 0.4f,
    biggestTier = 256,
)

private fun restingBoard(): Board = Board.empty(COLS, ROWS)
    .with(Cell(0, 7), NumberBlock(BlockValue.V32))
    .with(Cell(1, 7), NumberBlock(BlockValue.V4))
    .with(Cell(2, 7), NumberBlock(BlockValue.V128))
    .with(Cell(3, 7), NumberBlock(BlockValue.V1024))
    .with(Cell(4, 7), NumberBlock(BlockValue.V2))
    .with(Cell(0, 6), NumberBlock(BlockValue.V8))
    .with(Cell(3, 6), NumberBlock(BlockValue.V16))
    .with(Cell(4, 6), SpecialBlock(Special.STONE))

private fun dangerBoard(): Board = restingBoard()
    .with(Cell(0, 5), NumberBlock(BlockValue.V64))
    .with(Cell(0, 4), NumberBlock(BlockValue.V2))
    .with(Cell(0, 3), NumberBlock(BlockValue.V512))
    .with(Cell(0, 2), NumberBlock(BlockValue.V4))
    .with(Cell(0, 1), SpecialBlock(Special.BOMB))

/**
 * A board arranged so every landing preview has something to talk about: an 8 to
 * merge with, a Wildcard's neighbour to double, and a Bomb's neighbours to clear.
 */
private fun ghostState(falling: Block) = playingState().copy(
    board = Board.empty(COLS, ROWS)
        .with(Cell(2, 7), NumberBlock(BlockValue.V8))
        .with(Cell(1, 7), NumberBlock(BlockValue.V16))
        .with(Cell(3, 7), NumberBlock(BlockValue.V4))
        .with(Cell(2, 6), null),
    falling = FallingBlock(falling, Cell(2, 2)),
    ghost = Cell(2, 6),
)

private const val COLS = 5
private const val ROWS = 8

/**
 * Committed, and deliberately outside `build/`. A golden that lives in a build
 * directory disappears on a clean and cannot be reviewed in a pull request, which
 * removes both of the things it was for.
 */
private const val GoldenDirectory = "screenshots"

private const val CaptureTag = "capture"

/**
 * A small phone rather than a generous one, on purpose. The design's board is
 * hardcoded at `max-width: 370px` for 5x7; at 5x8 it is a whole row taller, and
 * the frame where that costs something is the short one. Capturing on a tall
 * device would make the golden agree with the design and disagree with a real
 * player's phone.
 */
private val ShortPhoneWidth = 360.dp
private val ShortPhoneHeight = 640.dp

/**
 * The SDK Robolectric renders against, pinned rather than tracking `compileSdk`.
 * A golden is a pixel comparison and a platform bump moves text metrics, so the
 * SDK moving should be a deliberate re-record with a diff to look at.
 */
internal const val ROBOLECTRIC_SDK = 34

/** A fixed density, for the same reason. `xhdpi` is a whole number of pixels per dp. */
internal const val ROBOLECTRIC_QUALIFIERS = "w360dp-h640dp-xhdpi"
