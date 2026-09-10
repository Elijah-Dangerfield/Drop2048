package com.dangerfield.drop2048.features.home.impl.feedback

import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.eitherWay
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.versionString
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.ui.snackbar.showSnackBar
import me.tatarka.inject.annotations.Inject
import kotlin.time.Duration.Companion.seconds

@Inject
class FeedbackViewModel(
    private val repository: FeedbackRepository,
    private val router: Router,
    private val appCache: AppCache,
) : SEAViewModel<FeedbackState, Unit, FeedbackAction>(
    initialStateArg = FeedbackState()
) {

    init {
        takeAction(FeedbackAction.Load)
    }

    override suspend fun handleAction(action: FeedbackAction) {
        when (action) {
            FeedbackAction.Load -> action.load()
            FeedbackAction.Back -> router.goBack()
            is FeedbackAction.MessageChanged -> action.updateMessage()
            FeedbackAction.ToggleDiagnostics -> action.toggleDiagnostics()
            FeedbackAction.Submit -> action.submitFeedback()
        }
    }

    private suspend fun FeedbackAction.load() {
        val saved = Catching { appCache.get() }
            .logOnFailure { "Could not read the diagnostics preference" }
            .getOrNull()
            ?: return
        updateState { it.copy(diagnosticsOptIn = saved.diagnosticsOptIn) }
    }

    /**
     * SPEC 17's opt-in, written through to the same field the settings row
     * writes. One preference, two places it can be set, and no third copy of it.
     */
    private suspend fun FeedbackAction.toggleDiagnostics() {
        val next = !state.diagnosticsOptIn
        updateState { it.copy(diagnosticsOptIn = next) }
        Catching { appCache.update { it.copy(diagnosticsOptIn = next) } }
            .logOnFailure { "Could not persist the diagnostics preference" }
    }

    private suspend fun FeedbackAction.MessageChanged.updateMessage() {
        val updated = value
        updateState { it.copy(message = updated, errorMessage = null) }
    }

    private suspend fun FeedbackAction.submitFeedback() {
        val current = state
        if (current.message.isBlank()) {
            updateState { it.copy(errorMessage = "Add a quick note before sending.") }
            return
        }
        updateState { it.copy(isSubmitting = true, errorMessage = null) }
        repository.submitFeedback(
            message = current.message.trim().withDiagnostics(current.diagnosticsOptIn),
            isBugReport = false
        ).eitherWay {
            appCache.update { it.copy(feedbacksGiven = it.feedbacksGiven + 1) }
            updateState { it.copy(isSubmitting = false) }
            showSnackBar(message = "Got it. Thank you!", delayBy = 1.seconds)
            router.goBack()
        }
    }
}

/**
 * Device and build, appended only when the player has said yes.
 *
 * Appended to the message rather than sent as a side channel, so what is
 * attached is exactly what the copy beside the switch says is attached, and the
 * player could read it back if they wanted to.
 */
private fun String.withDiagnostics(optedIn: Boolean): String =
    if (!optedIn) this
    else "$this\n\n---\n${BuildInfo.platform} · ${BuildInfo.versionString()} · ${BuildInfo.releaseChannel}"

data class FeedbackState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,

    /** SPEC 17. Off unless the player turned it on, here or in settings. */
    val diagnosticsOptIn: Boolean = false,
)

sealed interface FeedbackAction {
    data object Load : FeedbackAction
    data object Back : FeedbackAction
    data object ToggleDiagnostics : FeedbackAction
    data class MessageChanged(val value: String) : FeedbackAction
    data object Submit : FeedbackAction
}
