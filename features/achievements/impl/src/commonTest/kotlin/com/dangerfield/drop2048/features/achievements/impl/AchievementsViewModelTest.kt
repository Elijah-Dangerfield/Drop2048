package com.dangerfield.drop2048.features.achievements.impl

import com.dangerfield.drop2048.libraries.achievements.Achievement
import com.dangerfield.drop2048.libraries.achievements.AchievementCounters
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.AchievementState
import com.dangerfield.drop2048.libraries.achievements.Achievements
import com.dangerfield.drop2048.libraries.achievements.AchievementsRepository
import com.dangerfield.drop2048.libraries.achievements.RunFacts
import com.dangerfield.drop2048.libraries.achievements.RunOutcome
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementsViewModelTest : CoroutineTest() {

    @Test
    fun everyBadgeInTheCatalogIsOnTheGrid() = runUnitTest {
        val viewModel = viewModel()

        assertEquals(Achievements.catalog.size, viewModel.state.totalCount)
        assertEquals(
            Achievements.catalog.map { it.id },
            viewModel.state.badges.map { it.id },
            "the grid is not in catalog order",
        )
    }

    @Test
    fun aBadgeEarnedWhileTheScreenIsOpenShowsUpWithoutARefresh() = runUnitTest {
        val history = MutableStateFlow(AchievementState.Empty)
        val viewModel = viewModel(FakeRepository(history))
        assertEquals(0, viewModel.state.earnedCount)

        history.value = AchievementState(unlocked = mapOf(AchievementId.FirstMerge to 1L))

        assertEquals(1, viewModel.state.earnedCount)
    }

    @Test
    fun progressIsResolvedAgainstThePlayersCountersAndClampedAtTheTarget() = runUnitTest {
        val outcome = RunOutcome(
            mode = GameMode.ENDLESS,
            score = 0,
            level = 1,
            blocksPlaced = 0,
            durationMs = 0,
            highestTier = 32,
            longestCascade = 3,
            bursts = 0,
            merges = 400,
            facts = RunFacts.Empty,
            endedAt = 1,
        )
        val history = MutableStateFlow(
            AchievementState(counters = AchievementCounters.Empty.fold(outcome)),
        )
        val viewModel = viewModel(FakeRepository(history))

        val firstMerge = viewModel.state.badges.single { it.id == AchievementId.FirstMerge }
        val chain = viewModel.state.badges.single { it.id == AchievementId.ChainOfFive }

        assertEquals(1L, firstMerge.current, "400 merges reported against a target of 1")
        assertEquals(3L, chain.current)
        assertEquals(CascadeProgress, chain.progress)
    }

    /**
     * A row into a dashboard that will not open is worse than no row, and on
     * Android there is no dashboard at all (Play Games is not in v1).
     */
    @Test
    fun theLeaderboardsRowIsDrawnOnlyWhenThereIsSomewhereToGo() = runUnitTest {
        val leaderboards = FakeLeaderboards()
        val viewModel = viewModel(leaderboards = leaderboards)
        assertFalse(viewModel.state.leaderboardsOfferable)

        leaderboards.isOfferable.value = true

        assertTrue(viewModel.state.leaderboardsOfferable)
    }

    @Test
    fun openingTheLeaderboardsAsksForTheAllTimeBoard() = runUnitTest {
        val leaderboards = FakeLeaderboards()
        val viewModel = viewModel(leaderboards = leaderboards)
        viewModel.takeAction(AchievementsAction.OpenLeaderboards)

        assertEquals(listOf<Leaderboard?>(Leaderboard.AllTimeScore), leaderboards.dashboards)
    }

    @Test
    fun selectingABadgeOpensItsDetailAndClosingPutsItBack() = runUnitTest {
        val viewModel = viewModel()

        viewModel.takeAction(AchievementsAction.Select(AchievementId.CleanSweep))

        assertEquals(AchievementId.CleanSweep, viewModel.state.selected?.id)

        viewModel.takeAction(AchievementsAction.CloseDetail)
        assertEquals(null, viewModel.state.selected)
    }

    private fun viewModel(
        repository: AchievementsRepository = FakeRepository(),
        leaderboards: Leaderboards = FakeLeaderboards(),
    ) = AchievementsViewModel(repository, leaderboards)

    private companion object {
        /** Three of the five steps `ChainOfFive` asks for. */
        const val CascadeProgress = 0.6f
    }
}

private class FakeRepository(
    private val history: MutableStateFlow<AchievementState> = MutableStateFlow(AchievementState.Empty),
) : AchievementsRepository {

    override fun observe(): Flow<AchievementState> = history

    override suspend fun state(): AchievementState = history.value

    override suspend fun record(outcome: RunOutcome): List<Achievement> = emptyList()

    override suspend fun reset() = Unit
}

private class FakeLeaderboards : Leaderboards {

    val dashboards = mutableListOf<Leaderboard?>()

    override val isOfferable = MutableStateFlow(false)

    override fun submit(board: Leaderboard, value: Long) = Unit

    override fun reportUnlocked(achievementNames: Set<String>) = Unit

    override fun openDashboard(board: Leaderboard?) {
        dashboards += board
    }
}
