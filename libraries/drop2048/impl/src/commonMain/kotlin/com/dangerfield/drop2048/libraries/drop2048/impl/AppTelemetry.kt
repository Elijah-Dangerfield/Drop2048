package com.dangerfield.drop2048.libraries.drop2048.impl

import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.Platform
import com.dangerfield.drop2048.libraries.core.TelemetryInfo
import com.dangerfield.drop2048.libraries.core.buildType
import com.dangerfield.drop2048.libraries.core.versionString
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.LogLevel
import com.dangerfield.drop2048.libraries.core.logging.Logger
import com.dangerfield.drop2048.libraries.drop2048.FeedbackKind
import com.dangerfield.drop2048.libraries.drop2048.Telemetry
import com.dangerfield.drop2048.libraries.drop2048.impl.logging.DevConsoleWriter
import com.dangerfield.drop2048.libraries.drop2048.impl.logging.KermitLogTree
import com.dangerfield.drop2048.libraries.drop2048.impl.logging.SentryLogTree
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity as KermitSeverity
import io.sentry.kotlin.multiplatform.Attachment
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.SentryOptions
import io.sentry.kotlin.multiplatform.protocol.UserFeedback
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppTelemetry : Telemetry by ConfiguredTelemetry(
    configProvider = { SentryRuntimeConfig.forApp(BuildInfo) }
)

class IosExtensionTelemetry(
    private val configProvider: () -> SentryRuntimeConfig = { SentryRuntimeConfig.forIosExtension(BuildInfo) }
) : Telemetry by ConfiguredTelemetry(configProvider)

