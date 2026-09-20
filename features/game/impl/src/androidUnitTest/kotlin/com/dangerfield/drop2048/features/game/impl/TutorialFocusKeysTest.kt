package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.dangerfield.drop2048.libraries.ui.system.FocusRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Every spotlight the script asks for points at something the screen draws.
 *
 * ### The bug this is the general form of
 *
 * `TutorialFocus.Drop` resolved to `DropFocusKey`, and that key is registered by
 * the arrow row alone. Under `ControlScheme.Drag` there is no arrow row, so the
 * key was never registered: `FocusScrim` found no rectangle, punched no hole in
 * its own dim layer, and handed the coach mark `Rect.Zero` to hang off. Three
 * beats of the guided run — the ones whose entire job is to point at the drop
 * control — pointed at the top-left corner of a uniformly dimmed screen.
 *
 * Nothing failed. The tutorial still completes, because the downward flick still
 * works, which is exactly why it survived from C5 to the round that made drag the
 * default. It was reachable the whole time: set Drag in settings, replay the
 * tutorial.
 *
 * ### Why it is written this way
 *
 * The two halves that disagreed were "what the script asks for" and "what the
 * screen registers", and any test that restates either of them in its own words
 * can agree with itself while the app is broken. So this asks the script
 * ([Tutorial.Script] through `spotlightKeys`) and renders the real [GameScreen],
 * then reads the registry the screen filled in. A focus that resolves to a key
 * some scheme never draws fails here, whatever the focus or the scheme is.
 *
 * One test per scheme because a `ComposeContentTestRule` takes one `setContent`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class TutorialFocusKeysTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dragRegistersEveryKeyTheScriptAsksFor() = compose.assertScriptCanPointAtSomething(ControlScheme.Drag)

    @Test
    fun buttonsRegisterEveryKeyTheScriptAsksFor() = compose.assertScriptCanPointAtSomething(ControlScheme.Buttons)

    @Test
    fun bothRegistersEveryKeyTheScriptAsksFor() = compose.assertScriptCanPointAtSomething(ControlScheme.Both)
}

private fun ComposeContentTestRule.assertScriptCanPointAtSomething(scheme: ControlScheme) {
    val registry = FocusRegistry()
    val state = tutorialState(scheme)

    setContent {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PreviewContent {
                Box(modifier = Modifier.width(PhoneWidth).height(PhoneHeight)) {
                    GameScreen(state = state, onAction = {}, focusRegistry = registry)
                }
            }
        }
    }
    waitForIdle()

    Tutorial.Script.forEach { lesson ->
        val keys = lesson.focus.spotlightKeys(state.arrowsOnScreen)
        if (lesson.focus != TutorialFocus.None) {
            assertTrue(keys.isNotEmpty(), "${lesson.step} lights nothing under $scheme")
        }
        keys.forEach { key ->
            assertTrue(
                registry.registered(key),
                "${lesson.step} spotlights $key, which $scheme never puts on screen",
            )
        }
    }
}

/**
 * A mid-tutorial board, played in [scheme], carrying an outlined cell.
 *
 * The step on the frame does not matter: what registers a key is the screen's
 * layout, and that is what is being checked. The outline does have to be present
 * — it is drawn only while a beat asks for one — so this is the frame the
 * opening beat produces, which is the only frame under which every key in the
 * script is reachable at once.
 */
private fun tutorialState(scheme: ControlScheme) = GameUiState(
    board = Board.empty(Cols, Rows).with(Cell(1, Rows - 1), NumberBlock(BlockValue.V4)),
    falling = FallingBlock(NumberBlock(BlockValue.V4), Cell(Cols - 1, 2)),
    ghost = Cell(Cols - 1, Rows - 1),
    phase = GamePhase.Playing,
    controlScheme = scheme,
    tutorial = TutorialFrame(
        step = TutorialStep.Steer,
        focus = TutorialFocus.Target,
        speaks = true,
        awaitsTap = false,
        canSkip = false,
        target = Cell(2, Rows - 1),
    ),
)

private const val Cols = 5
private const val Rows = 8

private val PhoneWidth = 360.dp
private val PhoneHeight = 640.dp
