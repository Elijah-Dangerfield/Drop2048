package com.dangerfield.drop2048.features.home.impl.feedback

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.drop2048.Telemetry
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

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
                isBugReport = isBugReport,
                eventId = logId,
                errorCode = errorCode,
                attachSessionLog = attachSessionLog,
            )
        }
    }
}
