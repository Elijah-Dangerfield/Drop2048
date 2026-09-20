package com.dangerfield.drop2048.admin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/** A one-line success/error banner shown under the connection panel. */
internal data class Status(val ok: Boolean, val message: String)

/**
 * A failed write, kept until the operator dismisses it. The transient status
 * line gets overwritten by the next action; this list doesn't — so a rejected
 * write can never silently "snap back" (the reload refreshes server truth, but
 * the attempted value survives here).
 */
internal data class ErrorEntry(
    val time: String,
    val operation: String,
    val attempted: String?,
    val message: String,
)

internal fun nowTimeLabel(): String = js("new Date().toLocaleTimeString()") as String

/**
 * Everything a view needs to make a write: the api, which environment it hits,
 * and the shared safety plumbing. [confirmWrite] is the only mutation path in
 * the console — it puts a before→after confirm sheet in front of the operator,
 * and on failure records a persistent [ErrorEntry].
 *
 * Reloads on both success and failure: a rejected write can still have changed
 * state (or the operator's view of it can be stale), so we always re-fetch so
 * the flag/rule list reflects what the server actually holds.
 */
internal class AdminCtx(
    val api: AdminApi,
    val scope: CoroutineScope,
    val envName: String,
    val isProd: Boolean,
    val setStatus: (Status) -> Unit,
    val reload: () -> Unit,
    private val requestConfirm: (PendingWrite) -> Unit,
    private val reportError: (ErrorEntry) -> Unit,
) {
    fun confirmWrite(
        title: String,
        flagPath: String?,
        before: String,
        after: String,
        success: String,
        warning: String? = null,
        block: suspend () -> Unit,
    ) {
        requestConfirm(
            PendingWrite(
                title = title,
                envName = envName,
                isProd = isProd,
                flagPath = flagPath,
                before = before,
                after = after,
                warning = warning,
                requireEnvTyping = isProd && warning != null,
                onConfirm = {
                    scope.launch {
                        Catching { block() }
                            .onSuccess { setStatus(Status(true, success)) }
                            .onFailure { error ->
                                val message = error.message ?: "Request failed"
                                reportError(ErrorEntry(nowTimeLabel(), title, after, message))
                                setStatus(Status(false, message))
                            }
                        reload()
                    }
                },
            ),
        )
    }
}

@Suppress("TooGenericExceptionCaught")
internal fun parseJsonOrNull(raw: String): JsonElement? =
    Catching { adminJson.parseToJsonElement(raw.trim()) }.getOrNull()

internal fun csvSet(raw: String): Set<String>? =
    raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet().ifEmpty { null }

internal fun randomUuid(): String = js("crypto.randomUUID()") as String

/**
 * A confirm prompt for values that hit every user hard — locking them out,
 * forcing an upgrade, or invalidating scores that are already on the board.
 * Returns the warning to show, or null when the change is routine.
 *
 * Keyed on (path, value) because most of these are value-specific
 * (`maintenanceMode = "blocking"` is a lockout; `"off"` is harmless). Two are
 * not, and warn on any value: raising the minimum supported version, and
 * [DIGEST_MOVING_PATHS].
 *
 * ### Why `level.blocksPerLevel` is on this list and the other gameplay keys are not
 *
 * The distinction is measured, not stylistic. The speed curve is a pacing dial
 * with no effect on any outcome column (L28/D9); the spawn table sets the tier ceiling and moves the median level by at
 * most one (L19). All three are safe to turn live.
 *
 * `level.blocksPerLevel` feeds level advancement, so it **moves the pinned
 * determinism digest**. Every seed-attached bug report recorded under the old
 * value replays as a different run under the new
 * one — and it still replays, which is the expensive kind of wrong. D9 keeps the
 * key in reserve rather than excluding it, so the console's job is to make
 * changing it a deliberate act. On prod the warning also forces the operator to
 * type the environment name before Confirm arms.
 */
internal fun dangerousWarning(path: String, value: String): String? {
    val v = value.trim()
    return when {
        path == "upgrade.maintenanceMode" && v == "\"blocking\"" ->
            "This sets maintenance mode to BLOCKING — it locks ALL users out of the app. Continue?"
        path == "upgrade.maintenanceMode" && v == "\"banner\"" ->
            "This shows a maintenance banner to ALL users. Continue?"
        path == "upgrade.minSupportedVersionCode" ->
            "Raising the minimum supported version force-upgrades every user below it. Double-check the number. Continue?"
        path in DIGEST_MOVING_PATHS ->
            "This MOVES THE DETERMINISM DIGEST. Every seed-attached bug report recorded under the old " +
                "value will replay as a DIFFERENT run — silently, because it " +
                "still replays. Only the clock behaves this way; the speed curve and the spawn table were " +
                "both measured safe to change live. Continue?"
        else -> null
    }
}

/**
 * Config paths that feed level advancement and therefore change what a seed
 * replays to. One entry today; it is a set so the next one is a one-line change
 * rather than a second `when` branch someone forgets to add.
 */
internal val DIGEST_MOVING_PATHS = setOf("level.blocksPerLevel")

/** Compact, human-readable rendering of a JSON value for tables (no pretty-print). */
internal fun JsonElement?.inline(): String = this?.toString() ?: "—"

/**
 * A targeting rule as a plain-English clause: "app version > 1.0.1 and country
 * in US/CA". The value it sets is shown separately by the caller.
 */
internal fun conditionsSentence(c: RuleConditions): String {
    val parts = buildList {
        c.platforms?.let { add("platform in ${it.joinToString("/")}") }
        appVersionPhrase(c)?.let { add(it) }
        c.minVersionCode?.let { add("build ≥ $it") }
        c.maxVersionCode?.let { add("build ≤ $it") }
        c.countries?.let { add("country in ${it.joinToString("/")}") }
        c.locales?.let { add("locale in ${it.joinToString("/")}") }
        c.userAllow?.let { add("user in allowlist (${it.size})") }
        c.userDeny?.let { add("user not in denylist (${it.size})") }
        c.rolloutPercent?.let { add("rollout $it%") }
    }
    return if (parts.isEmpty()) "everyone" else parts.joinToString(" and ")
}

private fun appVersionPhrase(c: RuleConditions): String? {
    val min = c.minAppVersion?.takeUnless { it.isBlank() }
    val max = c.maxAppVersion?.takeUnless { it.isBlank() }
    val minOp = if (c.minAppVersionInclusive) "≥" else ">"
    val maxOp = if (c.maxAppVersionInclusive) "≤" else "<"
    return when {
        min != null && max != null -> "app version $minOp $min and $maxOp $max"
        min != null -> "app version $minOp $min"
        max != null -> "app version $maxOp $max"
        else -> null
    }
}
