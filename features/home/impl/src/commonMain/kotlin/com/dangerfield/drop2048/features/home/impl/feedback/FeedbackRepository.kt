package com.dangerfield.drop2048.features.home.impl.feedback

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.drop2048.FeedbackKind
import com.dangerfield.drop2048.libraries.drop2048.Telemetry
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The **player's** way into Sentry: the feedback page in Settings and the bug
 * reporter behind a crash.
 *
 * It can only file the two player kinds, and that is the point rather than an
 * omission. [FeedbackKind.OwnerDirective] is filed from the tester-only panel,
 * against `Telemetry` directly, by a module a release build does not contain —
 * so there is no argument a screen in this feature could pass that would dress a
 * player's report up as the owner's and take their session log with it.
 */
interface FeedbackRepository {
    /**
     * @param attachSessionLog the player's `diagnosticsOptIn`. Off unless they
     *   turned the switch on, and the only thing that lets a session log leave
     *   with a report. See [Telemetry.captureUserFeedback].
     */
    suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String? = null,
        errorCode: Int? = null,
        attachSessionLog: Boolean = false,
    ): Catching<Unit>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class FeedbackRepositoryImpl @Inject constructor(
    private val telemetry: Telemetry
) : FeedbackRepository {
    override suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String?,
        errorCode: Int?,
        attachSessionLog: Boolean,
    ): Catching<Unit> {
        return Catching {
            telemetry.captureUserFeedback(
                message = message,
                kind = if (isBugReport) FeedbackKind.BugReport else FeedbackKind.Feedback,
                eventId = logId,
                errorCode = errorCode,
                attachSessionLog = attachSessionLog,
            )
        }
    }
}
