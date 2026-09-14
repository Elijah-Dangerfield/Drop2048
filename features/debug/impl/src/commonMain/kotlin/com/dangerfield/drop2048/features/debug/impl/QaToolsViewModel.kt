package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.devfeedback.DevFeedbackFabCache
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

/**
 * The QA menu, which today is one switch.
 *
 * It exists as its own view model rather than three more fields on
 * [DebugViewModel] because of what [DebugViewModel] does on construction:
 * `markDebugSession()`. See `QaToolsRoute` for the full argument. In short, this
 * screen must be free to open, and the way to guarantee that is for it to hold
 * no reference to `DebugController` at all rather than to remember not to call
 * it — a shape that cannot express the mistake beats a guard you can forget, the
 * same trade C12 made with `DebugOverrides`.
 *
 * The cache is **read** rather than observed. Every write on this screen
 * refreshes, and the only other writer is the drag, which cannot happen while
 * this screen is covering the button.
 */
@Inject
class QaToolsViewModel(
    private val feedbackFab: DevFeedbackFabCache,
) : SEAViewModel<QaToolsState, QaToolsEvent, QaToolsAction>(
    initialStateArg = QaToolsState(),
) {

    init {
        takeAction(QaToolsAction.Refresh)
    }

    override suspend fun handleAction(action: QaToolsAction) {
        when (action) {
            QaToolsAction.Refresh -> action.refresh()
            is QaToolsAction.ShowFeedbackFab -> action.write {
                feedbackFab.update { it.copy(hidden = !action.shown) }
            }

            QaToolsAction.Back -> sendEvent(QaToolsEvent.Back)
        }
    }

    private suspend fun QaToolsAction.write(block: suspend () -> Unit) {
        Catching { block() }.logOnFailure { "QA tool failed" }
        refresh()
    }

    private suspend fun QaToolsAction.refresh() {
        val fab = Catching { feedbackFab.get() }
            .logOnFailure { "Could not read where the directive button is" }
            .getOrNull()
        updateState { it.copy(loaded = true, feedbackFabShown = fab?.hidden != true) }
    }
}

data class QaToolsState(
    val loaded: Boolean = false,
    /**
     * Defaults to shown, which is what the cache defaults to. A `false` here
     * before the read lands would blink the switch off on every open.
     */
    val feedbackFabShown: Boolean = true,
)

sealed interface QaToolsEvent {
    data object Back : QaToolsEvent
}

sealed interface QaToolsAction {
    data object Refresh : QaToolsAction
    data class ShowFeedbackFab(val shown: Boolean) : QaToolsAction
    data object Back : QaToolsAction
}
