package com.dangerfield.drop2048.libraries.achievements.impl

import com.dangerfield.drop2048.libraries.achievements.Achievement
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.AchievementState
import com.dangerfield.drop2048.libraries.achievements.AchievementsRepository
import com.dangerfield.drop2048.libraries.achievements.RunOutcome
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [AchievementPlatformSync] — that earning a badge in this app puts one on the
 * player's Game Center profile.
 *
 * Twenty-four badges existed for a release with no platform reporting at all:
 * every one of them local, invisible outside the app, and nothing anywhere
 * failing. That is the same shape of hole SPEC 15 warns about for
 * `Leaderboards.submit`, so it gets the same kind of test — one that asserts the
 * platform was touched, in the module that does the touching.
 *
 * What is *not* asserted here is which of a declared set actually reach the
 * network. This publishes the whole earned set on every change by design, and
 * `RealLeaderboardsTest` owns the deduplication below it.
 */
class AchievementPlatformSyncTest : CoroutineTest() {

    private val leaderboards = RecordingLeaderboards()

    private fun sync(achievements: AchievementsRepository) =
        AchievementPlatformSync(achievements, leaderboards, AppCoroutineScope(dispatchers))

    @Test
    fun theEarnedSetIsDeclaredAtBoot() = runUnitTest {
        sync(FakeRepository(unlocked(AchievementId.FirstMerge, AchievementId.SixtyFour)))

        assertEquals(
            setOf(AchievementId.FirstMerge.name, AchievementId.SixtyFour.name),
            leaderboards.reported.last(),
        )
    }

    /**
     * The badge earned while the screen is open, or at the end of a run. This is
     * the case a call site in `endRun` would also cover — the three below are
     * the ones it would not.
     */
    @Test
    fun aNewlyEarnedBadgeIsDeclared() = runUnitTest {
        val achievements = FakeRepository(AchievementState.Empty)
        sync(achievements)

        achievements.emit(unlocked(AchievementId.FirstMerge))

        assertEquals(setOf(AchievementId.FirstMerge.name), leaderboards.reported.last())
    }

    /** Not a delta: the platform is always told everything, not just what changed. */
    @Test
    fun theWholeSetIsDeclaredEachTimeAndNotJustTheNewOne() = runUnitTest {
        val achievements = FakeRepository(unlocked(AchievementId.FirstMerge))
        sync(achievements)

        achievements.emit(unlocked(AchievementId.FirstMerge, AchievementId.SixtyFour))

        assertEquals(
            setOf(AchievementId.FirstMerge.name, AchievementId.SixtyFour.name),
            leaderboards.reported.last(),
        )
    }

    /**
     * A re-fold that changes a counter but unlocks nothing is the common case —
     * it happens at the end of every run — and redeclaring an unchanged set on
     * each one would be a wasted pass through the layer below for the life of
     * the app.
     */
    @Test
    fun anUnchangedSetIsNotDeclaredTwice() = runUnitTest {
        val achievements = FakeRepository(unlocked(AchievementId.FirstMerge))
        sync(achievements)
        val afterBoot = leaderboards.reported.size

        achievements.emit(unlocked(AchievementId.FirstMerge))

        assertEquals(afterBoot, leaderboards.reported.size)
    }

    @Test
    fun aPlayerWithNoBadgesDeclaresAnEmptySetRatherThanNothing() = runUnitTest {
        sync(FakeRepository(AchievementState.Empty))

        assertEquals(listOf(emptySet()), leaderboards.reported)
    }

    /**
     * Reporting a badge is worth nothing next to being able to play, so a
     * repository that cannot be read must not take the app's boot sequence with
     * it — this runs from an `AutoInit` on the app scope, where an escaping
     * exception is not survivable.
     */
    @Test
    fun aRepositoryThatThrowsIsSurvived() = runUnitTest {
        sync(ThrowingRepository())

        assertTrue(leaderboards.reported.isEmpty())
    }
}

private fun unlocked(vararg ids: AchievementId): AchievementState =
    AchievementState(unlocked = ids.associateWith { 0L })

private class RecordingLeaderboards : Leaderboards {

    val reported = mutableListOf<Set<String>>()

    override val isOfferable = MutableStateFlow(false)

    override fun submit(board: Leaderboard, value: Long) = Unit

    override fun reportUnlocked(achievementNames: Set<String>) {
        reported += achievementNames
    }

    override fun openDashboard(board: Leaderboard?) = Unit
}

private class FakeRepository(initial: AchievementState) : AchievementsRepository {

    private val states = MutableStateFlow(initial)

    fun emit(next: AchievementState) {
        states.value = next
    }

    override fun observe(): Flow<AchievementState> = states

    override suspend fun state(): AchievementState = states.first()

    override suspend fun record(outcome: RunOutcome): List<Achievement> = emptyList()

    override suspend fun reset() = Unit
}

private class ThrowingRepository : AchievementsRepository {

    override fun observe(): Flow<AchievementState> = error("the database is gone")

    override suspend fun state(): AchievementState = error("the database is gone")

    override suspend fun record(outcome: RunOutcome): List<Achievement> = emptyList()

    override suspend fun reset() = Unit
}
