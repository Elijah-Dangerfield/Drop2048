package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.game.impl.screenshot.ROBOLECTRIC_QUALIFIERS
import com.dangerfield.drop2048.features.game.impl.screenshot.ROBOLECTRIC_SDK
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The overlay menu is reachable, one option at a time.
 *
 * ### The bug this exists for
 *
 * The paused overlay's "tap anywhere to resume" used to be a `Modifier.clickable`
 * on the container that holds the menu. `clickable` merges the semantics of
 * everything below it, so the whole overlay collapsed into a single node carrying
 * all of its text and exactly one action: resume. Asking for `Settings` resumed
 * the game instead, every time, on every path that addresses the UI through
 * semantics rather than through raw screen coordinates: TalkBack, Voice Access,
 * Switch Access, and every UI test. The start overlay, which has no dismiss tap,
 * offers the same options and had none of the problem, which is what made the
 * failure look like "the resume click wins".
 *
 * These tests click **through the merged tree** (`useUnmergedTree` left at its
 * default) for exactly that reason. The unmerged tree never had the bug, so a
 * test that opted out of merging would have passed against the broken code.
 *
 * ### What is covered
 *
 * Every option the two overlays offer, on both overlays, plus the dismiss tap
 * itself, because "no option ever resumes the game" is trivially satisfiable by
 * deleting tap-to-resume and [tappingTheHeadlineResumes] is what stops that being
 * the fix.
 *
 * ### What is not covered here
 *
 * Where each option *goes*: `GameViewModel` owns the action-to-effect mapping and
 * its own tests own that. This file asserts only which action the tap produced.
 * Pixels belong to `GameScreenScreenshotTest`, and the moment an option is
 * dropped from one overlay and not the other is caught there, by the `game-start`
 * and `game-paused` goldens.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class OverlayMenuTapTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsOnThePausedOverlayOpensSettings() {
        val actions = compose.overlay(GamePhase.Paused)

        compose.onNodeWithText("Settings").performClick()

        assertTrue(GameAction.OpenSettings in actions, "was $actions")
        assertFalse(GameAction.Resume in actions, "was $actions")
    }

    @Test
    fun statsOnThePausedOverlayOpensStats() {
        val actions = compose.overlay(GamePhase.Paused)

        compose.onNodeWithText("Stats").performClick()

        assertTrue(GameAction.ShowStats in actions, "was $actions")
        assertFalse(GameAction.Resume in actions, "was $actions")
    }

    @Test
    fun quitOnThePausedOverlayQuits() {
        val actions = compose.overlay(GamePhase.Paused)

        compose.onNodeWithText("Quit").performClick()

        assertTrue(GameAction.Quit in actions, "was $actions")
        assertFalse(GameAction.Resume in actions, "was $actions")
    }

    @Test
    fun restartOnThePausedOverlayRestarts() {
        val actions = compose.overlay(GamePhase.Paused)

        compose.onNodeWithText("Restart").performClick()

        assertTrue(GameAction.Restart in actions, "was $actions")
        assertFalse(GameAction.Resume in actions, "was $actions")
    }

    /**
     * The overlay is still dismissible by tapping it, and the headline is a fair
     * proxy for "anywhere that is not an option": it takes no pointer input of
     * its own, so the tap can only have reached the scrim behind it.
     */
    @Test
    fun tappingTheHeadlineResumes() {
        val actions = compose.overlay(GamePhase.Paused)

        compose.onNodeWithText("Paused").performClick()

        assertTrue(GameAction.Resume in actions, "was $actions")
    }

    /**
     * Every option is a button with an action of its own, which is the same
     * claim [settingsOnThePausedOverlayOpensSettings] makes about behaviour,
     * made about the tree an assistive technology reads instead.
     */
    @Test
    fun everyOptionIsItsOwnButton() {
        compose.overlay(GamePhase.Paused)

        Options.forEach { option ->
            compose.onNodeWithText(option)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assertHasClickAction()
        }
    }

    /**
     * No option's touch target reaches into its neighbour's.
     *
     * `GameQuietButton` grows its touch area past what it draws, which is the
     * only way to get near the 48dp floor without moving the copy. The price is
     * that two options can end up claiming the same pixel, and Compose settles
     * that by draw order rather than by which label is nearer, so the row drawn
     * second would silently take the whole contested band and a tap just under
     * `Quit` would open `Stats`. `OverlayOption` caps the growth at half the gap
     * to stop it; this is what proves the cap is still the right number.
     *
     * Targets that *meet* are fine and expected: bounds are half-open, so a
     * pixel on the seam belongs to exactly one of them.
     */
    @Test
    fun noTwoOptionsClaimTheSamePixel() {
        compose.overlay(GamePhase.Paused)

        val targets = Options.associateWith {
            compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot
        }

        targets.entries.forEachIndexed { index, (name, rect) ->
            targets.entries.drop(index + 1).forEach { (otherName, other) ->
                assertFalse(
                    rect.overlaps(other),
                    "$name at $rect overlaps $otherName at $other",
                )
            }
        }
    }

    @Test
    fun settingsOnTheStartOverlayOpensSettings() {
        val actions = compose.overlay(GamePhase.Ready)

        compose.onNodeWithText("Settings").performClick()

        assertTrue(GameAction.OpenSettings in actions, "was $actions")
        assertFalse(GameAction.Start in actions, "was $actions")
    }

    @Test
    fun statsOnTheStartOverlayOpensStats() {
        val actions = compose.overlay(GamePhase.Ready)

        compose.onNodeWithText("Stats").performClick()

        assertTrue(GameAction.ShowStats in actions, "was $actions")
        assertFalse(GameAction.Start in actions, "was $actions")
    }
}

/**
 * The real screen at a small-phone size, with the overlay for [phase] up.
 *
 * [LocalInspectionMode] is forced on for the same reason the screenshot harness
 * forces it: the looping animations the screen draws never let Compose go idle,
 * and a test that waits for idle would hang rather than fail.
 */
private fun ComposeContentTestRule.overlay(phase: GamePhase): List<GameAction> {
    val actions = mutableListOf<GameAction>()
    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(modifier = Modifier.width(PhoneWidth).height(PhoneHeight)) {
                    GameScreen(state = overlayState(phase), onAction = { actions += it })
                }
            }
        }
    }
    return actions
}

private fun overlayState(phase: GamePhase) = GameUiState(
    board = Board.empty(Cols, Rows).with(Cell(0, Rows - 1), NumberBlock(BlockValue.V4)),
    phase = phase,
    score = 4_896,
    best = 130_450,
    level = 7,
    levelFraction = 0.4f,
    biggestTier = 256,
)

/** Every option the paused overlay offers, in the order it draws them. */
private val Options = listOf("Restart", "Quit", "Stats", "Settings")

private const val Cols = 5
private const val Rows = 8
private val PhoneWidth = 360.dp
private val PhoneHeight = 640.dp
