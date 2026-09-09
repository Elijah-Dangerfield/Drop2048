package com.dangerfield.drop2048.features.stats.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.progress.ProgressRepository
import com.dangerfield.drop2048.libraries.progress.RunStats
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * SPEC 15, and nothing else.
 *
 * There is no arithmetic here: every number is folded out of `run_record` by
 * `:libraries:progress` and this only decides when to ask. A stat computed in a
 * ViewModel would be a second source of truth for a number the stats page and
 * the HUD both show.
 */
@Inject
class StatsViewModel(
    private val progress: ProgressRepository,
) : SEAViewModel<StatsState, StatsEvent, StatsAction>(initialStateArg = StatsState()) {

    init {
        takeAction(StatsAction.Load)
    }

    override suspend fun handleAction(action: StatsAction) {
        when (action) {
            StatsAction.Load -> action.load()
            StatsAction.Back -> sendEvent(StatsEvent.Leave)
        }
    }

    /**
     * Collected for the life of the screen rather than read once, so a run
     * recorded while the page is open moves the numbers on it. That happens: the
     * page is opened from the stacked-out sheet and the sheet is still behind it.
     */
    private fun StatsAction.load() {
        viewModelScope.launch {
            progress.observeStats()
                .catch { Catching<Unit> { throw it }.logOnFailure { "Could not read stats" } }
                .collect { stats -> updateState { it.copy(stats = stats, loading = false) } }
        }
    }
}

data class StatsState(
    val stats: RunStats = RunStats.Empty,
    val loading: Boolean = true,
)

sealed interface StatsEvent {
    data object Leave : StatsEvent
}

sealed interface StatsAction {
    data object Load : StatsAction
    data object Back : StatsAction
}
