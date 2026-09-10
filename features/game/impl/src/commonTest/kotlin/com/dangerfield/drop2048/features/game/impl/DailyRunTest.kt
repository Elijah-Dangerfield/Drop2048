package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.daily.dailySeedFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SPEC 14 on the board itself: what spends an attempt, what does not, and what
 * gets written when the run ends.
 *
 * How many attempts a day *has* is the repository's rule and is checked in
 * `DailyRepositoryImplTest`. What is checked here is that the screen asks
 * exactly once, in the right places.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyRunTest : CoroutineTest() {

    @Test
    fun daily_spendsOneAttemptAndPlaysTheDaysSeed() = runUnitTest {
        playing(daily = FakeDailyRepository().grant()) {
            act(GameAction.StartDaily)

            assertEquals(1, daily.started.size)
            assertEquals(GameMode.DAILY, state.mode)
            assertEquals(dailySeedFor(Day), savedRun()?.seed)
            assertEquals(GameMode.DAILY, savedRun()?.mode)
            assertEquals("2026-09-09", savedRun()?.dailyDate)
        }
    }

    /**
     * The day the attempt was granted, not the day it finished on. An attempt
     * that crosses 00:00 UTC belongs to the board it began on, and the ViewModel
     * is where that date has to survive.
     */
    @Test
    fun daily_banksTheScoreAgainstTheDayItWasGranted() = runUnitTest {
        playing(picture = NearlyStackedOut, fallingAt = Cell(2, 0), daily = FakeDailyRepository().grant()) {
            act(GameAction.StartDaily)
            land()
            waitOutResolution()

            assertPhase(GamePhase.StackedOut)
            assertEquals(listOf(Day to state.score), daily.banked)
        }
    }

    /**
     * A Daily run is still a run: `run_record` gets a row with `mode = DAILY`, or
     * SPEC 15's lifetime numbers would silently stop counting a whole mode.
     */
    @Test
    fun daily_isAlsoRecordedAsARun() = runUnitTest {
        playing(picture = NearlyStackedOut, fallingAt = Cell(2, 0), daily = FakeDailyRepository().grant()) {
            act(GameAction.StartDaily)
            land()
            waitOutResolution()

            val record = progress.recorded.single()
            assertEquals(GameMode.DAILY, record.mode)
            assertEquals(dailySeedFor(Day), record.seed)
        }
    }

    /**
     * Restart is a free reroll of a board everyone else gets one shot at, so it
     * leaves instead. The screen also hides the control; this is the half that
     * holds when the route is reached some other way.
     */
    @Test
    fun daily_refusesRestart() = runUnitTest {
        playing(daily = FakeDailyRepository().grant()) {
            act(GameAction.StartDaily)
            val seed = savedRun()?.seed

            act(GameAction.Restart)

            assertTrue(effects.contains(GameEffect.Leave))
            assertEquals(seed, savedRun()?.seed)
            assertEquals(1, daily.started.size)
        }
    }

    /**
     * A day with nothing left cannot be entered by navigating to the route. The
     * Daily screen is the gate, but a deep link, a restored back stack and a
     * stale screen all go around it.
     */
    @Test
    fun daily_leavesRatherThanStartingWhenTheAttemptIsRefused() = runUnitTest {
        playing(daily = FakeDailyRepository()) {
            act(GameAction.StartDaily)

            assertTrue(effects.contains(GameEffect.Leave))
            assertEquals(GameMode.ENDLESS, state.mode)
        }
    }

    /**
     * Backgrounding the app during the Daily must not cost the player their day.
     * The run comes back off disk and the route argument fires again on the way
     * in, so the second `StartDaily` has to recognise the run it is already in.
     */
    @Test
    fun daily_resumesWithoutSpendingASecondAttempt() = runUnitTest {
        val ledger = FakeDailyRepository().grant()
        val saved = playing(daily = ledger) {
            act(GameAction.StartDaily)
            savedRun()
        }

        playing(resume = saved, daily = ledger) {
            act(GameAction.StartDaily)

            assertEquals(1, ledger.started.size)
            assertEquals(GameMode.DAILY, state.mode)
            assertFalse(effects.contains(GameEffect.Leave))
        }
    }

    private companion object {
        val Day = LocalDate(2026, 9, 9)

        /** One drop from the top row, so the run ends without a long script. */
        val NearlyStackedOut = """
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
