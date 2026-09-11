package com.dangerfield.drop2048.libraries.drop2048.impl.logging

import com.dangerfield.drop2048.libraries.core.AuthReason
import com.dangerfield.drop2048.libraries.core.AuthUnready
import com.dangerfield.drop2048.libraries.core.logging.EXTRA_APP_EVENT
import com.dangerfield.drop2048.libraries.core.logging.LogContext
import com.dangerfield.drop2048.libraries.core.logging.LogEntry
import com.dangerfield.drop2048.libraries.core.logging.LogLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryLogTreeTest {

    private val tree = SentryLogTree(
        minBreadcrumbLevel = LogLevel.Info,
        minEventLevel = LogLevel.Error,
    )

    @Test
    fun `expected control-flow throwable at error level is not captured as an event`() {
        assertFalse(tree.shouldCaptureEvent(errorEntry(AuthUnready(AuthReason.FinishingSetup))))
        assertFalse(tree.shouldCaptureEvent(errorEntry(AuthUnready(AuthReason.NeedAccount))))
    }

    @Test
    fun `real throwable at error level is captured as an event`() {
        assertTrue(tree.shouldCaptureEvent(errorEntry(IllegalStateException("boom"))))
    }

    @Test
    fun `error-level message without a throwable is captured as an event`() {
        assertTrue(tree.shouldCaptureEvent(errorEntry(throwable = null)))
    }

    @Test
    fun `below-threshold entries are never captured as events`() {
        assertFalse(
            tree.shouldCaptureEvent(
                LogEntry(
                    level = LogLevel.Warn,
                    tag = "test",
                    message = "warn",
                    throwable = IllegalStateException("boom"),
                    context = LogContext.Empty,
                )
            )
        )
    }

    /**
     * The session log a player sends with an opted-in feedback report carries
     * the *name* of an app event and none of its attributes.
     *
     * `settings_diagnostics_hint` promises "never your board, your scores". A
     * `run.end` logs its score, level and highest tier as context extras and the
     * event name as its message, so the whole promise rests on this line not
     * formatting the context. Asserted both ways: the name has to be there, or
     * this would pass against a buffer that dropped the entry entirely.
     */
    @Test
    fun `a buffered app event carries its name and none of its attributes`() {
        val line = tree.bufferLine(
            LogEntry(
                level = LogLevel.Info,
                tag = "GameViewModel",
                message = "run.end",
                throwable = null,
                context = LogContext(
                    tags = emptyMap(),
                    extras = mapOf(
                        EXTRA_APP_EVENT to "run.end",
                        "score" to 148_320L,
                        "level" to 17,
                        "highest_tier" to 11,
                    ),
                ),
            )
        )

        assertTrue(line.contains("run.end"))
        assertFalse(line.contains("148320"))
        assertFalse(line.contains("highest_tier"))
    }

    private fun errorEntry(throwable: Throwable?): LogEntry = LogEntry(
        level = LogLevel.Error,
        tag = "test",
        message = throwable?.message ?: "error",
        throwable = throwable,
        context = LogContext.Empty,
    )
}
