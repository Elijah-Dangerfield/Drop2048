package com.dangerfield.drop2048.libraries.achievements.impl

import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.RunFacts
import com.dangerfield.drop2048.libraries.achievements.RunOutcome
import com.dangerfield.drop2048.libraries.achievements.Stat
import com.dangerfield.drop2048.libraries.achievements.db.AchievementDao
import com.dangerfield.drop2048.libraries.achievements.db.AchievementFactEntity
import com.dangerfield.drop2048.libraries.achievements.db.AchievementUnlockEntity
import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementsRepositoryImplTest {

    private val dao = FakeAchievementDao()
    private val repository = AchievementsRepositoryImpl(dao)

    @Test
    fun aRunIsRecordedAndWhatItUnlockedComesBack() = runTest {
        val unlocked = repository.record(run(merges = 1))

        assertEquals(listOf(AchievementId.FirstMerge), unlocked.map { it.id })
    }

    /**
     * The reason the fact carries a key at all. An at-least-once caller — a
     * retried write, a stacked-out sheet force-quit and reopened — must not walk
     * a lifetime counter up twice.
     */
    @Test
    fun recordingTheSameRunTwiceCountsItOnce() = runTest {
        repository.record(run(merges = 5, endedAt = 100))
        val second = repository.record(run(merges = 5, endedAt = 100))

        assertEquals(5L, repository.state().counters[Stat.TotalMerges])
        assertTrue(second.isEmpty(), "the duplicate announced a badge")
    }

    /**
     * The back-fill. A badge the player's history already earns is granted the
     * next time they finish anything, with the date of the run that earned it,
     * because what has been *announced* is stored separately from what has been
     * *done*.
     */
    @Test
    fun aBadgeThatHistoryAlreadyEarnsIsGrantedOnTheNextRun() = runTest {
        dao.facts += factOf(run(merges = 40, endedAt = 10))

        val unlocked = repository.record(run(endedAt = 20))

        assertTrue(AchievementId.FirstMerge in unlocked.map { it.id })
        assertEquals(10L, repository.state().unlocked.getValue(AchievementId.FirstMerge))
    }

    @Test
    fun theObservedStateFollowsTheFactLog() = runTest {
        repository.record(run(merges = 3, highestTier = 64))

        val state = repository.observe().first()

        assertEquals(3L, state.counters[Stat.TotalMerges])
        assertTrue(state.isUnlocked(AchievementId.SixtyFour))
    }

    @Test
    fun resetClearsBothTables() = runTest {
        repository.record(run(merges = 3))

        repository.reset()

        assertEquals(0L, repository.state().counters[Stat.TotalMerges])
        assertTrue(repository.state().unlocked.isEmpty())
    }

    private fun run(
        merges: Int = 0,
        highestTier: Int = 32,
        endedAt: Long = 1,
    ): RunOutcome = RunOutcome(
        mode = GameMode.ENDLESS,
        score = 0,
        level = 1,
        blocksPlaced = 0,
        durationMs = 0,
        highestTier = highestTier,
        longestCascade = 0,
        bursts = 0,
        merges = merges,
        facts = RunFacts.Empty,
        endedAt = endedAt,
    )

    private fun factOf(outcome: RunOutcome) = AchievementFactEntity(
        id = 1,
        key = outcome.key,
        mode = outcome.mode.name,
        score = outcome.score,
        level = outcome.level,
        blocksPlaced = outcome.blocksPlaced,
        durationMs = outcome.durationMs,
        highestTier = outcome.highestTier,
        longestCascade = outcome.longestCascade,
        bursts = outcome.bursts,
        merges = outcome.merges,
        boardsCleared = 0,
        stoneBursts = 0,
        wildcardBursts = 0,
        longestDangerRun = 0,
        dailyStreakDays = outcome.dailyStreakDays,
        endedAt = outcome.endedAt,
    )
}

/**
 * A DAO with the two behaviours the repository leans on: the unique index on
 * `key`, and `ORDER BY id`. Everything else is a list.
 */
private class FakeAchievementDao : AchievementDao {

    val facts = mutableListOf<AchievementFactEntity>()
    private val unlocks = mutableListOf<AchievementUnlockEntity>()
    private val changes = MutableStateFlow(0)

    override suspend fun insertFact(row: AchievementFactEntity) {
        if (facts.none { it.key == row.key }) {
            facts += row.copy(id = facts.size + 1L)
            changes.value++
        }
    }

    override suspend fun facts(): List<AchievementFactEntity> = facts.sortedBy { it.id }

    override fun observeFacts(): Flow<List<AchievementFactEntity>> = changes.map { facts.sortedBy { row -> row.id } }

    override suspend fun insertUnlocks(rows: List<AchievementUnlockEntity>) {
        rows.forEach { row ->
            if (unlocks.none { it.achievementId == row.achievementId }) unlocks += row
        }
        changes.value++
    }

    override suspend fun unlocks(): List<AchievementUnlockEntity> = unlocks.toList()

    override fun observeUnlocks(): Flow<List<AchievementUnlockEntity>> = changes.map { unlocks.toList() }

    override suspend fun deleteAllFacts() {
        facts.clear()
        changes.value++
    }

    override suspend fun deleteAllUnlocks() {
        unlocks.clear()
        changes.value++
    }
}
