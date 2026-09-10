package com.dangerfield.drop2048.features.daily.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.progress.daily.DailyRepository
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStatus
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import com.dangerfield.drop2048.libraries.progress.daily.DeviceTimeZone
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import me.tatarka.inject.annotations.Inject
import kotlin.time.Duration

/**
 * SPEC 14's screen, and no arithmetic of its own.
 *
 * Every number here — the day, the seed, the attempts left, the streak, the time
 * to the next board — is resolved by `DailyRepository` in one snapshot. A
 * ViewModel that recomputed any of them would be a second clock, and the whole
 * point of keying the mode on UTC is that there is exactly one.
 *
 * It collects rather than reads once, so a screen left open across 00:00 UTC
 * swaps to the new board on its own — and a screen returned to after a run shows
 * the score that run just banked.
 */
@Inject
class DailyViewModel(
    private val daily: DailyRepository,
    private val timeZone: DeviceTimeZone,
) : SEAViewModel<DailyState, DailyEvent, DailyAction>(initialStateArg = DailyState()) {

    private val logger = KLog.withTag("Daily")

    init {
        takeAction(DailyAction.Load)
    }

    override suspend fun handleAction(action: DailyAction) {
        when (action) {
            DailyAction.Load -> action.load()
            DailyAction.Play -> action.play()
            DailyAction.Retry -> action.retry()
            DailyAction.Back -> sendEvent(DailyEvent.Leave)
        }
    }

    private fun DailyAction.load() {
        viewModelScope.launch {
            daily.observe()
                .catch { Catching<Unit> { throw it }.logOnFailure { "Could not read the daily" } }
                .collect { status ->
                    updateState { it.copy(status = status, zone = timeZone.current(), loading = false) }
                }
        }
    }

    /**
     * The screen does not spend the attempt; the game screen does, on arrival.
     *
     * Two reasons and the second is the one that matters. The attempt has to be
     * spent by whoever can also *start* the run, or a player who is navigated
     * away mid-transition pays for a board they never saw. And `GameRoute` can be
     * reached without passing through here at all, so the gate has to live at the
     * board either way — putting a second one here would be two rules to keep in
     * agreement.
     *
     * The consequence is that **resuming is not the same question as playing**.
     * An attempt in flight has already been spent, so `playable` is false for it
     * and the gate has to let it through on [DailyStatus.inProgress] instead. The
     * game screen makes the same distinction: it looks for a saved Daily before
     * it asks for an attempt.
     */
    private suspend fun DailyAction.play() {
        val status = state.status
        if (!status.playable && !(status.enabled && status.inProgress)) return
        sendEvent(DailyEvent.OpenDaily)
    }

    private suspend fun DailyAction.retry() {
        updateState { it.copy(retrying = true) }
        val result = Catching { daily.grantRetry() }
            .logOnFailure { "Could not grant a Daily retry" }
            .getOrDefault(DailyRetryResult.Unavailable)
        logger.logEvent("daily.retry", "result" to result::class.simpleName.orEmpty())
        updateState { it.copy(retrying = false, lastRetry = result) }
        if (result is DailyRetryResult.Granted) sendEvent(DailyEvent.OpenDaily)
    }
}

data class DailyState(
    val status: DailyStatus = EmptyStatus,
    /** The player's own zone, for rendering the countdown. Never for the date. */
    val zone: TimeZone = TimeZone.UTC,
    val loading: Boolean = true,
    val retrying: Boolean = false,
    /** The last refusal or grant, so the screen can say which one it was. */
    val lastRetry: DailyRetryResult? = null,
)

sealed interface DailyEvent {
    data object Leave : DailyEvent

    /** Navigate to `GameRoute(mode = DAILY)`. */
    data object OpenDaily : DailyEvent
}

sealed interface DailyAction {
    data object Load : DailyAction
    data object Play : DailyAction
    data object Retry : DailyAction
    data object Back : DailyAction
}

/**
 * The state before the first emission: a day nobody can play.
 *
 * Deliberately not playable and deliberately not enabled, so a screen that
 * rendered it by mistake offers nothing rather than offering a board on a date
 * that is not today's.
 */
private val EmptyStatus = DailyStatus(
    date = LocalDate(1970, 1, 1),
    seed = 0,
    result = null,
    streak = DailyStreak.Empty,
    attemptsAllowed = 0,
    retryOffered = false,
    resetsIn = Duration.ZERO,
    enabled = false,
)
