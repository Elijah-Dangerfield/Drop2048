package com.dangerfield.drop2048.libraries.devfeedback.tester

import com.dangerfield.drop2048.libraries.drop2048.FeedbackKind
import com.dangerfield.drop2048.libraries.drop2048.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * A directive files as the owner's kind, and asks for nothing else.
 *
 * The distinction this holds is C13a's, and it survived the owner ruling of
 * 2026-09-20 that took the diagnostics switch off the settings screen. A
 * player's report attaches a session log only when they opted in; a directive
 * gets the log and its breadcrumbs unconditionally, and `AppTelemetry` decides
 * that from [FeedbackKind.isOwnerChannel] rather than from an argument. So the
 * property worth pinning here is **negative**: this reporter never passes
 * `attachSessionLog`, because a flag it could pass is a flag a player's report
 * could be given too.
 *
 * **Not covered here:** that the Sentry event actually carries the dump.
 * `AppTelemetry` owns the `ownerChannel || attachSessionLog` line and the
 * `clearBreadcrumbs` beside it; `FeedbackKindPolicyTest` holds the floor that
 * exactly one kind is the owner's.
 */
class TelemetryOwnerDirectiveReporterTest {

    @Test
    fun aDirectiveIsFiledAsTheOwnersKind() = runTest {
        val telemetry = RecordingTelemetry()

        TelemetryOwnerDirectiveReporter(telemetry)
            .fileDirective("the board sizing is off on a small phone", listOf(byteArrayOf(1)))

        val report = telemetry.reports.single()
        assertEquals(FeedbackKind.OwnerDirective, report.kind)
        assertTrue(
            report.kind.isOwnerChannel,
            "the log rides on the kind. If this stops being the owner channel, a directive " +
                "quietly files with no session log and nothing else changes.",
        )
        assertEquals(1, report.screenshots.size)
    }

    @Test
    fun theReporterNeverAsksForTheSessionLog() = runTest {
        val telemetry = RecordingTelemetry()

        TelemetryOwnerDirectiveReporter(telemetry).fileDirective("something", emptyList())

        assertFalse(
            telemetry.reports.single().attachSessionLog,
            "attachSessionLog is the player's opt-in. A directive gets the log from its kind, " +
                "and a caller that sets this flag is one edit away from setting it on a " +
                "player's report.",
        )
    }
}

private class RecordingTelemetry : Telemetry {
    data class Report(
        val message: String,
        val kind: FeedbackKind,
        val attachSessionLog: Boolean,
        val screenshots: List<ByteArray>,
    )

    val reports = mutableListOf<Report>()

    override fun initialize() = Unit
    override fun setCurrentRoute(route: String) = Unit
    override fun setSession(sessionId: String) = Unit
    override fun setInstallId(installId: String) = Unit
    override fun setContext(key: String, value: String?) = Unit

    override fun captureUserFeedback(
        message: String,
        kind: FeedbackKind,
        eventId: String?,
        errorCode: Int?,
        attachSessionLog: Boolean,
        screenshots: List<ByteArray>,
    ) {
        reports += Report(message, kind, attachSessionLog, screenshots)
    }
}
