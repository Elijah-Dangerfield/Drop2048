package com.dangerfield.drop2048.libraries.drop2048.impl.logging

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.isExpectedControlFlow
import com.dangerfield.drop2048.libraries.core.logging.LogContext
import com.dangerfield.drop2048.libraries.core.logging.LogEntry
import com.dangerfield.drop2048.libraries.core.logging.LogId
import com.dangerfield.drop2048.libraries.core.logging.LogLevel
import com.dangerfield.drop2048.libraries.core.logging.LogTree
import com.dangerfield.drop2048.libraries.networking.isOfflineError
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import kotlin.time.Clock

/**
 * Routes [KLog] entries to Sentry: breadcrumbs at/above [minBreadcrumbLevel],
 * events at/above [minEventLevel].
 *
 * It also keeps a bounded **in-memory ring buffer** of everything at/above
 * [minBufferLevel] — typically *below* the breadcrumb threshold, so the
 * fine-grained Debug/Verbose detail we deliberately don't ship is still
 * retained locally. Nothing leaves the device from the buffer until
 * [snapshot] is dumped onto a user-feedback event as an attachment (see
 * `AppTelemetry.captureUserFeedback`). Normal sessions cost nothing extra;
 * only sessions where the user actually reports something carry the rich log.
 */
class SentryLogTree(
    private val minBreadcrumbLevel: LogLevel,
    private val minEventLevel: LogLevel,
    private val minBufferLevel: LogLevel? = null,
    private val now: () -> String = { Clock.System.now().toString() },
) : LogTree() {

    private val ringBuffer: LogRingBuffer? =
        minBufferLevel?.let { LogRingBuffer(BUFFER_CAPACITY, MAX_LINE_CHARS) }

    // Lowest level worth delivering to this tree at all: a log below every
    // threshold (breadcrumb, event, buffer) is pure overhead, so the engine
    // skips it via isLoggable. minBufferLevel, when set, is usually the floor —
    // e.g. release buffers Debug+ but never formats per-frame Verbose.
    private val loggableFloor: Int = minOf(
        minBreadcrumbLevel.priority,
        minEventLevel.priority,
        minBufferLevel?.priority ?: Int.MAX_VALUE,
    )

    override fun isLoggable(level: LogLevel, tag: String?): Boolean {
        if (!Sentry.isEnabled()) return false
        return level.priority >= loggableFloor
    }

    override fun log(entry: LogEntry): LogId? {
        if (!Sentry.isEnabled()) return null

        if (minBufferLevel != null && entry.level.priority >= minBufferLevel.priority) {
            appendToBuffer(entry)
        }

        if (entry.level.priority >= minBreadcrumbLevel.priority) {
            addBreadcrumb(entry)
        }

        if (shouldCaptureEvent(entry)) {
            return captureEvent(entry)
        }

        return null
    }

    /**
     * Error-level and above becomes a Sentry event, except two expected classes
     * that still breadcrumb and buffer locally but never inflate error counts:
     * typed control-flow throwables (e.g. `AuthUnready` short-circuiting an
     * authed call before it hits the wire), and device-offline connectivity
     * failures (a phone in airplane mode failing background calls is
     * not an app failure, and one such device flooded the error panel).
     */
    internal fun shouldCaptureEvent(entry: LogEntry): Boolean {
        if (entry.level.priority < minEventLevel.priority) return false
        val throwable = entry.throwable ?: return true
        return !throwable.isExpectedControlFlow && !throwable.isOfflineError()
    }

    /**
     * One newline-joined dump of the buffered lines, for attaching to a
     * feedback event. Non-clearing — the ring keeps overwriting itself, so a
     * later feedback still has recent context. Empty when buffering is off.
     */
    fun snapshot(): String = ringBuffer?.snapshot() ?: ""

    private fun appendToBuffer(entry: LogEntry) {
        ringBuffer?.add(bufferLine(entry))
    }

    /**
     * One buffered line: timestamp, level, tag, message. **Never the entry's
     * context.**
     *
     * That omission is the promise `settings_diagnostics_hint` makes. An app
     * event logs its *name* as the message and everything else as extras — so a
     * `run.end` buffers as "run.end" and its `score`, `level` and `highest_tier`
     * stay out of the dump that a player who opted in sends us. Writing the
     * extras here would be a one-line change and would silently break the copy
     * on screen, which is why `SentryLogTreeTest` asserts on it.
     *
     * Internal for that test. Breadcrumbs deliberately do the opposite and carry
     * the extras, which is why the feedback carrier event clears them; see
     * `Telemetry.captureUserFeedback`.
     */
    internal fun bufferLine(entry: LogEntry): String {
        val message = entry.message ?: entry.throwable?.message ?: DEFAULT_MESSAGE
        return "${now()} ${entry.level.name.uppercase()} ${entry.tag ?: "-"}: $message"
    }

    private fun addBreadcrumb(entry: LogEntry) {
        val breadcrumb = Breadcrumb().apply {
            level = entry.level.toSentryLevel()
            category = entry.tag ?: BREADCRUMB_CATEGORY
            message = entry.message ?: entry.throwable?.message ?: DEFAULT_MESSAGE
        }

        entry.context.tags.forEach { (key, value) ->
            breadcrumb.setData("tag.$key", value)
        }
        entry.context.extras.forEach { (key, value) ->
            if (value != null) {
                breadcrumb.setData("extra.$key", value)
            }
        }

        Sentry.addBreadcrumb(breadcrumb)
    }

    private fun captureEvent(entry: LogEntry): LogId? {
        val message = entry.message ?: entry.throwable?.message ?: DEFAULT_MESSAGE
        val sentryLevel = entry.level.toSentryLevel()

        val sentryId = entry.throwable?.let {
            Sentry.captureException(it) { scope ->
                scope.level = sentryLevel
                applyContext(scope, entry)
            }
        } ?: Sentry.captureMessage(message) { scope ->
            scope.level = sentryLevel
            applyContext(scope, entry)
        }

        return LogId.from(sentryId.toString())
    }

    private fun applyContext(scope: Scope, entry: LogEntry) {
        val tag = entry.tag
        if (!tag.isNullOrBlank()) {
            scope.setTag(LOGGER_TAG_KEY, tag)
        }

        applyContextMaps(scope, entry.context)
    }

    private fun applyContextMaps(scope: Scope, context: LogContext) {
        context.tags.forEach { (key, value) ->
            scope.setTag(key, value)
        }
        context.extras.forEach { (key, value) ->
            if (value != null) {
                scope.setExtra(key, Catching {value.toString()}.getOrElse { "${value::class.simpleName}" } )
            }
        }
    }

    private fun LogLevel.toSentryLevel(): SentryLevel = when (this) {
        LogLevel.Verbose, LogLevel.Debug -> SentryLevel.DEBUG
        LogLevel.Info -> SentryLevel.INFO
        LogLevel.Warn -> SentryLevel.WARNING
        LogLevel.Error -> SentryLevel.ERROR
        LogLevel.Assert, LogLevel.Fatal -> SentryLevel.FATAL
    }

    companion object {
        private const val LOGGER_TAG_KEY = "logger_tag"
        private const val BREADCRUMB_CATEGORY = "klog"
        private const val DEFAULT_MESSAGE = "(no message)"

        // Ring-buffer bounds: ~500 lines, each capped, keeps the feedback
        // attachment small (worst case a few hundred KB) while covering the
        // recent history that matters for a just-reported issue.
        private const val BUFFER_CAPACITY = 500
        private const val MAX_LINE_CHARS = 1000
    }
}