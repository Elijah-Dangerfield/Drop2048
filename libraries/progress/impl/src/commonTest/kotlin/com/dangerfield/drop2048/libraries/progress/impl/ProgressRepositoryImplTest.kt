package com.dangerfield.drop2048.libraries.progress.impl

import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.RunRecord
import com.dangerfield.drop2048.libraries.progress.db.RunRecordDao
import com.dangerfield.drop2048.libraries.progress.db.RunRecordEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

/**
 * The round trip, and the two things that can only go wrong at this layer: a
 * field mapped to the wrong column, and an enum that no longer names anything.
 *
 * The fold itself is `RunStatsTest`'s; asserting the numbers again through a
 * fake DAO would only make them harder to debug.
 */
class ProgressRepositoryImplTest {

    @Test
    fun record_thenObserve_roundTripsEveryField() = runTest {
        val dao = FakeRunRecordDao()
        val repository = ProgressRepositoryImpl(dao)

        repository.record(run)

        val stats = repository.observeStats().first()
        assertEquals(1, stats.runsPlayed)
        assertEquals(7_400, stats.bestScore)
        assertEquals(1024, stats.highestTier)
        assertEquals(41, stats.totalMerges)
        assertEquals(96, stats.totalBlocksPlaced)
        assertEquals(6, stats.longestCascade)
        assertEquals(2, stats.mostBurstsInARun)
        assertEquals(2, stats.lifetimeBursts)
        assertEquals(240_000, stats.totalPlaytimeMs)
    }

    @Test
    fun bestScore_isZeroBeforeTheFirstRun() = runTest {
        assertEquals(0, ProgressRepositoryImpl(FakeRunRecordDao()).bestScore())
    }

    /**
     * Decision D19: a Daily score cannot own the headline best, because a board
     * everybody played the same seed of is not comparable to an Endless run.
     *
     * The positive control is the second half — the identical score recorded as
     * Endless *does* take the best — so the assertion cannot pass because the
     * repository simply lost the row.
     */
    @Test
    fun bestScore_ignoresDailyRuns() = runTest {
        val repository = ProgressRepositoryImpl(FakeRunRecordDao())
        repository.record(run.copy(score = 4_000))
        repository.record(run.copy(score = 30_000, mode = GameMode.DAILY))

        assertEquals(4_000, repository.bestScore())

        repository.record(run.copy(score = 30_000, mode = GameMode.ENDLESS))
        assertEquals(30_000, repository.bestScore(), "the same score in Endless does take it")
    }

    @Test
    fun bestScore_isTheMaximum() = runTest {
        val repository = ProgressRepositoryImpl(FakeRunRecordDao())
        repository.record(run.copy(score = 100))
        repository.record(run.copy(score = 9_000))
        repository.record(run.copy(score = 4_000))

        assertEquals(9_000, repository.bestScore())
    }

    /**
     * A stored mode this build no longer has must not take the whole page down
     * with it. It reads as endless and the run still counts.
     */
    @Test
    fun unknownMode_readsAsEndless() = runTest {
        val dao = FakeRunRecordDao()
        dao.rows.value = listOf(entityOf(run).copy(mode = "TOURNAMENT"))

        assertEquals(1, ProgressRepositoryImpl(dao).observeStats().first().runsPlayed)
    }

    private companion object {
        val run = RunRecord(
            endedAt = 1_700_000_000_000,
            score = 7_400,
            level = 9,
            blocksPlaced = 96,
            durationMs = 240_000,
            highestTier = 1024,
            cause = "ROW_ZERO_OCCUPIED",
            longestCascade = 6,
            bursts = 2,
            merges = 41,
            mode = GameMode.ENDLESS,
            seed = -42,
        )

        fun entityOf(record: RunRecord) = RunRecordEntity(
            endedAt = record.endedAt,
            score = record.score,
            level = record.level,
            blocksPlaced = record.blocksPlaced,
            durationMs = record.durationMs,
            highestTier = record.highestTier,
            cause = record.cause,
            longestCascade = record.longestCascade,
            bursts = record.bursts,
            merges = record.merges,
            mode = record.mode.name,
            seed = record.seed,
        )
    }
}

private class FakeRunRecordDao : RunRecordDao {
    val rows = MutableStateFlow<List<RunRecordEntity>>(emptyList())

    override suspend fun insert(row: RunRecordEntity): Long {
        val id = rows.value.size + 1L
        rows.value = rows.value + row.copy(id = id)
        return id
    }

    override fun observeAll(): Flow<List<RunRecordEntity>> = rows.map { it.sortedBy(RunRecordEntity::endedAt) }

    override suspend fun all(): List<RunRecordEntity> = rows.value

    /**
     * Mirrors `RunRecordDao.bestScore`'s `WHERE mode = 'ENDLESS'` (decision D19).
     *
     * A fake that reproduced the *old* query would make
     * [ProgressRepositoryImplTest.bestScore_ignoresDailyRuns] pass for the wrong
     * reason. It is worth stating that this is the only coverage the filter has:
     * the real query is SQL Room compiles, and the project has no Room-backed
     * test to run it against on any platform.
     */
    override suspend fun bestScore(): Long? = rows.value
        .filter { it.mode == GameMode.ENDLESS.name }
        .maxOfOrNull { it.score }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
