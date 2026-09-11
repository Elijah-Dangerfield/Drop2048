package com.dangerfield.drop2048.libraries.telemetry.impl

import com.dangerfield.drop2048.libraries.core.fixed
import com.dangerfield.drop2048.libraries.core.logging.EXTRA_APP_EVENT
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.LogEntry
import com.dangerfield.drop2048.libraries.core.logging.LogId
import com.dangerfield.drop2048.libraries.core.logging.LogTree
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent

/**
 * SPEC 17's day 1 / 3 / 7 return funnel, at its call site: the dispatcher's
 * foreground event. Nothing here calls `logEvent` directly, so deleting the
 * emission from [RetentionReporter] reds these and leaves the rest of the
 * telemetry suite green.
 */
class RetentionReporterTest : CoroutineTest() {

    private val tree = Recording()
    private val cache = InMemoryAppCache()

    @AfterTest
    fun tearDown() = KLog.clearTrees()

    @Test
    fun theFirstForegroundStampsTheInstallAndReportsNothing() = runUnitTest {
        KLog.plant(tree)

        foreground(atMillis = Origin, coldBoot = true)
        runCurrent()

        assertEquals(Origin, cache.snapshot.firstLaunchAt)
        assertTrue(tree.days().isEmpty(), "day zero was reported as a return")
    }

    @Test
    fun comingBackTheNextDayReportsDayOneOnceHoweverOftenTheyOpenIt() = runUnitTest {
        KLog.plant(tree)
        foreground(atMillis = Origin, coldBoot = true)
        runCurrent()

        foreground(atMillis = Origin + Day)
        foreground(atMillis = Origin + Day + Hour)
        foreground(atMillis = Origin + Day + 2 * Hour)
        runCurrent()

        assertEquals(listOf(1), tree.days())
    }

    /**
     * A player who is away for five days returns having been retained *past*
     * day 1 and day 3, and the curve those milestones draw is "were they still
     * here by day N". Firing only on an exact match would drop every return
     * that skipped a milestone, and those are exactly the lapsed players the
     * curve is drawn to count.
     */
    @Test
    fun aReturnThatSkippedMilestonesReportsTheOnesItPassed() = runUnitTest {
        KLog.plant(tree)
        foreground(atMillis = Origin, coldBoot = true)
        runCurrent()

        foreground(atMillis = Origin + 5 * Day)
        runCurrent()

        assertEquals(listOf(1, 3), tree.days())
    }

    @Test
    fun aClockMovedBackwardsReportsNothingRatherThanEverything() = runUnitTest {
        KLog.plant(tree)
        foreground(atMillis = Origin, coldBoot = true)
        runCurrent()

        foreground(atMillis = Origin - 30 * Day)
        runCurrent()

        assertTrue(tree.days().isEmpty(), "a backwards clock farmed the retention funnel")
    }

    private fun foreground(atMillis: Long, coldBoot: Boolean = false) {
        RetentionReporter(
            appCache = cache,
            clock = Clock.fixed(Instant.fromEpochMilliseconds(atMillis)),
            appScope = AppCoroutineScope(dispatchers),
        ).onForeground(AppEvent.OnForeground(isColdBoot = coldBoot))
    }

    private class Recording : LogTree() {
        private val entries = mutableListOf<LogEntry>()

        override fun log(entry: LogEntry): LogId? {
            entries += entry
            return null
        }

        fun days(): List<Int> = entries
            .filter { it.context.extras[EXTRA_APP_EVENT] == "funnel.return_day" }
            .mapNotNull { it.context.extras["day"] as? Int }
    }

    private class InMemoryAppCache : AppCache {
        private val stored = MutableStateFlow(AppData())
        val snapshot: AppData get() = stored.value
        override val updates: Flow<AppData> = stored
        override suspend fun get(): AppData = stored.value
        override suspend fun set(value: AppData) {
            stored.value = value
        }

        override suspend fun clear() {
            stored.value = AppData()
        }
    }

    private companion object {
        const val Hour = 60L * 60 * 1000
        const val Day = 24 * Hour

        /** Comfortably past the epoch, so the backwards-clock case stays positive. */
        const val Origin = 400L * Day
    }
}
