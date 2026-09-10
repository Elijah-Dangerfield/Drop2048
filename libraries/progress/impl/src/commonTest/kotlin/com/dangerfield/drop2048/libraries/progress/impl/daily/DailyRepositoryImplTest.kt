package com.dangerfield.drop2048.libraries.progress.impl.daily

import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.gameconfig.DailyChallengeEnabled
import com.dangerfield.drop2048.libraries.gameconfig.RewardedDailyRetriesPerDay
import com.dangerfield.drop2048.libraries.progress.daily.DailyAttempt
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryAd
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryResult
import com.dangerfield.drop2048.libraries.progress.daily.ProEntitlement
import com.dangerfield.drop2048.libraries.progress.daily.RewardOutcome
import com.dangerfield.drop2048.libraries.progress.daily.dailySeedFor
import com.dangerfield.drop2048.libraries.progress.db.DailyResultDao
import com.dangerfield.drop2048.libraries.progress.db.DailyResultEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The rules that only exist once the ledger does: one attempt a day, a Pro
 * second, a bought third, and a day boundary the app can cross while it is open.
 */
@OptIn(ExperimentalTime::class)
class DailyRepositoryImplTest {

    @Test
    fun theFirstAttemptOfADayIsGrantedOnTheDaysSeed() = runTest {
        val repository = repository()

        val attempt = repository.startAttempt()

        assertIs<DailyAttempt.Granted>(attempt)
        assertEquals(LocalDate(2026, 9, 9), attempt.date)
        assertEquals(dailySeedFor(LocalDate(2026, 9, 9)), attempt.seed)
        assertEquals(1, attempt.attemptNumber)
    }

    /** SPEC 14's whole rule. */
    @Test
    fun theSecondAttemptOfADayIsRefused() = runTest {
        val repository = repository()
        repository.startAttempt()

        assertIs<DailyAttempt.NoAttemptsLeft>(repository.startAttempt())
    }

    /**
     * The attempt is spent when it starts, so killing the app instead of
     * finishing does not hand back a fresh board.
     */
    @Test
    fun anAbandonedAttemptIsStillSpent() = runTest {
        val dao = FakeDailyResultDao()
        repository(dao).startAttempt()

        assertIs<DailyAttempt.NoAttemptsLeft>(repository(dao).startAttempt())
    }

    /** SPEC 12: Pro gets two. */
    @Test
    fun proGetsTwoAttempts() = runTest {
        val repository = repository(pro = true)

        assertIs<DailyAttempt.Granted>(repository.startAttempt())
        assertIs<DailyAttempt.Granted>(repository.startAttempt())
        assertIs<DailyAttempt.NoAttemptsLeft>(repository.startAttempt())
    }

    @Test
    fun theKillSwitchRefusesBeforeItTouchesTheLedger() = runTest {
        val dao = FakeDailyResultDao()

        assertIs<DailyAttempt.Disabled>(repository(dao, enabled = false).startAttempt())
        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun finishingBanksTheScoreAndCompletesTheDay() = runTest {
        val repository = repository()
        val attempt = repository.startAttempt() as DailyAttempt.Granted

        repository.recordAttempt(attempt.date, score = 4_200)

        val status = repository.status()
        assertTrue(status.completed)
        assertEquals(4_200, status.result?.score)
        assertEquals(1, status.streak.current)
    }

    /**
     * SPEC 14 says the mode is scored on final score and does not say which of
     * two attempts counts. The better one does, because otherwise paying for a
     * retry buys the chance to make your day worse.
     */
    @Test
    fun aSecondAttemptKeepsTheBetterScore() = runTest {
        val repository = repository(pro = true)
        val first = repository.startAttempt() as DailyAttempt.Granted
        repository.recordAttempt(first.date, score = 9_000)
        val second = repository.startAttempt() as DailyAttempt.Granted
        repository.recordAttempt(second.date, score = 300)

        assertEquals(9_000, repository.status().result?.score)
    }

    /**
     * An attempt begun at 23:58 UTC and finished after midnight scores against
     * the board it was played on, not the one that has just arrived.
     */
    @Test
    fun anAttemptThatCrossesMidnightScoresAgainstItsOwnDay() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-09T23:58:00Z"))
        val repository = repository(clock = clock)
        val attempt = repository.startAttempt() as DailyAttempt.Granted

        clock.now = Instant.parse("2026-09-10T00:03:00Z")
        repository.recordAttempt(attempt.date, score = 1_500)

        val history = repository.history().associateBy { it.date }
        assertEquals(1_500, history[LocalDate(2026, 9, 9)]?.score)
        assertTrue(history[LocalDate(2026, 9, 10)] == null, "the new day must still be untouched")
        assertFalse(repository.status().completed)
    }

    /** The new day is playable the instant the old one ends. */
    @Test
    fun theNextDayIsAFreshAttempt() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-09T12:00:00Z"))
        val repository = repository(clock = clock)
        repository.startAttempt()

        clock.now = Instant.parse("2026-09-10T00:00:00Z")

