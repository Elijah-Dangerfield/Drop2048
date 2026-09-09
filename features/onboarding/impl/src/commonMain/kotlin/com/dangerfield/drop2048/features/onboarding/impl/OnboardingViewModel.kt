package com.dangerfield.drop2048.features.onboarding.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import kotlin.time.TimeSource
import me.tatarka.inject.annotations.Inject

/**
 * First-launch welcome. Drop 2048 has no accounts, so this flow only marks the
 * device as onboarded and hands off to the game; there is no identity to
 * bootstrap and nothing to sign into.
 *
 * The body of this flow is a placeholder until C5 replaces it with the guided
 * tutorial. `hasUserOnboarded` is the flag that stops it opening twice, and it
 * is the only durable side effect the flow has.
 */
@Inject
class OnboardingViewModel(
    private val appCache: AppCache,
) : SEAViewModel<OnboardingState, OnboardingEvent, OnboardingAction>(
    initialStateArg = OnboardingState(),
) {

    private val logger = KLog.withTag("OnboardingFlow")

    private val onboardingStartedAt = TimeSource.Monotonic.markNow()

    private var exitedToHome = false

    init {
        takeAction(OnboardingAction.ResolveEntry)
    }

    override suspend fun handleAction(action: OnboardingAction) {
        when (action) {
            OnboardingAction.ResolveEntry -> action.handleResolveEntry()
            OnboardingAction.Start -> action.handleStart()
        }
    }

    private suspend fun OnboardingAction.handleResolveEntry() {
        Catching {
            if (appCache.get().hasUserOnboarded) {
                exitedToHome = true
                sendEvent(OnboardingEvent.NavigateToHome)
            } else {
                logger.logEvent("onboarding.step_viewed", "step" to "welcome")
            }
        }.logOnFailure { "Onboarding entry resolution failed" }
    }

    private suspend fun OnboardingAction.handleStart() {
        updateState { it.copy(isFinishing = true) }
        exitedToHome = true
        logger.logEvent(
            "onboarding.completed",
            "duration_sec" to onboardingStartedAt.elapsedNow().inWholeSeconds,
        )
        Catching { appCache.update { it.copy(hasUserOnboarded = true) } }
            .logOnFailure { "Failed to persist onboarding completion" }
        sendEvent(OnboardingEvent.NavigateToHome)
    }

    override fun onCleared() {
        if (!exitedToHome) logger.logEvent("onboarding.abandoned", "step" to "welcome")
        super.onCleared()
    }
}

data class OnboardingState(
    val isFinishing: Boolean = false,
)

sealed interface OnboardingEvent {
    data object NavigateToHome : OnboardingEvent
}

sealed interface OnboardingAction {
    /** Internal: fired once on init to bounce a returning player straight home. */
    data object ResolveEntry : OnboardingAction

    /** Leave onboarding for the game and never come back. */
    data object Start : OnboardingAction
}
