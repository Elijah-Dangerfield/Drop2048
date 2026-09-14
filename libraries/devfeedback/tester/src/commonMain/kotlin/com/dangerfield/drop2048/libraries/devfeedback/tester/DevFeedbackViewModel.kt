package com.dangerfield.drop2048.libraries.devfeedback.tester

import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.drop2048.FeedbackKind
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The owner's directive channel. Everything typed here is filed to Sentry as
 * [FeedbackKind.OwnerDirective], which is what the `feedback-triage` skill
 * searches for and turns into a `docs/todos.md` item.
 *
 * A singleton rather than a per-open instance: the panel is an overlay that can
 * be opened from anywhere, and a half-written directive should survive
 * dismissing the panel to go and look at the thing being complained about.
 */
@Inject
@SingleIn(AppScope::class)
class DevFeedbackViewModel(
    private val reporter: OwnerDirectiveReporter,
) : SEAViewModel<DevFeedbackState, Unit, DevFeedbackAction>(
    initialStateArg = DevFeedbackState(),
) {

    override suspend fun handleAction(action: DevFeedbackAction) {
        when (action) {
            is DevFeedbackAction.Open -> action.updateState {
                it.copy(isOpen = true, screenshot = action.screenshot, sent = false)
            }

            DevFeedbackAction.Dismiss -> action.updateState { it.copy(isOpen = false) }

            is DevFeedbackAction.MessageChanged -> action.updateState {
                it.copy(message = action.value.take(DirectiveCharLimit))
            }

            DevFeedbackAction.RemoveScreenshot -> action.updateState { it.copy(screenshot = null) }

            DevFeedbackAction.Submit -> action.submit()
        }
    }

    /**
     * Failure is not surfaced, and that is a decision rather than a swallowed
     * error. Sentry has no DSN on a local build, so a directive filed there
     * never leaves the device and never will; the only recovery offered by a
     * failure state would be retyping the same words into the same disabled
     * Sentry. The reporter has already logged it. Clearing the form and
     * confirming is the honest outcome either way, and an error banner the owner
     * learns to ignore would cost them the next directive too.
     */
    private suspend fun DevFeedbackAction.submit() {
        val message = state.message.trim()
        if (message.isEmpty()) return

        val screenshot = state.screenshot
        updateState { it.copy(isSubmitting = true) }

        reporter.fileDirective(
            message = message,
            screenshots = listOfNotNull(screenshot?.bytes),
        ).logOnFailure { "Owner directive failed to reach Sentry" }

        updateState { DevFeedbackState(isOpen = true, sent = true) }
    }
}

/** Long enough for a paragraph of intent, short enough to stay one ask. */
const val DirectiveCharLimit: Int = 1000

data class DevFeedbackState(
    val isOpen: Boolean = false,
    val message: String = "",
    /**
     * Captured from the screen the panel opened over, before it covered it.
     *
     * Pre-attached rather than picked, because the frame the owner was looking
     * at when they reached for the button is almost always the one they mean.
     * Removable because it is the one attachment that can show something they
     * did not intend to send, and because it is by far the largest.
     */
    val screenshot: Screenshot? = null,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
) {
    val canSubmit: Boolean get() = message.isNotBlank() && !isSubmitting
}

sealed interface DevFeedbackAction {
    data class Open(val screenshot: Screenshot?) : DevFeedbackAction
    data object Dismiss : DevFeedbackAction
    data class MessageChanged(val value: String) : DevFeedbackAction
    data object RemoveScreenshot : DevFeedbackAction
    data object Submit : DevFeedbackAction
}
