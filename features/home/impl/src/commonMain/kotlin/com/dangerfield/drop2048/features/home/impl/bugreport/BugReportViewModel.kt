package com.dangerfield.drop2048.features.home.impl.bugreport

import com.dangerfield.drop2048.features.home.impl.feedback.FeedbackRepository
import com.dangerfield.drop2048.features.home.impl.feedback.withDiagnostics
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.eitherWay
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.ui.snackbar.showSnackBar
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Duration.Companion.seconds

@Inject
class BugReportViewModel(
    private val repository: FeedbackRepository,
    private val router: Router,
    private val appCache: AppCache,
    @Assisted logId: String? = null,
    @Assisted errorCode: Int? = null,
    @Assisted contextMessage: String? = null,
) : SEAViewModel<BugReportState, Unit, BugReportAction>(
    initialStateArg = BugReportState(
        logId = logId,
        errorCode = errorCode,
        contextMessage = contextMessage
    )
) {

    init {
        takeAction(BugReportAction.Load)
    }

    override suspend fun handleAction(action: BugReportAction) {
        when (action) {
            BugReportAction.Load -> action.load()
            BugReportAction.Back -> router.goBack()
            is BugReportAction.MessageChanged -> action.updateMessage()
            BugReportAction.Submit -> action.submitBugReport()
        }
    }

    /**
     * Reads the same `diagnosticsOptIn` the feedback form and the settings row
     * write.
     *
     * A bug report never read it before, so it sent a session log from a player
     * who had left the switch off — the toggle gated the feedback form's
     * appended version string and nothing else. One preference, three places it
     * is read, no second copy of it.
     */
    private suspend fun BugReportAction.load() {
        val saved = Catching { appCache.get() }
            .logOnFailure { "Could not read the diagnostics preference" }
            .getOrNull()
            ?: return
        updateState { it.copy(diagnosticsOptIn = saved.diagnosticsOptIn) }
    }

    private suspend fun BugReportAction.MessageChanged.updateMessage() {
        val updated = value
        updateState { it.copy(message = updated, errorMessage = null) }
    }

    private suspend fun BugReportAction.submitBugReport() {
        val current = state
        if (current.message.isBlank()) {
            updateState { it.copy(errorMessage = "Add a quick note before sending.") }
            return
        }
        updateState { it.copy(isSubmitting = true, errorMessage = null) }
        repository.submitFeedback(
            message = current.message.trim().withDiagnostics(current.diagnosticsOptIn),
            isBugReport = true,
            logId = current.logId,
            errorCode = current.errorCode,
            attachSessionLog = current.diagnosticsOptIn,
        ).eitherWay {
            appCache.update { it.copy(bugsReported = it.bugsReported + 1) }
            updateState { it.copy(isSubmitting = false) }
            showSnackBar(message = "Thanks for reporting this!", delayBy = 1.seconds)
            router.goBack()
        }
    }
}

data class BugReportState(
    val message: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val logId: String? = null,
    val errorCode: Int? = null,
    val contextMessage: String? = null,

    /** SPEC 17. Off unless the player turned it on, here or in settings. */
    val diagnosticsOptIn: Boolean = false,
) {
    val hasContext: Boolean
        get() = !contextMessage.isNullOrBlank() || !logId.isNullOrBlank() || errorCode != null
}

sealed interface BugReportAction {
    data object Load : BugReportAction
    data object Back : BugReportAction
    data class MessageChanged(val value: String) : BugReportAction
    data object Submit : BugReportAction

}
