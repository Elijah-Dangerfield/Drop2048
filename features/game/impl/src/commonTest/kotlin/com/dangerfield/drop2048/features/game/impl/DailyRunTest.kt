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
import kotlin.test.assertNotNull
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
            assertEquals(dailySeedFor(Day), savedRun(GameMode.DAILY)?.seed)
            assertEquals(GameMode.DAILY, savedRun(GameMode.DAILY)?.mode)
            assertEquals("2026-09-09", savedRun(GameMode.DAILY)?.dailyDate)
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
     * Decision D19 at the moment the player sees it: a Daily that beat the
     * all-time best does not say so, because `bestScore()` filters Daily rows out
     * and the stats page would flatly contradict the celebration.
     *
     * The positive control is the second scenario: the identical score in Endless
     * does light it, so the assertion cannot pass because the sheet lost the
     * flag.
     */
    @Test
    fun daily_neverClaimsANewBest() = runUnitTest {
        playing(
            picture = NearlyStackedOut,
            fallingAt = Cell(2, 0),
            daily = FakeDailyRepository().grant(),
        ) {
            act(GameAction.StartDaily)
            land()
            waitOutResolution()

            assertPhase(GamePhase.StackedOut)
            assertTrue(state.score > 0, "the run really did beat the old best of zero")
            assertFalse(state.newBest, "and a Daily does not get to own it")
        }

        playing(picture = NearlyStackedOut, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()

            assertPhase(GamePhase.StackedOut)
            assertTrue(state.newBest, "the same score in Endless does")
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
            val seed = savedRun(GameMode.DAILY)?.seed

            act(GameAction.Restart)

            assertTrue(effects.contains(GameEffect.Leave))
            assertEquals(seed, savedRun(GameMode.DAILY)?.seed)
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
            savedRun(GameMode.DAILY)
        }

        playing(resume = saved, daily = ledger) {
            act(GameAction.StartDaily)

            assertEquals(1, ledger.started.size)
            assertEquals(GameMode.DAILY, state.mode)
            assertFalse(effects.contains(GameEffect.Leave))
        }
    }

    /**
     * The bug this chunk fixed, end to end, as the sequence that produced it:
     * start the Daily, quit it, play Endless, come back.
     *
     * With one save slot the Endless run overwrote the Daily blob, so the attempt
     * was gone **and the day was spent** — SPEC 14 gives one attempt a day and
     * nothing hands it back. C6 found it and could not fix it.
     *
     * `daily.started` staying at one is the assertion that matters: the run comes
     * back off its own slot rather than being charged for a second time, which
     * would be the other way of "fixing" this and would give the player a free
     * reroll of a board everybody else gets one shot at.
     */
    @Test
    fun daily_survivesAnEndlessRunStartedOverTheTopOfIt() = runUnitTest {
        val ledger = FakeDailyRepository().grant()
        val slots = playing(daily = ledger) {
            act(GameAction.StartDaily)
            act(GameAction.Pause)
            act(GameAction.ConfirmQuit)

            act(GameAction.Restart)
            land()

            assertEquals(GameMode.ENDLESS, savedRun()?.mode, "the Endless run wrote its own slot")
            assertEquals(GameMode.DAILY, savedRun(GameMode.DAILY)?.mode, "and left the Daily alone")
            savedRuns
        }

        assertEquals(dailySeedFor(Day), slots.stored(GameMode.DAILY)?.seed)
    }

    /**
     * And the attempt is playable again once it survives, without being charged
     * for twice.
     */
    @Test
    fun daily_resumesFromItsOwnSlotAfterAnEndlessRun() = runUnitTest {
        val ledger = FakeDailyRepository().grant()
        val saved = playing(daily = ledger) {
            act(GameAction.StartDaily)
            savedRun(GameMode.DAILY)
        }

        playing(resume = assertNotNull(saved), daily = ledger) {
            act(GameAction.StartDaily)

            assertEquals(1, ledger.started.size, "no second attempt was spent")
            assertEquals(GameMode.DAILY, state.mode)
            assertEquals(dailySeedFor(Day), savedRun(GameMode.DAILY)?.seed)
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
