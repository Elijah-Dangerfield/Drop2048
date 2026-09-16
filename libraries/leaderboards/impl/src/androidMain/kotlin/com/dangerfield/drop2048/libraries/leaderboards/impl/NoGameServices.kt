package com.dangerfield.drop2048.libraries.leaderboards.impl

import com.dangerfield.drop2048.libraries.leaderboards.GameServices
import com.dangerfield.drop2048.libraries.leaderboards.GameServicesStatus
import com.dangerfield.drop2048.libraries.leaderboards.SubmitResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Android has no leaderboards yet. SPEC 15 names Play Games alongside Game
 * Center and it is not in this chunk, so this reports
 * [GameServicesStatus.Unavailable] from construction and never changes its mind.
 *
 * That is enough for the whole feature to be silent here: `RealLeaderboards`
 * reads the status before it sends anything, `isOfferable` stays false so no
 * entry point is ever drawn, and held values are collected and never flushed.
 *
 * When Play Games does arrive it replaces this file and nothing else. The seam
 * above is already the right shape for it: `GamesSignInClient` answers
 * [startAuthentication], `LeaderboardsClient.submitScore(id, value)` answers
 * [submit], `AchievementsClient.unlock(id)` answers [reportAchievement], and
 * `getLeaderboardIntent` answers [presentDashboard]. What it does need is a
 * second id per board, because Play mints its own, and `Leaderboard` says where
 * that goes — along with the same treatment for `platformAchievementId`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoGameServices : GameServices {

    private val state = MutableStateFlow(GameServicesStatus.Unavailable)

    override val status: StateFlow<GameServicesStatus> = state.asStateFlow()

    override fun startAuthentication() = Unit

    override suspend fun submit(leaderboardId: String, value: Long): SubmitResult =
        SubmitResult.NotAuthenticated

    override suspend fun reportAchievement(achievementId: String): SubmitResult =
        SubmitResult.NotAuthenticated

    override suspend fun presentDashboard(leaderboardId: String?) = Unit
}
