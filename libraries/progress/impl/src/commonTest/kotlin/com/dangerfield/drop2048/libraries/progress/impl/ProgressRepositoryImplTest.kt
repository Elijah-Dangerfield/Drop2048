package com.dangerfield.drop2048.libraries.progress.impl

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
 * The round trip, and the thing that can only go wrong at this layer: a field
 * mapped to the wrong column.
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

    @Test
    fun bestScore_isTheMaximum() = runTest {
        val repository = ProgressRepositoryImpl(FakeRunRecordDao())
        repository.record(run.copy(score = 100))
        repository.record(run.copy(score = 9_000))
        repository.record(run.copy(score = 4_000))

        assertEquals(9_000, repository.bestScore())
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
            seed = -42,
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

    override suspend fun bestScore(): Long? = rows.value.maxOfOrNull { it.score }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
