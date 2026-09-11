package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.progress.daily.DailyAttempt
import com.dangerfield.drop2048.libraries.progress.daily.DailyRepository
import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStatus
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * Hand-rolled, per `docs/practices/testing.md`, and only as much of each seam as
 * the debug menu touches: the streak it shows, and the reset it offers.
 */
internal class StubDailyRepository : DailyRepository {
    var resets = 0
        private set

    override fun observe(): Flow<DailyStatus> = MutableStateFlow(today)

    override suspend fun status(): DailyStatus = today

    override suspend fun startAttempt(): DailyAttempt = DailyAttempt.NoAttemptsLeft

    override suspend fun recordAttempt(date: LocalDate, score: Long) = Unit

    override suspend fun grantRetry(): DailyRetryResult = DailyRetryResult.Unavailable

    override suspend fun history(): List<DailyResult> = emptyList()

    override suspend fun reset() {
        resets += 1
    }

    private val today = DailyStatus(
        date = LocalDate(2026, 9, 9),
        seed = 1,
        result = null,
        streak = DailyStreak(current = 3, best = 5),
        attemptsAllowed = 1,
        retryOffered = false,
        resetsIn = Duration.ZERO,
        enabled = true,
    )
}

internal class StubAppCache(initial: AppData = AppData()) : AppCache {
    private val state = MutableStateFlow(initial)

    val value: AppData get() = state.value

    override val updates: Flow<AppData> = state

    override suspend fun get(): AppData = state.value

    override suspend fun set(value: AppData) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AppData()
    }
}
