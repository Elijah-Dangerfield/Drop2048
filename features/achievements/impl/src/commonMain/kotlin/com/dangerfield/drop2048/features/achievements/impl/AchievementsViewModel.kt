package com.dangerfield.drop2048.features.achievements.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.libraries.achievements.AchievementGroup
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.AchievementState
import com.dangerfield.drop2048.libraries.achievements.Achievements
import com.dangerfield.drop2048.libraries.achievements.AchievementsRepository
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.flowroutines.collectIn
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import me.tatarka.inject.annotations.Inject

/**
 * The badge grid, and the one entry point the platform leaderboards have.
 *
 * The history comes from [AchievementsRepository] and is **observed** rather
 * than read once: the fold re-runs whenever a fact or an unlock lands, so a
 * badge earned while this screen is open — the stacked-out sheet is usually
 * still behind it — shows up without a refresh.
 *
 * [Leaderboards.isOfferable] decides whether the leaderboards row is drawn at
 * all. It is false on Android and false while Game Center is unresolved, and a
 * row that leads nowhere is worse than no row.
 */
@Inject
class AchievementsViewModel(
    private val achievements: AchievementsRepository,
    private val leaderboards: Leaderboards,
) : SEAViewModel<AchievementsState, AchievementsEvent, AchievementsAction>(
    initialStateArg = AchievementsState(),
) {

    init {
        takeAction(AchievementsAction.Load)
        achievements.observe().collectIn(viewModelScope) {
            takeAction(AchievementsAction.HistoryChanged(it))
        }
        leaderboards.isOfferable.collectIn(viewModelScope) {
            takeAction(AchievementsAction.LeaderboardsOfferable(it))
        }
    }

    override suspend fun handleAction(action: AchievementsAction) {
        when (action) {
            AchievementsAction.Load -> action.load()
            is AchievementsAction.HistoryChanged -> action.updateState {
                it.copy(loading = false, badges = action.state.toBadges())
            }

            is AchievementsAction.LeaderboardsOfferable -> action.updateState {
                it.copy(leaderboardsOfferable = action.offerable)
            }

            AchievementsAction.Back -> sendEvent(AchievementsEvent.NavigateBack)
            is AchievementsAction.Select -> action.updateState { it.copy(selectedId = action.id) }
            AchievementsAction.CloseDetail -> action.updateState { it.copy(selectedId = null) }
            AchievementsAction.OpenLeaderboards -> leaderboards.openDashboard(Leaderboard.AllTimeScore)
        }
    }

    /**
     * The first paint.
     *
     * The history is read here as well as observed, because a `Flow` off a Room
     * query is fine but the screen should not sit on a spinner while the first
     * emission arrives on an empty table.
     */
    private suspend fun AchievementsAction.load() {
        val history = Catching { achievements.state() }
            .logOnFailure { "Failed to read achievement history" }
            .getOrNull()
            ?: AchievementState.Empty

        updateState { it.copy(loading = false, badges = history.toBadges()) }
    }
}

/** Catalog order, with each badge's progress resolved against the player's counters. */
private fun AchievementState.toBadges(): List<Badge> = Achievements.catalog.map { achievement ->
    Badge(
        id = achievement.id,
        group = Achievements.groupOf(achievement.id),
        unlocked = isUnlocked(achievement.id),
        progress = achievement.progress(counters),
        current = achievement.currentFor(counters),
        target = achievement.target,
    )
}

/** One badge, as the grid and the detail dialog need it. */
data class Badge(
    val id: AchievementId,
    val group: AchievementGroup,
    val unlocked: Boolean,
    /** 0.0 to 1.0. */
    val progress: Float,
    val current: Long,
    val target: Long,
)

data class AchievementsState(
    val loading: Boolean = true,
    val badges: List<Badge> = emptyList(),
    /** The badge whose detail dialog is open. */
    val selectedId: AchievementId? = null,
    val leaderboardsOfferable: Boolean = false,
) {
    val earnedCount: Int get() = badges.count { it.unlocked }

    val totalCount: Int get() = badges.size

    val selected: Badge? get() = badges.firstOrNull { it.id == selectedId }

    /**
     * The grid, one shelf at a time. Grouped rather than declared twice, so a
     * badge cannot be in the catalog and off the screen: `groupBy` keeps
     * encounter order and [badges] is already in catalog order, which is the
     * order the sections were flattened in.
     */
    val sections: List<BadgeSection>
        get() = badges.groupBy { it.group }.map { (group, list) -> BadgeSection(group, list) }
}

/** One heading and the badges under it. */
data class BadgeSection(
    val group: AchievementGroup,
    val badges: List<Badge>,
)

sealed interface AchievementsEvent {
    data object NavigateBack : AchievementsEvent
}

sealed interface AchievementsAction {
    data object Load : AchievementsAction
    data class HistoryChanged(val state: AchievementState) : AchievementsAction
    data class LeaderboardsOfferable(val offerable: Boolean) : AchievementsAction
    data object Back : AchievementsAction
    data class Select(val id: AchievementId) : AchievementsAction
    data object CloseDetail : AchievementsAction
    data object OpenLeaderboards : AchievementsAction
}