private class ConfiguredTelemetry(
    private val configProvider: () -> SentryRuntimeConfig
) : Telemetry {

    private val logger: Logger = KLog.withTag("Telemetry")
    private var initialized = false

    // The planted Sentry tree, held so captureUserFeedback can dump its
    // in-memory log buffer as an attachment. Null until Sentry initializes.
    private var sentryLogTree: SentryLogTree? = null

    override fun initialize() {
        if (initialized) return
        initialized = true

        KLog.plant(KermitLogTree())

        // Debug-only: drop Kermit's global min-severity to Verbose so nothing
        // is pre-filtered before reaching any writer. The platform writers
        // (OSLogWriter on iOS, LogcatWriter on Android) handle Info+ natively.
        // [DevConsoleWriter] adds a pretty stdout-only path for Debug-and-
        // below entries, because Android Studio's KMM plugin filters those
        // out of its Run window when running iOS apps. See the writer's
        // header for the full reasoning.
        if (BuildInfo.isDebug) {
            KermitLogger.setMinSeverity(KermitSeverity.Verbose)
            KermitLogger.addLogWriter(DevConsoleWriter())
        }

        val config = configProvider()

        if (!config.isEnabled) {
            logger.i { scope ->
                scope.tag("environment", config.environment)
                scope.tag("platform", config.platformTag)
                "Sentry disabled for ${config.environment}"
            }
            return
        }

        Catching {
            Sentry.init(config::applyTo)
        }.onFailure {
            logger.e(it) { scope ->
                scope.tag("environment", config.environment)
                scope.tag("platform", config.platformTag)
                scope.tag("build_type", config.buildTypeTag)
            }
        }.onSuccess {
            val tree = SentryLogTree(
                minBreadcrumbLevel = config.logPolicy.minBreadcrumbLevel,
                minEventLevel = config.logPolicy.minEventLevel,
                minBufferLevel = config.logPolicy.minBufferLevel,
            )
            sentryLogTree = tree
            KLog.plant(tree)
            Sentry.configureScope {
                it.setExtra("platform", config.platformTag)
                it.setExtra("build_type", config.buildTypeTag)
                it.setExtra("release_channel", BuildInfo.releaseChannel)
                // Tags (not extras) so triage can filter issues by the exact
                // commit a build shipped from.
                it.setTag(COMMIT_SHA_KEY, BuildInfo.commitSha)
                it.setTag(COMMIT_BRANCH_KEY, BuildInfo.commitBranch)
            }
            logger.i { scope ->
                scope.extra("environment", config.environment)
                scope.extra("platform", config.platformTag)
                scope.extra("build_type", config.buildTypeTag)
                "Sentry initialized for ${config.environment}"
            }
        }
    }

    override fun setCurrentRoute(route: String) {
        // Best-effort: when Sentry isn't initialized (e.g. disabled
        // environment) configureScope has no scope to mutate, so skip quietly
        // rather than logging on every navigation.
        if (!Sentry.isEnabled()) return
        Sentry.configureScope {
            // Tag = searchable/filterable in the issues list; extra = shown on
            // the event detail.
            it.setTag(ROUTE_KEY, route)
            it.setExtra(ROUTE_KEY, route)
        }
    }

    override fun setSession(sessionId: String) {
        // Best-effort, same scope-persistence reasoning as setCurrentRoute:
        // writing the tag on the scope means a later native crash (turned into
        // an event on next launch) still carries the session it happened in.
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.setTag(SESSION_ID_KEY, sessionId) }
    }

    override fun setInstallId(installId: String) {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope { it.setTag(INSTALL_ID_KEY, installId) }
    }

    override fun setContext(key: String, value: String?) {
        if (!Sentry.isEnabled()) return
        Sentry.configureScope {
            if (value.isNullOrBlank()) it.removeTag(key) else it.setTag(key, value)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun captureUserFeedback(
        message: String,
        kind: FeedbackKind,
        eventId: String?,
        errorCode: Int?,
        attachSessionLog: Boolean,
        screenshots: List<ByteArray>,
    ) {
        val payload = message.trim()
        if (payload.isBlank()) {
            logger.w {
                it.tag(FEEDBACK_KIND_TAG, kind.tag)
                "Ignoring empty feedback payload"
            }
            return
        }

        if (!Sentry.isEnabled()) {
            logger.w {
                it.tag(FEEDBACK_KIND_TAG, kind.tag)
                "Sentry disabled, feedback dropped"
            }
            return
        }

        val isBugReport = kind == FeedbackKind.BugReport
        // The owner writing about their own install is the only caller whose
        // report carries the evidence unconditionally. See
        // [FeedbackKind.isOwnerChannel] for why that is a property of the kind
        // and not a second argument, and Telemetry.captureUserFeedback for why
        // it is not a hole in the promise C13a made to players.
        val ownerChannel = kind.isOwnerChannel
        val wantsLog = ownerChannel || attachSessionLog

        // The legacy User Feedback API only persists feedback attached to an
        // event Sentry has already ingested — an empty or unknown event id is
        // silently dropped on ingest, which is why feedback never surfaced.
        // `eventId` here is our internal KLog id (or null for general feedback),
        // never a real Sentry id, so mint a carrier event via captureMessage and
        // attach the feedback to that. Mirrors Sentry's documented
        // captureMessage → captureUserFeedback flow. The KLog id / error code
        // ride along in the comment for correlation back to the logs.
        // Mint a unique id for this report and stamp it on a LOCAL scope for
        // just the carrier event: beforeSend reads it to fingerprint the event
        // into its own issue (see init). Local scope means none of this leaks
        // onto later events.
        val logDump = if (wantsLog) {
            sentryLogTree?.snapshot()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val feedbackId = Uuid.random().toString()
        val sentryId = Sentry.captureMessage(kind.carrierMessage) { scope ->
            // A player's report carries no breadcrumbs, because a breadcrumb
            // carries the logged event's attributes and `run.end` would put
            // their score on every report whatever the switch said (L74). An
            // owner directive keeps them: they are the minutes leading up to the
            // complaint, and they are the owner's own.
            if (!ownerChannel) scope.clearBreadcrumbs()
            scope.setTag(FEEDBACK_EVENT_TAG, feedbackId)
            // The one thing triage filters on. See [FeedbackKind] for why it is
            // a tag and not part of the message.
            scope.setTag(FEEDBACK_KIND_TAG, kind.tag)
            scope.setTag(FEEDBACK_DIAGNOSTICS_TAG, attachSessionLog.toString())
            // Info, not the default. A report is not an error, and a Sentry
            // issue at error level sorts with the crashes and reads as one in
            // the list, which pulls triage toward it as if something broke.
            scope.level = SentryLevel.INFO
            // The written message goes on the carrier as well as into
            // captureUserFeedback, and the duplication is the fix rather than an
            // oversight. The legacy User Feedback API is the only one this SDK
            // has, and where its comments render depends on the Sentry org's
            // feedback settings; Sodogku had a real report arrive with the log
            // and the screenshot visible on the issue and the typed words
            // nowhere. Extras and attachments are shown on the issue page
            // unconditionally, so the payload lands beside the evidence it
            // explains.
            scope.setExtra(FEEDBACK_MESSAGE_KEY, payload)
            scope.addAttachment(Attachment(payload.encodeToByteArray(), "feedback.txt", "text/plain"))
            if (logDump != null) {
                scope.addAttachment(Attachment(logDump.encodeToByteArray(), "session-log.txt", "text/plain"))
            }
            screenshots.asSequence()
                .filter { it.isNotEmpty() }
                .take(MAX_FEEDBACK_SCREENSHOTS)
                .forEachIndexed { index, bytes ->
                    scope.addAttachment(Attachment(bytes, "screenshot-${index + 1}.jpg", "image/jpeg"))
                }
        }

        val feedback = UserFeedback(sentryId).apply {
            comments = buildString {
                // Build provenance up top so triage can tell which code
                // produced the report without cross-referencing tags —
                // and whether it's already fixed on a later commit.
                append("Build: ${BuildInfo.versionString()} @ ${BuildInfo.commitSha} (${BuildInfo.commitBranch})\n")
                if (isBugReport) {
                    errorCode?.let { append("Error code: $it\n") }
                    eventId?.let { append("Log ID: $it\n") }
                }
                append('\n')
                append(payload)
            }
        }

        Sentry.captureUserFeedback(feedback)

        logger.i { scope ->
            scope.tag(FEEDBACK_KIND_TAG, kind.tag)
            scope.extra("event_id", sentryId.toString())
            if (isBugReport) {
                errorCode?.let { scope.extra("error_code", it) }
            }
            scope.extra("payload_length", payload.length)
            scope.extra("session_log_attached", logDump != null)
            scope.extra("screenshots_attached", screenshots.count { it.isNotEmpty() })
            "Feedback forwarded to Sentry (${kind.tag})"
        }
    }
}

// Scope key for the current navigation route (set via [Telemetry.setCurrentRoute]).
// Shared by the tag and the extra so they read identically in Sentry.
private const val ROUTE_KEY = "route"

// Correlation keys mirrored on the backend (OTel span attributes + log
// fields), so the same value queries Sentry, Tempo, and Loki.
private const val SESSION_ID_KEY = "session_id"
private const val INSTALL_ID_KEY = "install_id"

// Build provenance: the exact commit + branch the installed binary was
// produced from, baked into the generated BuildConfig at build time. Lets
// triage pin a report to code and spot already-fixed-on-main issues.
private const val COMMIT_SHA_KEY = "commit_sha"
private const val COMMIT_BRANCH_KEY = "commit_branch"

// Per-feedback id stamped on the carrier event; `beforeSend` turns it into the
// event fingerprint so each feedback report is its own Sentry issue despite the
// shared "User feedback" / "Bug report" message.
private const val FEEDBACK_EVENT_TAG = "feedback_event"
private const val FEEDBACK_FINGERPRINT = "feedback"

// Whether the player's diagnostics opt-in was on for this report. A tag rather
// than an extra so triage can filter to the reports that have a session log
// before opening any of them. It records what the *player* chose, so an owner
// directive can read `false` here and still carry a log — the two facts are
// different questions and collapsing them would lose the answer to the first.
private const val FEEDBACK_DIAGNOSTICS_TAG = "diagnostics_opt_in"

// Which channel filed the report. The `feedback-triage` skill's only query, and
// the single point of coupling between this file and that routine —
// `FeedbackTriageQueryContractTest` holds the two ends together.
private const val FEEDBACK_KIND_TAG = "feedback_kind"

// The written words, duplicated onto the carrier because the feedback twin does
// not always render them. See captureUserFeedback.
private const val FEEDBACK_MESSAGE_KEY = "feedback_message"

// Enough to show a before and an after and the thing in between. Past that it
// is a screen recording somebody wanted, and the carrier event is not the place
// for one.
private const val MAX_FEEDBACK_SCREENSHOTS = 3

data class SentryRuntimeConfig(
    val dsn: String,
    val environment: String,
    val release: String,
    val sendDefaultPii: Boolean,
    val attachStacktrace: Boolean,
    val tracesSampleRate: Double?,
    val platformTag: String,
    val buildTypeTag: String,
    val logPolicy: LogPolicy,
    val enableAutoSessionTracking: Boolean
) {
    val isEnabled: Boolean get() = dsn.isNotBlank()

    /** Maps this config onto the SDK's [SentryOptions] — the single seam
     *  between our config surface and Sentry's knobs. */
    internal fun applyTo(options: SentryOptions) {
        options.dsn = dsn
        options.environment = environment
        options.release = release
        options.sendDefaultPii = sendDefaultPii
        options.attachStackTrace = attachStacktrace
        options.enableAutoSessionTracking = enableAutoSessionTracking
        // Deliberately never touches options.sampleRate: that knob samples
        // *error events*, and every error/feedback/crash must ship. Traces
        // are the only thing we sample (statistical data, heavy volume).
        tracesSampleRate?.let { options.tracesSampleRate = it }
        // Every feedback carrier event has an identical message
        // ("User feedback" / "Bug report") and no stacktrace, so Sentry
        // would group them all into one issue. Give each its own
        // fingerprint (keyed by a per-feedback id set in
        // captureUserFeedback) so every report is its own issue —
        // individually triageable and resolvable. Other events fall
        // through untouched.
        options.beforeSend = { event ->
            event.getTag(FEEDBACK_EVENT_TAG)?.let { id ->
                event.fingerprint = mutableListOf(FEEDBACK_FINGERPRINT, id)
            }
            event
        }
    }

    data class LogPolicy(
        val minBreadcrumbLevel: LogLevel,
        val minEventLevel: LogLevel,
        /**
         * Lowest level retained in the in-memory ring buffer dumped onto user
         * feedback (null = no buffer). Set below [minBreadcrumbLevel] to keep
         * the fine-grained detail we don't ship — debug builds buffer Verbose+,
         * release buffers Debug+ (skips per-frame Verbose churn).
         */
        val minBufferLevel: LogLevel? = null,
    )

    companion object {
        fun forApp(buildInfo: BuildInfo): SentryRuntimeConfig {
            val platformTag = when (buildInfo.platform) {
                Platform.Android -> "android"
                Platform.iOS -> "ios"
            }
            val buildTypeTag = buildInfo.buildType
            // All platforms / build types report to a single Sentry project.
            // The `environment` tag (releaseChannel-platform-buildType) and the
            // `platform` extra separate debug vs release and iOS vs Android
            // within it, so one DSN is enough. Injected at build time (CI env /
            // local.properties — see loadTelemetryMetadata in build-logic);
            // blank leaves crash reporting disabled.
            val dsn = TelemetryInfo.sentryDsn
            val environment = "${buildInfo.releaseChannel}-$platformTag-$buildTypeTag"
            // Must stay in lockstep with the release-upload step in the iOS
            // deploy lane — Sentry attaches events to releases by exact
            // string match.
            val release = "drop2048@${buildInfo.versionName}+${buildInfo.buildNumber}"
            val tracesSampleRate = if (buildInfo.isDebug) 1.0 else 0.15
            val breadcrumbLevel = if (buildInfo.isDebug) LogLevel.Debug else LogLevel.Info
            return SentryRuntimeConfig(
                dsn = dsn,
                environment = environment,
                release = release,
                sendDefaultPii = false,
                attachStacktrace = true,
                tracesSampleRate = tracesSampleRate,
                platformTag = platformTag,
                buildTypeTag = buildTypeTag,
                logPolicy = LogPolicy(
                    minBreadcrumbLevel = breadcrumbLevel,
                    minEventLevel = LogLevel.Error,
                    minBufferLevel = if (buildInfo.isDebug) LogLevel.Verbose else LogLevel.Debug,
                ),
                enableAutoSessionTracking = true
            )
        }

        fun forIosExtension(buildInfo: BuildInfo): SentryRuntimeConfig {
            val base = forApp(buildInfo)
            val environment = "${buildInfo.releaseChannel}-ios-extension-${buildInfo.buildType}"
            return base.copy(
                environment = environment,
                release = base.release + "-extension",
                tracesSampleRate = if (buildInfo.isDebug) 0.25 else 0.05,
                logPolicy = LogPolicy(
                    minBreadcrumbLevel = LogLevel.Info,
                    minEventLevel = LogLevel.Error
                ),
                enableAutoSessionTracking = false
            )
        }
    }
}
