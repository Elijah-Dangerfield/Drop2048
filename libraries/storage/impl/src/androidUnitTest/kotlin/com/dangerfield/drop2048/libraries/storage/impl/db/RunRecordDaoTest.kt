package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.progress.db.RunRecordEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The SQL behind `run_record`, run against real SQLite.
 *
 * `bestScore()`'s `WHERE mode = 'ENDLESS'` (decision D19) is the reason this
 * file exists: `FakeRunRecordDao` filtered the same way in Kotlin, so the
 * *rule* was covered and the *query* never was. D27 removed the mode and the
 * filter, and the file stays because the rest of the SQL — the ordering, the
 * generated key, the wipe — was never covered anywhere else either.
 */
@RunWith(RobolectricTestRunner::class)
class RunRecordDaoTest {

    private val db = inMemoryAppDatabase()
    private val dao = db.runRecordDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun `best score is the highest of every run`() = runTest {
        dao.insert(record(score = 100))
        dao.insert(record(endedAt = 2, score = 900))
        dao.insert(record(endedAt = 3, score = 400))

        assertEquals(900, dao.bestScore())
    }

    @Test
    fun `best score is null on an empty table`() = runTest {
        assertNull(dao.bestScore())
    }

    @Test
    fun `rows come back oldest first`() = runTest {
        dao.insert(record(endedAt = 300, score = 3))
        dao.insert(record(endedAt = 100, score = 1))
        dao.insert(record(endedAt = 200, score = 2))

        assertEquals(listOf(1L, 2L, 3L), dao.all().map { it.score })
        assertEquals(listOf(1L, 2L, 3L), dao.observeAll().first().map { it.score })
    }

    @Test
    fun `the primary key is generated rather than taken from the row`() = runTest {
        val first = dao.insert(record(score = 1))
        val second = dao.insert(record(score = 2))

        assertEquals(first + 1, second)
        assertEquals(listOf(first, second), dao.all().map { it.id })
    }

    @Test
    fun `delete all empties the table`() = runTest {
        dao.insert(record(score = 1))
        dao.insert(record(score = 2))

        dao.deleteAll()

        assertEquals(emptyList(), dao.all())
        assertNull(dao.bestScore())
    }

    private fun record(
        endedAt: Long = 1,
        score: Long = 0,
    ) = RunRecordEntity(
        endedAt = endedAt,
        score = score,
        level = 3,
        blocksPlaced = 40,
        durationMs = 60_000,
        highestTier = 128,
        cause = "ROW_ZERO_OCCUPIED",
        longestCascade = 2,
        bursts = 0,
        merges = 12,
        seed = 42,
    )
}
