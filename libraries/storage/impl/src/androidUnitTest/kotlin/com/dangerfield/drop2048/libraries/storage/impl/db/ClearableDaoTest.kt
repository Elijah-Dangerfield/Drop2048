package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.achievements.db.AchievementFactEntity
import com.dangerfield.drop2048.libraries.achievements.db.AchievementUnlockEntity
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import com.dangerfield.drop2048.libraries.progress.db.RunRecordEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/** An [AppDatabaseProvider] over a database the test already owns. */
class TestDatabaseProvider(override val database: AppDatabase) : AppDatabaseProvider

/**
 * "Reset progress" (C11) injects `Set<ClearableDao>` and wipes whatever is in
 * it. L34 made the point that a multibinding nothing reads is never validated;
 * this is the other half — that every member of the set, assembled the way the
 * DI graph assembles it, actually empties the table it stands for.
 *
 * The set is built by hand rather than resolved from the graph because the graph
 * lives in `:apps:compose` and this module may not depend on it. What it does
 * prove is that no provider's `deleteAll` silently does nothing, which is the
 * failure mode a player only discovers after asking for their history to be
 * erased.
 */
@RunWith(RobolectricTestRunner::class)
class ClearableDaoTest {

    private val db = inMemoryAppDatabase()

    @After
    fun tearDown() = db.close()

    @Test
    fun `every clearable provider empties its table`() = runTest {
        val provider = TestDatabaseProvider(db)
        db.runRecordDao().insert(
            RunRecordEntity(
                endedAt = 1,
                score = 10,
                level = 1,
                blocksPlaced = 1,
                durationMs = 1,
                highestTier = 2,
                cause = "ROW_ZERO_OCCUPIED",
                longestCascade = 0,
                bursts = 0,
                merges = 0,
                seed = 1,
            )
        )
        db.achievementDao().insertFact(
            AchievementFactEntity(
                key = "run-1",
                score = 10,
                level = 1,
                blocksPlaced = 1,
                durationMs = 1,
                highestTier = 2,
                longestCascade = 0,
                bursts = 0,
                merges = 0,
                boardsCleared = 0,
                stoneBursts = 0,
                wildcardBursts = 0,
                longestDangerRun = 0,
                endedAt = 1,
            )
        )
        db.achievementDao().insertUnlocks(
            listOf(AchievementUnlockEntity("FIRST_BURST", unlockedAt = 1))
        )

        val clearables: Set<ClearableDao> = setOf(
            ProvideRunRecordDao(provider),
            ProvideAchievementDao(provider),
            ProvideExampleUserDataDao(provider),
        )
        clearables.forEach { it.deleteAll() }

        assertEquals(emptyList(), db.runRecordDao().all())
        assertEquals(emptyList(), db.achievementDao().facts())
        assertEquals(emptyList(), db.achievementDao().unlocks())
    }
}
