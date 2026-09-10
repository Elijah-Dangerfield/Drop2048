package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.progress.db.DailyResultEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `daily_result`, whose rules are almost entirely written in SQL rather than in
 * Kotlin: `IGNORE` on conflict is what makes one-attempt-per-day a property of
 * the database, and `MAX(score, :score)` is what stops two finishes racing and
 * letting the worse one win. Neither is visible to a fake that re-implements the
 * DAO's signature.
 */
@RunWith(RobolectricTestRunner::class)
class DailyResultDaoTest {

    private val db = inMemoryAppDatabase()
    private val dao = db.dailyResultDao()

    @After
    fun tearDown() = db.close()

    @Test
    fun `opening a day twice does not reset it`() = runTest {
        dao.insertIfAbsent(day("2026-09-09", seed = 1))
        dao.spendAttempt("2026-09-09")

        val second = dao.insertIfAbsent(day("2026-09-09", seed = 999))

        assertEquals(-1, second)
        val row = dao.forDate("2026-09-09")
        assertEquals(1, row?.attemptsUsed)
        assertEquals(1, row?.seed)
    }

    @Test
    fun `banking a score keeps the better one`() = runTest {
        dao.insertIfAbsent(day("2026-09-09"))

        dao.bankScore("2026-09-09", 5_000)
        dao.bankScore("2026-09-09", 1_200)

        val row = dao.forDate("2026-09-09")
        assertEquals(5_000, row?.score)
        assertTrue(row?.completed == true)
    }

    @Test
    fun `a day nobody opened has no row`() = runTest {
        assertNull(dao.forDate("2026-09-09"))
    }

    /**
     * An `UPDATE` against a date with no row is a no-op in SQLite rather than an
     * error, so nothing here throws — which is exactly why it is worth pinning.
     * A caller that spends an attempt on an unopened day gets silence, and the
     * repository is what has to open the day first.
     */
    @Test
    fun `spending against an unopened day changes nothing`() = runTest {
        dao.spendAttempt("2026-09-09")
        dao.spendRetry("2026-09-09")
        dao.bankScore("2026-09-09", 900)

        assertEquals(emptyList(), dao.all())
    }

    @Test
    fun `counters increment independently`() = runTest {
        dao.insertIfAbsent(day("2026-09-09"))

        dao.spendAttempt("2026-09-09")
        dao.spendRetry("2026-09-09")
        dao.spendRetry("2026-09-09")

        val row = dao.forDate("2026-09-09")
        assertEquals(1, row?.attemptsUsed)
        assertEquals(2, row?.retriesUsed)
        assertFalse(row?.completed == true)
    }

    /**
     * `ORDER BY date` on a text column is only chronological because the dates
     * are ISO. That is the whole reason the column is text and not an epoch, so
     * it is the property the streak fold silently depends on.
     */
    @Test
    fun `iso dates sort chronologically`() = runTest {
        dao.insertIfAbsent(day("2026-10-02"))
        dao.insertIfAbsent(day("2026-09-30"))
        dao.insertIfAbsent(day("2026-10-10"))

        val expected = listOf("2026-09-30", "2026-10-02", "2026-10-10")
        assertEquals(expected, dao.all().map { it.date })
        assertEquals(expected, dao.observeAll().first().map { it.date })
    }

    @Test
    fun `delete all empties the table`() = runTest {
        dao.insertIfAbsent(day("2026-09-09"))

        dao.deleteAll()

        assertEquals(emptyList(), dao.all())
    }

    private fun day(date: String, seed: Long = 7) = DailyResultEntity(
        date = date,
        seed = seed,
        score = 0,
        attemptsUsed = 0,
        completed = false,
        retriesUsed = 0,
    )
}
