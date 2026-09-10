package com.dangerfield.drop2048.features.daily.impl

import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.progress.daily.DailyAttempt
import com.dangerfield.drop2048.libraries.progress.daily.DailyRepository
import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStatus
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import com.dangerfield.drop2048.libraries.progress.daily.DeviceTimeZone
import com.dangerfield.drop2048.libraries.progress.daily.dailySeedFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * The screen's decisions, which are only three: what to draw, whether to offer a
 * run, and what to do when an ad is refused.
 *
 * Nothing here re-derives a date, a seed or a streak. Those are the
 * repository's and `DailyRepositoryImplTest` is where they are checked; a second
 * set of assertions over a fake that reimplemented them would pass while the two
 * drifted apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyViewModelTest : CoroutineTest() {

    @Test
    fun `it renders the day the repository resolved`() = runUnitTest {
        val (viewModel, _) = viewModel()

        assertEquals(Day, viewModel.state.status.date)
        assertEquals(dailySeedFor(Day), viewModel.state.status.seed)
        assertFalse(viewModel.state.loading)
    }

    @Test
    fun `a playable day opens the board`() = runUnitTest {
        val (viewModel, events) = viewModel()

        viewModel.takeAction(DailyAction.Play)

        assertTrue(events.contains(DailyEvent.OpenDaily))
    }

    /**
     * The screen must not navigate to a run it knows cannot start. The board
     * refuses too, but arriving there and bouncing straight out reads as a crash.
     */
    @Test
    fun `a spent day does not open the board`() = runUnitTest {
        val (viewModel, events) = viewModel(status = spentDay())

        viewModel.takeAction(DailyAction.Play)

        assertFalse(events.contains(DailyEvent.OpenDaily))
    }

    /**
     * The attempt is spent by the board, not here. If it were spent here, a
     * player navigated away mid-transition would pay for a run they never saw.
     */
    @Test
    fun `opening the board does not spend the attempt`() = runUnitTest {
        val repository = FakeDailyRepository()
        val (viewModel, _) = viewModel(repository = repository)

        viewModel.takeAction(DailyAction.Play)

        assertEquals(0, repository.attemptsStarted)
    }

    @Test
    fun `a granted retry goes straight into the run`() = runUnitTest {
        val repository = FakeDailyRepository(
            status = spentDay(retryOffered = true),
            retry = DailyRetryResult.Granted(attemptsAllowed = 2),
        )
        val (viewModel, events) = viewModel(repository = repository)

        viewModel.takeAction(DailyAction.Retry)

        assertTrue(events.contains(DailyEvent.OpenDaily))
    }

    /**
     * No ad system yet (C10). The screen has to survive that as a state rather
     * than as an error, because it is also what a network with no fill looks
     * like.
     */
    @Test
    fun `an unavailable retry leaves the player where they were`() = runUnitTest {
        val (viewModel, events) = viewModel(
            repository = FakeDailyRepository(
                status = spentDay(retryOffered = true),
                retry = DailyRetryResult.Unavailable,
            )
        )

        viewModel.takeAction(DailyAction.Retry)

        assertFalse(events.contains(DailyEvent.OpenDaily))
        assertFalse(viewModel.state.retrying)
        assertEquals(DailyRetryResult.Unavailable, viewModel.state.lastRetry)
    }

    /** The kill switch. Off means no entry point, not a broken screen. */
    @Test
    fun `a disabled daily offers nothing`() = runUnitTest {
        val (viewModel, events) = viewModel(status = today().copy(enabled = false))

        viewModel.takeAction(DailyAction.Play)

        assertFalse(viewModel.state.status.playable)
        assertFalse(events.contains(DailyEvent.OpenDaily))
    }

    /**
     * The events channel is single-consumer, so the collector is started before
     * any action is taken. A test that collected afterwards would find the
     * channel already drained and every assertion below would pass vacuously.
     */
    private fun TestScope.viewModel(
        status: DailyStatus = today(),
        repository: FakeDailyRepository = FakeDailyRepository(status),
    ): Pair<DailyViewModel, List<DailyEvent>> {
        val viewModel = DailyViewModel(daily = repository, timeZone = DeviceTimeZone { TimeZone.UTC })
        val events = mutableListOf<DailyEvent>()
        backgroundScope.launch { viewModel.eventFlow.collect { events += it } }
        return viewModel to events
    }

    private companion object {
        val Day = LocalDate(2026, 9, 9)

        fun today() = DailyStatus(
            date = Day,
            seed = dailySeedFor(Day),
            result = null,
            streak = DailyStreak(current = 2, best = 9),
            attemptsAllowed = 1,
            retryOffered = false,
            resetsIn = 4.hours,
            enabled = true,
        )

        fun spentDay(retryOffered: Boolean = false) = today().copy(
            result = DailyResult(
                date = Day,
                seed = dailySeedFor(Day),
                score = 4_000,
                attemptsUsed = 1,
                completed = true,
            ),
            retryOffered = retryOffered,
        )
    }
}

private class FakeDailyRepository(
    status: DailyStatus? = null,
    private val retry: DailyRetryResult = DailyRetryResult.Unavailable,
) : DailyRepository {

    private val statuses = MutableStateFlow(
        status ?: DailyStatus(
            date = LocalDate(2026, 9, 9),
            seed = 0,
            result = null,
            streak = DailyStreak.Empty,
            attemptsAllowed = 1,
            retryOffered = false,
            resetsIn = Duration.ZERO,
            enabled = true,
        )
    )

    var attemptsStarted = 0
        private set

    override fun observe(): Flow<DailyStatus> = statuses

    override suspend fun status(): DailyStatus = statuses.value

    override suspend fun startAttempt(): DailyAttempt {
        attemptsStarted++
        return DailyAttempt.NoAttemptsLeft
    }

    override suspend fun recordAttempt(date: LocalDate, score: Long) = Unit

    override suspend fun grantRetry(): DailyRetryResult = retry

    override suspend fun history(): List<DailyResult> = emptyList()

    override suspend fun reset() = Unit
}
