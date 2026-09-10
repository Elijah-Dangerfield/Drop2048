package com.dangerfield.drop2048.libraries.progress.impl.daily

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.gameconfig.DailyChallengeEnabled
import com.dangerfield.drop2048.libraries.gameconfig.RewardedDailyRetriesPerDay
import com.dangerfield.drop2048.libraries.progress.daily.DailyAttempt
import com.dangerfield.drop2048.libraries.progress.daily.DailyRepository
import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryAd
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStatus
import com.dangerfield.drop2048.libraries.progress.daily.ProEntitlement
import com.dangerfield.drop2048.libraries.progress.daily.RewardOutcome
import com.dangerfield.drop2048.libraries.progress.daily.dailyDayOf
import com.dangerfield.drop2048.libraries.progress.daily.dailySeedFor
import com.dangerfield.drop2048.libraries.progress.db.DailyResultDao
import com.dangerfield.drop2048.libraries.progress.db.DailyResultEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Room-backed [DailyRepository].
 *
 * Reads the whole `daily_result` table for every answer. That is one row per day
 * the player has ever opened — a few hundred after a couple of years — and it is
 * what makes the streak a fold rather than a counter. A query that fetched only
 * the recent tail would be a cache, and a cache is the thing this design exists
 * to avoid.
 *
 * ### The one-attempt rule lives in two places, on purpose
 *
 * The primary key refuses a second row for a date, and [startAttempt] refuses to
 * spend an attempt the day does not have. Neither is redundant: the key is what
 * survives two writers racing, and the check is what makes the refusal
 * *answerable* — a screen has to be able to say "no attempts left" rather than
 * discover it by having a write silently do nothing.
 */
@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DailyRepositoryImpl(
    private val dao: DailyResultDao,
    private val clock: Clock,
    private val pro: ProEntitlement,
    private val retryAd: DailyRetryAd,
    private val featureEnabled: DailyChallengeEnabled,
    private val retriesPerDay: RewardedDailyRetriesPerDay,
) : DailyRepository {

    override fun observe(): Flow<DailyStatus> = dayChanges()
        .flatMapLatest { day -> dao.observeAll().map { rows -> statusOn(day, rows.toResults()) } }
        .distinctUntilChanged()

    override suspend fun status(): DailyStatus = statusOn(today(), dao.all().toResults())

    override suspend fun startAttempt(): DailyAttempt {
        if (!featureEnabled()) return DailyAttempt.Disabled
        val day = today()
        val seed = dailySeedFor(day)
        dao.insertIfAbsent(
            DailyResultEntity(
                date = day.toString(),
                seed = seed,
                score = 0,
                attemptsUsed = 0,
                completed = false,
                retriesUsed = 0,
            )
        )
        val row = dao.forDate(day.toString())?.toResult() ?: return DailyAttempt.NoAttemptsLeft
        if (row.attemptsUsed >= allowanceFor(row)) return DailyAttempt.NoAttemptsLeft

        dao.spendAttempt(day.toString())
        return DailyAttempt.Granted(
            date = day,
            seed = row.seed,
            attemptNumber = row.attemptsUsed + 1,
        )
    }

    /**
     * Idempotent by construction: it only ever raises `completed` and takes a
     * `MAX` over the score, so a finish reported twice — which the ViewModel can
     * do if a stacked-out run is left on screen and the app is killed and
     * relaunched onto it — costs nothing.
     */
    override suspend fun recordAttempt(date: LocalDate, score: Long) {
        dao.bankScore(date.toString(), score)
    }

    override suspend fun grantRetry(): DailyRetryResult {
        val day = today()
        val row = dao.forDate(day.toString())?.toResult() ?: return DailyRetryResult.NothingToRetry
        if (row.attemptsUsed < allowanceFor(row)) return DailyRetryResult.NothingToRetry
        if (row.retriesUsed >= retriesPerDay()) return DailyRetryResult.NoneLeft

        return when (retryAd.show()) {
            RewardOutcome.Dismissed -> DailyRetryResult.Declined
            RewardOutcome.Unavailable -> DailyRetryResult.Unavailable
            RewardOutcome.Earned -> {
                dao.spendRetry(day.toString())
                val updated = dao.forDate(day.toString())?.toResult()
                DailyRetryResult.Granted(updated?.let { allowanceFor(it) } ?: allowanceFor(row) + 1)
            }
        }
    }

    override suspend fun history(): List<DailyResult> = dao.all().mapNotNull { it.toResult() }

    override suspend fun reset() {
        dao.deleteAll()
    }

    private fun statusOn(day: LocalDate, results: Map<LocalDate, DailyResult>): DailyStatus {
        val row = results[day]
        val allowed = row?.let { allowanceFor(it) } ?: baseAllowance()
        return DailyStatus(
            date = day,
            seed = dailySeedFor(day),
            result = row,
            streak = streakFrom(day, results.values),
            attemptsAllowed = allowed,
            retryOffered = row != null &&
                row.attemptsUsed >= allowed &&
                row.retriesUsed < retriesPerDay(),
            resetsIn = untilNextUtcDay(clock.now()),
            enabled = featureEnabled(),
        )
    }

    /** SPEC 12: one attempt, two for Pro, plus one for every retry already bought. */
    private fun allowanceFor(row: DailyResult): Int = baseAllowance() + row.retriesUsed

    private fun baseAllowance(): Int = if (pro.isPro()) ProAttempts else FreeAttempts

    /**
     * Emits today's UTC date, then again each time it rolls over.
     *
     * A screen left open across 00:00 UTC swaps to the new board on its own. The
     * delay is recomputed from the clock every pass rather than being a fixed 24
     * hours, so a device whose clock is corrected while the app is open re-aligns
     * on the next tick instead of drifting for the life of the process.
     */
    private fun dayChanges(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            emit(dailyDayOf(now))
            delay(untilNextUtcDay(now))
        }
    }

    private fun today(): LocalDate = dailyDayOf(clock.now())

    private companion object {
        const val FreeAttempts = 1
        const val ProAttempts = 2
    }
}

/**
 * A row whose date no longer parses is dropped rather than guessed at.
 *
 * It can only come from a build we no longer have, and the two ways to be wrong
 * are not symmetric: a dropped row costs a day of streak the player can see,
 * while a row invented at the wrong date silently shifts every calculation that
 * walks past it.
 */
internal fun DailyResultEntity.toResult(): DailyResult? {
    val parsed = Catching { LocalDate.parse(date) }.getOrNull() ?: return null
    return DailyResult(
        date = parsed,
        seed = seed,
        score = score,
        attemptsUsed = attemptsUsed,
        completed = completed,
        retriesUsed = retriesUsed,
    )
}

internal fun List<DailyResultEntity>.toResults(): Map<LocalDate, DailyResult> =
    mapNotNull { it.toResult() }.associateBy { it.date }
