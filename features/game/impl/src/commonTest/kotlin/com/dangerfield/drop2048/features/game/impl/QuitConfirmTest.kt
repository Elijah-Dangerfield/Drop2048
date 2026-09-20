package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Quitting a run always asks first (owner ruling, 2026-09-20).
 *
 * This is a **behaviour change**, not a removed option, which is why it has a
 * file. `confirmBeforeQuit` was a stored setting and `quit()` branched on it, so
 * deleting the field could have landed either way — the branch that survived a
 * careless removal is `leaveRun()`, and that would have made Quit destroy a run
 * with no question at all. The tests below are what makes the surviving
 * behaviour the deliberate one.
 *
 * **Not covered here:** what Quit does once confirmed. `leaveRun` returning to
 * the start overlay rather than popping the backstack is D12's, asserted where
 * the rest of the phase machine is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuitConfirmTest : CoroutineTest() {

    @Test
    fun quit_fromThePauseOverlay_alwaysAsksFirst() = runUnitTest {
        playing {
            act(GameAction.Pause, GameAction.Quit)

            assertTrue(state.confirmingQuit, "Quit must ask before it ends a run")
            assertPhase(GamePhase.Paused)
        }
    }

    /**
     * The negative half (L35). A dialog that is on screen from the first frame
     * would pass the test above without Quit doing anything at all.
     */
    @Test
    fun quit_isTheOnlyThingThatRaisesTheDialog() = runUnitTest {
        playing {
            act(GameAction.Pause)
            assertFalse(state.confirmingQuit)
        }
    }

    /**
     * The settings edge the removed field used to travel down.
     *
     * `settingsChanged` is republished on every write to `AppData` from
     * anywhere — the pause overlay's ghost toggle, a reset, a gate. It used to
     * carry `confirmBeforeQuit` and set the flag `quit()` read. Nothing it can
     * carry now may put that branch back.
     */
    @Test
    fun quit_asksEvenAfterSettingsAreRewritten() = runUnitTest {
        playing {
            act(
                GameAction.SettingsChanged(AppData(hasUserOnboarded = true, ghostEnabled = false)),
                GameAction.Quit,
            )

            assertTrue(state.confirmingQuit)
        }
    }

    @Test
    fun confirmingTheDialog_leavesTheRun() = runUnitTest {
        playing {
            act(GameAction.Pause, GameAction.Quit, GameAction.ConfirmQuit)

            assertFalse(state.confirmingQuit)
            assertPhase(GamePhase.Ready)
        }
    }

    @Test
    fun dismissingTheDialog_keepsThePlayerWhereTheyWere() = runUnitTest {
        playing {
            act(GameAction.Pause, GameAction.Quit, GameAction.DismissQuitConfirm)

            assertFalse(state.confirmingQuit)
            assertPhase(GamePhase.Paused)
        }
    }
}
