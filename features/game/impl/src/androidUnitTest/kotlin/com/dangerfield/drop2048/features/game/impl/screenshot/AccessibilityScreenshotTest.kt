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
import com.dangerfield.drop2048.features.game.impl.GamePhase
import com.dangerfield.drop2048.features.game.impl.GameScreen
import com.dangerfield.drop2048.features.game.impl.GameUiState
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The board as a player who uses the accessibility settings sees it.
 *
 * **These are the goldens that prove the wiring, and they are new in C11 because
 * until C11 there was nothing to wire.** C2 authored five palettes and a
 * large-numbers scale, `AppThemeProvider` took all of them, and `App.kt` called
 * it on defaults for six chunks — so every golden in this project was of the
 * default ramp, and would have stayed green forever if the other four ramps had
 * been deleted.
 *
 * `AccessibilitySettingsReachTheUiTest` proves the store reaches the
 * CompositionLocals. These prove the CompositionLocals reach the pixels, which is
 * the half no assertion can make: "the block face is a different colour" is a
 * claim about what was drawn.
 *
 * Reduce motion is captured too, and it is the weakest of the four on purpose —
 * a still frame cannot show a shorter animation. What it *can* show is that
 * `LocalInspectionMode` and reduce motion do not fight: the ghost, the toast and
 * the score counter all hold a fixed value under both, and a frame that differed
 * here would mean one of them reads the setting at a different point in its
 * animation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class AccessibilityScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun highContrastBoard() = compose.captureAccessible(
        name = "a11y-high-contrast",
        palette = BlockPaletteChoice.HighContrast,
    )

    @Test
    fun deuteranopiaBoard() = compose.captureAccessible(
        name = "a11y-deuteranopia",
        palette = BlockPaletteChoice.Deuteranopia,
    )

    @Test
    fun protanopiaBoard() = compose.captureAccessible(
        name = "a11y-protanopia",
        palette = BlockPaletteChoice.Protanopia,
    )

    @Test
    fun tritanopiaBoard() = compose.captureAccessible(
        name = "a11y-tritanopia",
        palette = BlockPaletteChoice.Tritanopia,
    )

    @Test
    fun largeBlockNumbers() = compose.captureAccessible(
        name = "a11y-large-numbers",
        largeNumbers = true,
    )

    @Test
    fun reducedMotion() = compose.captureAccessible(
        name = "a11y-reduce-motion",
        reduceMotion = true,
    )

    /** Both at once, which is the combination the settings screen makes easiest to reach. */
    @Test
    fun highContrastWithLargeNumbers() = compose.captureAccessible(
        name = "a11y-high-contrast-large",
        palette = BlockPaletteChoice.HighContrast,
        largeNumbers = true,
    )
}

/**
 * The same board every time, under a different accessibility setting.
 *
 * One board on purpose: the only thing that may differ between these images is
 * the setting, so a diff is attributable to it and nothing else. The board is
 * loaded with widely-separated tiers *and* two adjacent ones (16 beside 32),
 * because adjacent tiers are the pair a palette has to keep apart and the pair
 * the default ramp is measured worst on (D14).
 */
private fun ComposeContentTestRule.captureAccessible(
    name: String,
    palette: BlockPaletteChoice = BlockPaletteChoice.Default,
    reduceMotion: Boolean = false,
    largeNumbers: Boolean = false,
) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent(
                palette = palette,
                reduceMotion = reduceMotion,
                largeNumbers = largeNumbers,
            ) {
                Box(
                    modifier = Modifier
                        .width(ShortPhoneWidth)
                        .height(ShortPhoneHeight)
                        .testTag(CaptureTag),
                ) {
                    GameScreen(state = accessibilityState(), onAction = {})
                }
            }
        }
    }
    onNodeWithTag(CaptureTag).captureRoboImage(File(GoldenDirectory, "$name.png"))
}

private fun accessibilityState() = GameUiState(
    board = Board.empty(COLS, ROWS)
        .with(Cell(0, 7), NumberBlock(BlockValue.V2))
        .with(Cell(1, 7), NumberBlock(BlockValue.V16))
        .with(Cell(2, 7), NumberBlock(BlockValue.V32))
        .with(Cell(3, 7), NumberBlock(BlockValue.V256))
        .with(Cell(4, 7), NumberBlock(BlockValue.V2048))
        .with(Cell(0, 6), NumberBlock(BlockValue.V4))
        .with(Cell(1, 6), NumberBlock(BlockValue.V8))
        .with(Cell(2, 6), NumberBlock(BlockValue.V64))
        .with(Cell(3, 6), NumberBlock(BlockValue.V128))
        .with(Cell(4, 6), SpecialBlock(Special.STONE))
        .with(Cell(1, 5), NumberBlock(BlockValue.V512))
        .with(Cell(2, 5), NumberBlock(BlockValue.V1024)),
    falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(2, 2)),
    ghost = Cell(2, 4),
    phase = GamePhase.Playing,
    score = 4_896,
    best = 130_450,
    level = 7,
    levelFraction = 0.4f,
    biggestTier = 2048,
)

private const val COLS = 5
private const val ROWS = 8

private const val GoldenDirectory = "screenshots"
private const val CaptureTag = "capture"

/** L45: the tightest frame the app ships to, not the roomiest. */
private val ShortPhoneWidth = 360.dp
private val ShortPhoneHeight = 640.dp
