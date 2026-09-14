package com.dangerfield.drop2048.libraries.devfeedback.tester

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.drop2048.FeedbackKind
import com.dangerfield.drop2048.libraries.drop2048.Telemetry
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Where a directive goes: Sentry, as a carrier event tagged
 * `feedback_kind:owner_directive` with the words, the session log and the
 * screenshot on it.
 *
 * Its own one-method seam rather than injecting `Telemetry` into the view model,
 * for two reasons. A fake of `Telemetry` is a dozen empty overrides in a test
 * that cares about one call, and more importantly this is the only place in the
 * app that can produce a report of that kind — so it is worth being a named
 * thing somebody can grep for rather than an argument at a call site.
 *
 * The player's equivalent is `FeedbackRepository` in `:features:home:impl`, and
 * the two deliberately do not share a type. They differ in what they are allowed
 * to attach, and a shared interface is how that difference becomes a parameter
 * somebody passes the wrong value for.
 */
interface OwnerDirectiveReporter {
    suspend fun fileDirective(message: String, screenshots: List<ByteArray>): Catching<Unit>
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class TelemetryOwnerDirectiveReporter(
    private val telemetry: Telemetry,
) : OwnerDirectiveReporter {

    /**
     * `attachSessionLog` is not passed, and that is the whole point of the kind.
     * The log rides because this is [FeedbackKind.OwnerDirective], decided
     * inside `captureUserFeedback`, so there is no argument here that could be
     * set to `true` on a player's report by mistake.
     */
    override suspend fun fileDirective(
        message: String,
        screenshots: List<ByteArray>,
    ): Catching<Unit> = Catching {
        telemetry.captureUserFeedback(
            message = message,
            kind = FeedbackKind.OwnerDirective,
            eventId = null,
            errorCode = null,
            screenshots = screenshots,
        )
    }
}
