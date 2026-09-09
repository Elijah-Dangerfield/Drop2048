package com.dangerfield.drop2048.features.home.impl

import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject

/**
 * Placeholder home. The template's version loaded a profile; Drop 2048 has no
 * accounts, so there is nothing to load until C3 gives Home something to show.
 */
@Inject
class HomeViewModel : SEAViewModel<HomeState, HomeEvent, HomeAction>(
    initialStateArg = HomeState(),
) {

    override suspend fun handleAction(action: HomeAction) {
        when (action) {
            is HomeAction.Load -> Unit
            is HomeAction.Refresh -> Unit
        }
    }
}

data class HomeState(
    val placeholder: Unit = Unit,
)

sealed interface HomeEvent

sealed interface HomeAction {
    data object Load : HomeAction
    data object Refresh : HomeAction
}
