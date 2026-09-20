package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.achievements.Achievement
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.Achievements
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The reason C9 exists.**
 *
 * Sodogku shipped `Leaderboards.submit` with zero production call sites and did
 * not notice for a long time: every reference to it outside its own module was a
 * test double, so no score was ever posted and its telemetry event could not
 * fire. An API with no caller looks exactly like coverage.
 *
 * So the thing under test here is not `RealLeaderboards` — that has its own
 * file, in its own module, and it was never the part that was broken. It is that
 * **a run ending in the real ViewModel touches the platform**, that it picks the
 * right board for the mode it was played in, and that the same moment files the
 * achievement fact. Delete `postToLeaderboards` and this file goes red;
 * `RealLeaderboardsTest` does not.
 */
class RunEndReportingTest : CoroutineTest() {

    @Test
    fun aFinishedEndlessRunPostsItsScoreToTheAllTimeAndWeeklyBoards() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            declineContinue()

            assertEquals(GamePhase.StackedOut, state.phase)
            assertEquals(
                listOf(
                    Leaderboard.AllTimeScore to state.score,
                    Leaderboard.WeeklyScore to state.score,
                ),
                leaderboards.submissions,
                "a finished run did not reach the platform",
            )
        }
    }

    @Test
    fun nothingIsPostedWhileARunIsStillGoing() = runUnitTest {
        playing(fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            declineContinue()

            assertTrue(
                leaderboards.submissions.isEmpty(),
                "a score was posted mid-run: ${leaderboards.submissions}",
            )
        }
    }

    /**
     * The other half of the same moment. The fact carries `run_record`'s numbers
     * *and* the transcript-derived ones, so a badge and the stats page can never
     * disagree about the run they are describing.
     */
    @Test
    fun aFinishedRunIsFiledAsAnAchievementFact() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            declineContinue()

            val record = recordedRuns().single()
            val fact = achievements.recorded.single()

            assertEquals(record.score, fact.score)
            assertEquals(record.endedAt, fact.endedAt)
            assertEquals(record.longestCascade, fact.longestCascade)
            assertEquals(record.merges, fact.merges)
        }
    }

    @Test
    fun whatTheRunUnlockedReachesTheScreen() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            achievements = FakeAchievementsRepository(unlocks = listOf(FirstMerge)),
        ) {
            land()
            waitOutResolution()
            declineContinue()

            assertEquals(listOf(AchievementId.FirstMerge), state.unlocked)

            act(GameAction.DismissUnlocks)

            assertTrue(state.unlocked.isEmpty(), "the toast could announce itself twice")
        }
    }

    private companion object {
        /**
         * A column one block short of the top, so the next lock stacks out on
         * the frame its resolution finishes on.
         */
        val StackedOutBoard = """
            .  .  4  .  .
            .  .  8  .  .
            .  .  16 .  .
            .  .  32 .  .
            .  .  64 .  .
            .  .  128 . .
            .  .  256 . .
        """

        val FirstMerge: Achievement = Achievements[AchievementId.FirstMerge]
    }
}