        val next = repository.startAttempt()
        assertIs<DailyAttempt.Granted>(next)
        assertEquals(LocalDate(2026, 9, 10), next.date)
    }

    /**
     * A device clock moved backwards must not destroy anything or hand out a
     * second go at a day already spent.
     */
    @Test
    fun aClockMovedBackwardsCostsNothingAndGrantsNothing() = runTest {
        val clock = MutableClock(Instant.parse("2026-09-09T12:00:00Z"))
        val repository = repository(clock = clock)
        val attempt = repository.startAttempt() as DailyAttempt.Granted
        repository.recordAttempt(attempt.date, score = 7_000)

        clock.now = Instant.parse("2026-09-09T01:00:00Z")

        assertIs<DailyAttempt.NoAttemptsLeft>(repository.startAttempt())
        assertEquals(7_000, repository.status().result?.score)
    }

    /**
     * The rewarded retry with no ad system behind it (C10). Unavailable, never
     * declined: only a deliberate dismissal is allowed to withhold a reward, and
     * "there is no ad network yet" is not one.
     */
    @Test
    fun theRetryIsUnavailableWhileThereIsNoAdSystem() = runTest {
        val repository = repository()
        repository.startAttempt()

        assertIs<DailyRetryResult.Unavailable>(repository.grantRetry())
    }

    @Test
    fun aWatchedAdBuysExactlyOneMoreAttempt() = runTest {
        val repository = repository(ad = { RewardOutcome.Earned })
        repository.startAttempt()

        val granted = repository.grantRetry()
        assertIs<DailyRetryResult.Granted>(granted)
        assertEquals(2, granted.attemptsAllowed)
        assertIs<DailyAttempt.Granted>(repository.startAttempt())
        assertIs<DailyAttempt.NoAttemptsLeft>(repository.startAttempt())
    }

    /** `ads.rewarded.dailyRetriesPerDay` defaults to 1 (SPEC 12). */
    @Test
    fun theRetryCapIsCountedOnDisk() = runTest {
        val dao = FakeDailyResultDao()
        val repository = repository(dao, ad = { RewardOutcome.Earned })
        repository.startAttempt()
        repository.grantRetry()
        repository.startAttempt()

        assertIs<DailyRetryResult.NoneLeft>(repository(dao, ad = { RewardOutcome.Earned }).grantRetry())
    }

    @Test
    fun closingTheAdEarlyIsTheOnlyThingThatDeclines() = runTest {
        val repository = repository(ad = { RewardOutcome.Dismissed })
        repository.startAttempt()

        assertIs<DailyRetryResult.Declined>(repository.grantRetry())
    }

    @Test
    fun thereIsNothingToRetryWhileTheDayIsStillPlayable() = runTest {
        assertIs<DailyRetryResult.NothingToRetry>(repository(ad = { RewardOutcome.Earned }).grantRetry())
    }

    @Test
    fun observeReportsTheDayAndItsCountdown() = runTest {
        val status = repository(clock = MutableClock(Instant.parse("2026-09-09T20:00:00Z")))
            .observe()
            .first()

        assertEquals(LocalDate(2026, 9, 9), status.date)
        assertEquals(dailySeedFor(LocalDate(2026, 9, 9)), status.seed)
        assertEquals(4.hours, status.resetsIn)
        assertEquals(1, status.attemptsRemaining)
    }

    private fun repository(
        dao: FakeDailyResultDao = FakeDailyResultDao(),
        clock: Clock = MutableClock(Instant.parse("2026-09-09T12:00:00Z")),
        pro: Boolean = false,
        enabled: Boolean = true,
        ad: suspend () -> RewardOutcome = { RewardOutcome.Unavailable },
    ) = DailyRepositoryImpl(
        dao = dao,
        clock = clock,
        pro = ProEntitlement { pro },
        retryAd = DailyRetryAd { ad() },
        featureEnabled = DailyChallengeEnabled(configMap(enabled)),
        retriesPerDay = RewardedDailyRetriesPerDay(configMap(enabled)),
    )

    private fun configMap(dailyEnabled: Boolean) = object : AppConfigMap() {
        override val map: Map<String, *> =
            mapOf("feature" to mapOf("dailyChallenge" to dailyEnabled))
    }
}

private class MutableClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

/**
 * The `daily_result` table, in memory, with the two behaviours the real DAO's
 * rules depend on: insert-if-absent and update-in-place.
 */
private class FakeDailyResultDao : DailyResultDao {

    private val rows = MutableStateFlow<Map<String, DailyResultEntity>>(emptyMap())

    override suspend fun insertIfAbsent(row: DailyResultEntity): Long {
        if (rows.value.containsKey(row.date)) return -1
        rows.value = rows.value + (row.date to row)
        return 1
    }

    override suspend fun forDate(date: String): DailyResultEntity? = rows.value[date]

    override suspend fun spendAttempt(date: String) = update(date) {
        it.copy(attemptsUsed = it.attemptsUsed + 1)
    }

    override suspend fun bankScore(date: String, score: Long) = update(date) {
        it.copy(completed = true, score = maxOf(it.score, score))
    }

    override suspend fun spendRetry(date: String) = update(date) {
        it.copy(retriesUsed = it.retriesUsed + 1)
    }

    override fun observeAll(): Flow<List<DailyResultEntity>> =
        rows.map { it.values.sortedBy { row -> row.date } }

    override suspend fun all(): List<DailyResultEntity> = rows.value.values.sortedBy { it.date }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    private fun update(date: String, change: (DailyResultEntity) -> DailyResultEntity) {
        val existing = rows.value[date] ?: return
        rows.value = rows.value + (date to change(existing))
    }
}
