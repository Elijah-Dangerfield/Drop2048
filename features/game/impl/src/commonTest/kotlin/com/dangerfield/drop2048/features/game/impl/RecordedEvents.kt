package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.core.logging.EXTRA_APP_EVENT
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.LogEntry
import com.dangerfield.drop2048.libraries.core.logging.LogId
import com.dangerfield.drop2048.libraries.core.logging.LogTree

/**
 * The app events a scenario actually emitted, captured the way the real
 * pipeline captures them: a planted [LogTree] reading [EXTRA_APP_EVENT].
 *
 * This is here because C8's whole risk is events with no call site (Sodogku's
 * S15, `BUILD-PLAN.md`). A test that calls `logEvent` itself and asserts the
 * attributes proves the extension works and proves nothing about the game, so
 * every test in this package drives the **ViewModel** and reads what came out
 * of this tree — delete a `logEvent` line from `GameViewModel` and the caller's
 * test goes red while nothing in `:libraries:core` notices (L55).
 */
internal class RecordedEvents : LogTree() {

    private val entries = mutableListOf<LogEntry>()

    override fun log(entry: LogEntry): LogId? {
        if (entry.context.extras[EXTRA_APP_EVENT] != null) entries += entry
        return null
    }

    fun named(name: String): List<Map<String, Any?>> = entries
        .filter { it.context.extras[EXTRA_APP_EVENT] == name }
        .map { it.context.extras - EXTRA_APP_EVENT }

    fun names(): List<String> = entries.mapNotNull { it.context.extras[EXTRA_APP_EVENT] as? String }
}

/**
 * Plants a [RecordedEvents] for the body and takes it back down afterwards.
 *
 * `KLog` is a global, so a tree left planted by one test is a tree collecting
 * another test's events — and the failure is order-dependent, which is the
 * worst kind to read.
 */
internal fun <T> recordingEvents(body: (RecordedEvents) -> T): T {
    val tree = RecordedEvents()
    KLog.plant(tree)
    return try {
        body(tree)
    } finally {
        KLog.uproot(tree)
    }
}
