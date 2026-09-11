package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.debug.DebugOverrides
import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.blockOf
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The guard SPEC 19 is really about: a QA run must not become a player's data.
 *
 * Four things claim a player did something — `run_record`, `daily_result`,
 * `Leaderboards.submit` and the achievement fact log — and a debug session has
 * to silence all four. This is the file that goes red if one of them is added
 * back, and it is paired with the opposite assertion in `RunEndReportingTest`,
 * which proves the same four fire on a normal run. Neither is worth much alone:
 * a guard that suppresses everything is indistinguishable from a broken run end
 * (L35).
 */
class DebugSessionTest : CoroutineTest() {

    @Test
    fun aRunInADebugSessionWritesNoRunRecord() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            debug = FakeDebugController(),
        ) {
            land()
            waitOutResolution()
            declineContinue()

            assertEquals(GamePhase.StackedOut, state.phase)
            assertTrue(progress.recorded.isEmpty(), "a debug run reached run_record")
        }
    }

    @Test
    fun aRunInADebugSessionPostsNoScore() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            debug = FakeDebugController(),
        ) {
            land()
            waitOutResolution()
            declineContinue()

            assertTrue(
                leaderboards.submissions.isEmpty(),
                "a debug run posted ${leaderboards.submissions}",
            )
        }
    }

    /**
     * A Daily played in a debug session must not close the day, because the row
     * it would write is the only record of what the player scored on a board
     * everybody else also played.
     */
    @Test
    fun aDailyInADebugSessionBanksNothing() = runUnitTest {
        val daily = FakeDailyRepository().grant()
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            daily = daily,
            debug = FakeDebugController(),
        ) {
            act(GameAction.StartDaily)
            land()
            waitOutResolution()
            declineContinue()

            assertTrue(daily.banked.isEmpty(), "a debug Daily banked ${daily.banked}")
        }
    }

    @Test
    fun aRunInADebugSessionEarnsNoBadges() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            debug = FakeDebugController(),
        ) {
            land()
            waitOutResolution()
            declineContinue()

            assertTrue(achievements.recorded.isEmpty(), "a debug run filed an achievement fact")
            assertTrue(state.unlocked.isEmpty(), "a debug run announced a badge")
        }
    }

    /**
     * The run still happens. The stacked-out sheet is drawn, the score is on it,
     * and the share still works — what is suppressed is the claim that it counted.
     * A guard that also broke the screen would be found immediately; one that
     * only breaks the data would not.
     */
    @Test
    fun aDebugRunIsStillPlayedAndStillShown() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            debug = FakeDebugController(),
        ) {
            land()
            waitOutResolution()
            declineContinue()

            assertEquals(GamePhase.StackedOut, state.phase)
            assertTrue(state.score > 0, "a debug run scored nothing")
        }
    }

    /**
     * SPEC 19's forced queue, and the reason it is applied after the engine
     * rather than inside it: the engine still drew whatever the seed said, and
     * what the player is handed is overwritten afterwards.
     */
    @Test
    fun aForcedBlockIsHandedOutInsteadOfTheDrawnOne() = runUnitTest {
        val forced = blockOf(BlockValue.V1024)
        playing(
            picture = "",
            fallingAt = Cell(2, 0),
            debug = FakeDebugController(DebugOverrides(forcedBlocks = listOf(forced))),
        ) {
            land()
            waitOutResolution()
            declineContinue()

            assertEquals(forced, state.falling?.block, "the queue did not reach the board")
        }
    }

    private companion object {
        /**
         * A column one block short of the top, so the next lock stacks out on
         * the frame its resolution finishes on. The same picture
         * `RunEndReportingTest` asserts the opposite against.
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
    }
}
