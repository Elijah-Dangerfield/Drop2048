package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.libraries.devfeedback.DevFeedbackFabCache
import com.dangerfield.drop2048.libraries.devfeedback.DevFeedbackFabState
import com.dangerfield.drop2048.libraries.devfeedback.FabPlacement
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one switch on the QA screen, and the two things about it that matter.
 *
 * **It actually hides the button.** The switch is the only way back from a
 * button dragged somewhere unhelpful, and the only thing that reads its state is
 * a composable in a module this one cannot see, so the assertion has to be on
 * what reaches the cache: `hidden`, which `DevFeedbackHost` returns early on.
 *
 * **It does not move the button while it is doing it.** Hiding writes one field
 * of a record whose other two fields are a position the owner chose. A `set`
 * where an `update` belongs would put the button back at the default the next
 * time it was shown, which reads as the switch having broken the drag.
 */
class QaToolsViewModelTest : CoroutineTest() {

    @Test
    fun turningTheSwitchOffHidesTheButton() = runUnitTest {
        val cache = FakeFabCache()
        val viewModel = QaToolsViewModel(cache)

        viewModel.takeAction(QaToolsAction.ShowFeedbackFab(shown = false))

        assertTrue(cache.state.value.hidden, "this is the field the host returns early on")
        assertFalse(viewModel.stateFlow.value.feedbackFabShown)
    }

    @Test
    fun turningItBackOnShowsItAgain() = runUnitTest {
        val cache = FakeFabCache(DevFeedbackFabState(hidden = true))
        val viewModel = QaToolsViewModel(cache)

        viewModel.takeAction(QaToolsAction.ShowFeedbackFab(shown = true))

        assertFalse(cache.state.value.hidden)
        assertTrue(viewModel.stateFlow.value.feedbackFabShown)
    }

    @Test
    fun hidingTheButtonDoesNotForgetWhereItWasPut() = runUnitTest {
        val moved = DevFeedbackFabState(x = 0.1f, y = 0.9f)
        val cache = FakeFabCache(moved)
        val viewModel = QaToolsViewModel(cache)

        viewModel.takeAction(QaToolsAction.ShowFeedbackFab(shown = false))

        assertEquals(FabPlacement(x = 0.1f, y = 0.9f), cache.state.value.placement)
    }

    @Test
    fun theScreenReportsWhatIsOnDiskRatherThanTheDefault() = runUnitTest {
        val cache = FakeFabCache(DevFeedbackFabState(hidden = true))

        val viewModel = QaToolsViewModel(cache)

        assertTrue(viewModel.stateFlow.value.loaded)
        assertFalse(
            viewModel.stateFlow.value.feedbackFabShown,
            "a switch that reads on every open is the only thing telling the owner why the " +
                "button is not there",
        )
    }

    private class FakeFabCache(
        initial: DevFeedbackFabState = DevFeedbackFabState(),
    ) : DevFeedbackFabCache {
        val state = MutableStateFlow(initial)

        override val updates: Flow<DevFeedbackFabState> = state
        override suspend fun get(): DevFeedbackFabState = state.value
        override suspend fun set(value: DevFeedbackFabState) {
            state.value = value
        }

        override suspend fun clear() {
            state.value = DevFeedbackFabState()
        }

        override suspend fun update(
            transform: (DevFeedbackFabState) -> DevFeedbackFabState,
        ): DevFeedbackFabState {
            state.value = transform(state.value)
            return state.value
        }
    }
}
