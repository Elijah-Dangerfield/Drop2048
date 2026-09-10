package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.achievements.db.AchievementFactEntity
import com.dangerfield.drop2048.libraries.achievements.db.AchievementUnlockEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The two achievement tables. Both of their guards are indexes and conflict
 * strategies rather than Kotlin, which is to say both live entirely in the
 * schema this is the first test in the project to open.
 */
@RunWith(RobolectricTestRunner::class)
class AchievementDaoTest {

    private val db = inMemoryAppDatabase()
    private val dao = db.achievementDao()

    @After
    fun tearDown() = db.close()

    /**
     * A run filed twice must be counted once. The unique index on `key` is what
     * enforces it, and `IGNORE` is what turns the collision into silence rather
     * than a crash on the stacked-out sheet.
     */
    @Test
    fun `a fact filed twice is stored once`() = runTest {
        dao.insertFact(fact(key = "run-1", score = 100))
        dao.insertFact(fact(key = "run-1", score = 999))

        val facts = dao.facts()
        assertEquals(1, facts.size)
        assertEquals(100, facts.single().score)
    }

    /**
     * The fold is a replay, so order is load-bearing, and `ORDER BY id` is that
     * order even when two runs finished in the same millisecond — which is the
     * case `ORDER BY endedAt` would get wrong.
     */
    @Test
    fun `facts replay in insertion order even on a tied timestamp`() = runTest {
        dao.insertFact(fact(key = "a", score = 1, endedAt = 500))
        dao.insertFact(fact(key = "b", score = 2, endedAt = 500))
        dao.insertFact(fact(key = "c", score = 3, endedAt = 500))

        assertEquals(listOf(1L, 2L, 3L), dao.facts().map { it.score })
    }

    @Test
    fun `an unlock is never re-announced`() = runTest {
        dao.insertUnlocks(listOf(AchievementUnlockEntity("FIRST_BURST", unlockedAt = 100)))
        dao.insertUnlocks(listOf(AchievementUnlockEntity("FIRST_BURST", unlockedAt = 900)))

        val unlocks = dao.unlocks()
        assertEquals(1, unlocks.size)
        assertEquals(100, unlocks.single().unlockedAt)
    }

    /**
     * The pair has to move together (see `ProvideAchievementDao`): facts without
     * unlocks re-toasts every badge, unlocks without facts leaves badges standing
     * with no history behind them. This is the wipe the `ClearableDao` binding
     * actually performs.
     */
    @Test
    fun `the clearable binding wipes both tables`() = runTest {
        dao.insertFact(fact(key = "a"))
        dao.insertUnlocks(listOf(AchievementUnlockEntity("FIRST_BURST", unlockedAt = 1)))

        ProvideAchievementDao(TestDatabaseProvider(db)).deleteAll()

        assertEquals(emptyList(), dao.facts())
        assertEquals(emptyList(), dao.unlocks())
    }

    private fun fact(
        key: String,
        score: Long = 0,
        endedAt: Long = 1,
    ) = AchievementFactEntity(
        key = key,
        mode = "ENDLESS",
        score = score,
        level = 4,
        blocksPlaced = 60,
        durationMs = 90_000,
        highestTier = 256,
        longestCascade = 3,
        bursts = 1,
        merges = 30,
        boardsCleared = 0,
        stoneBursts = 0,
        wildcardBursts = 0,
        longestDangerRun = 2,
        dailyStreakDays = 0,
        endedAt = endedAt,
    )
}
