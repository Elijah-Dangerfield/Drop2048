package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.game.impl.screenshot.ROBOLECTRIC_QUALIFIERS
import com.dangerfield.drop2048.features.game.impl.screenshot.ROBOLECTRIC_SDK
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The downward flick, which under the default scheme is the only way to put a
 * block down.
 *
 * ### The case in the middle is the one this test exists for
 *
 * "Slide it over, then send it down, without lifting" is the gesture the game is
 * built on and the gesture the tutorial's first drop asks for by name. It could
 * not be performed. The flick's two thresholds — 30dp of travel, released within
 * 450ms — were measured from the moment the finger landed rather than from the
 * downward stroke, so steering first spent the window, and steering more than
 * 30dp sideways made `dy > abs(dx)` unreachable however hard the player flicked.
 *
 * Nothing about the thresholds changed. What changed is where they are measured
 * from, which is why the first and last cases are here too: a straight flick
 * still drops, and a slow finger still does not.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class BoardFlickTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aStraightFlickDrops() {
        var flicks = 0
        compose.board(onFlickDown = { flicks++ })

        compose.onNodeWithTag(BoardTag).performTouchInput {
            down(Offset(CentreX, StartY))
            advanceEventTime(QuickMillis)
            moveTo(Offset(CentreX, StartY + FlickTravel))
            up()
        }

        assertEquals(1, flicks, "a downward flick is a hard drop")
    }

    @Test
    fun aSlideFollowedByAFlickDrops() {
        var flicks = 0
        val columns = mutableListOf<Int>()
        compose.board(onSteerTo = { columns += it }, onFlickDown = { flicks++ })

        compose.onNodeWithTag(BoardTag).performTouchInput {
            down(Offset(CentreX, StartY))
            repeat(SlideSteps) { step ->
                advanceEventTime(SlowMillis)
                moveTo(Offset(CentreX + SlideStep * (step + 1), StartY))
            }
            advanceEventTime(QuickMillis)
            moveTo(Offset(CentreX + SlideStep * SlideSteps, StartY + FlickTravel))
            up()
        }

        assertTrue(columns.isNotEmpty(), "the slide steered the block")
        assertEquals(1, flicks, "and the flick at the end of it still dropped")
    }

    @Test
    fun aSlowDragDownDoesNotDrop() {
        var flicks = 0
        compose.board(onFlickDown = { flicks++ })

        compose.onNodeWithTag(BoardTag).performTouchInput {
            down(Offset(CentreX, StartY))
            advanceEventTime(PastTheWindowMillis)
            moveTo(Offset(CentreX, StartY + FlickTravel))
            up()
        }

        assertEquals(0, flicks, "a finger walked down the board is not a flick")
    }
}

private fun ComposeContentTestRule.board(
    onSteerTo: (Int) -> Unit = {},
    onFlickDown: () -> Unit = {},
) {
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(modifier = Modifier.width(BoardWidth).testTag(BoardTag)) {
                    GameBoard(
                        state = GameUiState(
                            board = Board.empty(Cols, Rows),
                            falling = FallingBlock(NumberBlock(BlockValue.V2), Cell(2, 0)),
                            phase = GamePhase.Playing,
                            controlScheme = ControlScheme.Drag,
                        ),
                        boardDescription = "board",
                        fallingDescription = null,
                        targetDescription = null,
                        calloutText = null,
                        onSteerTo = onSteerTo,
                        onFlickDown = onFlickDown,
                    )
                }
            }
        }
    }
}

private const val Cols = 5
private const val Rows = 8

private val BoardWidth = 350.dp
private const val BoardTag = "board"

/**
 * Pixels, because that is what `performTouchInput` speaks. The qualifiers pin
 * `xhdpi`, so one dp is two of these, and a cell on a 350dp board is 140.
 */
private const val CentreX = 175f
private const val StartY = 200f

/** Past the 30dp threshold, in the same pixels. */
private const val FlickTravel = 80f

/** Most of a column, three times, which is what makes `abs(dx)` the old killer. */
private const val SlideStep = 120f
private const val SlideSteps = 3

private const val QuickMillis = 40L
private const val SlowMillis = 200L

/** Comfortably past the 450ms window. */
private const val PastTheWindowMillis = 700L
