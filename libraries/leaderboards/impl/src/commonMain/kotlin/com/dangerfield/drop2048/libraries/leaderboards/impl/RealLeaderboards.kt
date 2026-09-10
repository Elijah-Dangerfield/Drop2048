package com.dangerfield.drop2048.libraries.leaderboards.impl

import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.leaderboards.GameServices
import com.dangerfield.drop2048.libraries.leaderboards.GameServicesStatus
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import com.dangerfield.drop2048.libraries.leaderboards.NoLeaderboards
import com.dangerfield.drop2048.libraries.leaderboards.SubmitResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The real [Leaderboards]: when a number is worth sending, and what happens to
 * one that could not be sent yet.
 *
 * ## The case this exists for
 *
 * Authentication is asynchronous and slow, and a run is not. A player who
 * launches the app, plays badly and stacks out in forty seconds has produced a
 * score before Game Center has decided who they are. Submitting it then does
 * nothing, and without somewhere to put it the score is lost until the *next*
 * run, which on a first session is often never. So an unsendable value is held,
 * one slot per board, newest wins, and flushed the moment authentication lands.
 * That single behaviour is most of what this class is.
 *
 * The held values are in memory only. A process death loses them, which is
 * correct rather than a shortcut: [Leaderboard.AllTimeScore] and
 * [Leaderboard.WeeklyScore] are recomputed and resent at the end of the next
 * run, so persisting them would be caching something already on disk in a more
 * fragile form. A Daily score is the one that a process death can strand for the
 * day, and that is the accepted cost of not adding an outbox for a value the
 * player can resend by playing.
 *
 * ## The other half: not sending
 *
 * [submitted] remembers the best value the platform has accepted this process,
 * and anything no better is dropped before it reaches the network. An all-time
 * best is submitted after every run and only changes on a personal best, so
 * without this the app would spend a network call per run to tell Game Center a
 * number it already has.
 *
 * ## Failure
 *
 * Every exit is silent. A refusal, an error, an exception out of the platform
 * seam, a submission for a board id that does not exist in App Store Connect —
 * which is every board today, see [Leaderboard] — all of them leave the game
 * exactly as it was. The only trace is a log line and, on success, an app event.
 *
 * Authentication is started from `init` because Apple asks for it as early as
 * possible, and this is a singleton nothing touches until a run ends — without
 * the `AutoInit` binding it would first run at the exact moment a score needed
 * sending.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = Leaderboards::class,
    replaces = [NoLeaderboards::class],
)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RealLeaderboards(
    private val services: GameServices,
    private val appScope: AppCoroutineScope,
) : Leaderboards, AutoInit {

    private val logger = KLog.withTag("Leaderboards")

    /** Guards [pending] and [submitted], which are read-modify-write from several coroutines. */
    private val lock = Mutex()

    /** The best value per board that has not been accepted yet. At most one entry per board. */
    private val pending = mutableMapOf<Leaderboard, Long>()

    /** The best value per board the platform has accepted this process. */
    private val submitted = mutableMapOf<Leaderboard, Long>()

    override val isOfferable: StateFlow<Boolean> = services.status
        .map { it == GameServicesStatus.Authenticated || it == GameServicesStatus.SignInRequired }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        services.startAuthentication()

        appScope.launch {
            services.status.collect { status ->
                if (status == GameServicesStatus.Authenticated) flush()
            }
        }
    }

    override fun submit(board: Leaderboard, value: Long) {
        appScope.launch { record(board, value) }
    }

    override fun openDashboard(board: Leaderboard?) {
        appScope.launch {
            Catching { services.presentDashboard(board?.id) }
                .logOnFailure { "Could not present the leaderboard dashboard" }
        }
    }

    /**
     * There is no separate guard for zero, which is what a run that stacked out
     * on the opening drop reports. Nothing has been accepted yet, so [submitted]
     * reads as 0 and the improvement test below already drops it. A second guard
     * would be a second thing to keep in step.
     */
    private suspend fun record(board: Leaderboard, value: Long) {
        val worthSending = lock.withLock {
            if (value <= submitted.bestFor(board)) {
                false
            } else {
                pending[board] = maxOf(pending.bestFor(board), value)
                true
            }
        }
        if (worthSending) flush()
    }

    /**
     * The whole flush holds the lock, including the network call inside it.
     * Nothing on a screen is waiting on this, and the alternative is two
     * concurrent flushes sending the same value twice.
     */
    private suspend fun flush() {
        if (services.status.value != GameServicesStatus.Authenticated) return

        lock.withLock {
            pending.toMap().forEach { (board, value) -> send(board, value) }
        }
    }

    /**
     * Both writes at the end are plain rather than a max, because [flush] holds
     * the lock across the whole network call: nothing can have raised either map
     * since this value was read out of it.
     */
    private suspend fun send(board: Leaderboard, value: Long) {
        val result = Catching { services.submit(board.id, value) }
            .logOnFailure { "Leaderboard submit threw for ${board.id}" }
            .getOrDefault(SubmitResult.Failed)

        if (result != SubmitResult.Submitted) {
            logger.d { "Leaderboard ${board.name} not submitted ($result); holding $value" }
            return
        }

        submitted[board] = value
        pending.remove(board)
        logger.logEvent(
            "leaderboard.submitted",
            "board" to board.name,
            "value" to value,
        )
    }

    private fun Map<Leaderboard, Long>.bestFor(board: Leaderboard): Long = this[board] ?: 0L
}
