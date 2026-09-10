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
 * file exists. `FakeRunRecordDao` filters the same way in Kotlin, so the *rule*
 * has always been covered and the *query* never was — and the query is the half
 * that ships.
 */
@RunWith(RobolectricTestRunner::class)
class RunRecordDaoTest {

    private val db = inMemoryAppDatabase()
    private val dao = db.runRecordDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun `best score ignores daily runs`() = runTest {
        dao.insert(record(score = 100, mode = "ENDLESS"))
        dao.insert(record(score = 900, mode = "DAILY"))
        dao.insert(record(score = 400, mode = "ENDLESS"))

        assertEquals(400, dao.bestScore())
    }

    /**
     * The other half of the filter, and the one a Kotlin fake makes look
     * impossible: a player whose only runs are Dailies has no best score at all,
     * rather than a best of zero or of their best Daily.
     */
    @Test
    fun `best score is null when every run is a daily`() = runTest {
        dao.insert(record(score = 900, mode = "DAILY"))
        dao.insert(record(score = 1200, mode = "DAILY"))

        assertNull(dao.bestScore())
    }

    @Test
    fun `best score is null on an empty table`() = runTest {
        assertNull(dao.bestScore())
    }

    /**
     * `mode` is a free-text column, so the filter is a string comparison and
     * SQLite's `=` is case sensitive for ASCII. A mode written in the wrong case
     * would silently drop out of the best score, which is worth pinning: it is
     * what makes storing the enum *name* rather than its ordinal load-bearing at
     * both ends.
     */
    @Test
    fun `best score matches the mode name exactly`() = runTest {
        dao.insert(record(score = 700, mode = "endless"))

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
        mode: String = "ENDLESS",
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
        mode = mode,
        seed = 42,
    )
}
