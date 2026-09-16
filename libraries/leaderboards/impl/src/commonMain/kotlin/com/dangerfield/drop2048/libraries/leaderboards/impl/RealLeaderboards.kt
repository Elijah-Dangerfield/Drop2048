package com.dangerfield.drop2048.libraries.leaderboards.impl

import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.gameconfig.LeaderboardsEnabled
import com.dangerfield.drop2048.libraries.leaderboards.GameServices
import com.dangerfield.drop2048.libraries.leaderboards.GameServicesStatus
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import com.dangerfield.drop2048.libraries.leaderboards.NoLeaderboards
import com.dangerfield.drop2048.libraries.leaderboards.SubmitResult
import com.dangerfield.drop2048.libraries.leaderboards.platformAchievementId
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
 * The real [Leaderboards]: when something is worth sending, and what happens to
 * something that could not be sent yet.
 *
 * ## The case this exists for
 *
 * Authentication is asynchronous and slow, and a run is not. A player who
 * launches the app, plays badly and stacks out in forty seconds has produced a
 * score before Game Center has decided who they are. Submitting it then does
 * nothing, and without somewhere to put it the score is lost until the *next*
 * run, which on a first session is often never. So an unsendable value is held,
 * one slot per board, newest wins, and flushed the moment authentication lands.
 * That single behaviour is most of what this class is. Badges go through the
 * same hold: a player who earns five of them before signing in gets all five.
 *
 * The held values are in memory only. A process death loses them, which is
 * correct rather than a shortcut: both boards are recomputed and resent at the
 * end of the next run, and the badge set is re-derived from the fact log on
 * every launch, so persisting either would be caching something already on disk
 * in a more fragile form.
 *
 * ## The other half: not sending
 *
 * [submitted] remembers the best value the platform has accepted this process,
 * and anything no better is dropped before it reaches the network. An all-time
 * best is submitted after every run and only changes on a personal best, so
 * without this the app would spend a network call per run to tell Game Center a
 * number it already has.
 *
 * **[Leaderboard.recurring] boards are exempt**, and that exemption is a bug fix
 * rather than an optimisation the recurring case happens to miss. The platform
 * resets a recurring window on its own clock; this process cannot see the reset,
 * so [submitted] goes on describing a board that no longer exists. An app alive
 * across the weekly boundary would drop its first score of the new week for not
 * beating last week's, and the player would be absent from the new board
 * entirely. [Leaderboard] carries the reasoning for fixing it there rather than
 * by modelling the window here.
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
    private val featureEnabled: LeaderboardsEnabled,
) : Leaderboards, AutoInit {

    private val logger = KLog.withTag("Leaderboards")

    /** Guards every map and set below, all of which are read-modify-write from several coroutines. */
    private val lock = Mutex()

    /** The best value per board that has not been accepted yet. At most one entry per board. */
    private val pending = mutableMapOf<Leaderboard, Long>()

    /** The best value per board the platform has accepted this process. */
    private val submitted = mutableMapOf<Leaderboard, Long>()

    /** Badge names the platform has not been told about yet. */
    private val pendingAchievements = mutableSetOf<String>()

    /** Badge names the platform has accepted this process. */
    private val reportedAchievements = mutableSetOf<String>()

    /**
     * The kill switch is read on every emission rather than once, so flipping
     * `feature.leaderboards` off takes the entry point away at the next status
     * change instead of at the next launch. It is not observable on its own —
     * `ConfiguredValue` is a read, not a flow — which is the honest limit of a
     * kill switch built on a config map, and the reason [submit] checks it too.
     */
    override val isOfferable: StateFlow<Boolean> = services.status
        .map { status ->
            featureEnabled() &&
                (status == GameServicesStatus.Authenticated || status == GameServicesStatus.SignInRequired)
        }
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
        if (!featureEnabled()) return
        appScope.launch { record(board, value) }
    }

    override fun reportUnlocked(achievementNames: Set<String>) {
        if (!featureEnabled()) return
        appScope.launch { recordUnlocked(achievementNames) }
    }

    override fun openDashboard(board: Leaderboard?) {
        if (!featureEnabled()) return
        appScope.launch {
            Catching { services.presentDashboard(board?.id) }
                .logOnFailure { "Could not present the leaderboard dashboard" }
        }
    }

    /**
     * There is no separate guard for zero, which is what a run that stacked out
     * on the opening drop reports. Nothing has been accepted yet, so [submitted]
     * reads as 0 and the improvement test below already drops it — including on
     * a recurring board, where the floor is the only part of that test left.
     */
    private suspend fun record(board: Leaderboard, value: Long) {
        val worthSending = lock.withLock {
            val floor = if (board.recurring) 0L else submitted.bestFor(board)
            if (value <= floor) {
                false
            } else {
                pending[board] = maxOf(pending.bestFor(board), value)
                true
            }
        }
        if (worthSending) flush()
    }

    private suspend fun recordUnlocked(names: Set<String>) {
        val worthSending = lock.withLock {
            val unreported = names - reportedAchievements
            pendingAchievements += unreported
            pendingAchievements.isNotEmpty()
        }
        if (worthSending) flush()
    }

    /**
     * The whole flush holds the lock, including the network calls inside it.
     * Nothing on a screen is waiting on this, and the alternative is two
     * concurrent flushes sending the same value twice.
     */
    private suspend fun flush() {
        if (services.status.value != GameServicesStatus.Authenticated) return

        lock.withLock {
            pending.toMap().forEach { (board, value) -> send(board, value) }
            pendingAchievements.toSet().forEach { name -> report(name) }
        }
    }

    /**
     * Both writes at the end are plain rather than a max, because [flush] holds
     * the lock across the whole network call: nothing can have raised either map
     * since this value was read out of it.
     *
     * A recurring board is cleared from [pending] like any other but its accepted
     * value is deliberately *not* written to [submitted] — leaving it out is what
     * keeps [record]'s exemption true for the life of the process rather than
     * only until the first acceptance.
     */
    private suspend fun send(board: Leaderboard, value: Long) {
        val result = Catching { services.submit(board.id, value) }
            .logOnFailure { "Leaderboard submit threw for ${board.id}" }
            .getOrDefault(SubmitResult.Failed)

        if (result != SubmitResult.Submitted) {
            logger.d { "Leaderboard ${board.name} not submitted ($result); holding $value" }
            return
        }

        if (!board.recurring) submitted[board] = value
        pending.remove(board)
        logger.logEvent(
            "leaderboard.submitted",
            "board" to board.name,
            "value" to value,
        )
    }

    private suspend fun report(name: String) {
        val id = platformAchievementId(name)
        val result = Catching { services.reportAchievement(id) }
            .logOnFailure { "Achievement report threw for $id" }
            .getOrDefault(SubmitResult.Failed)

        if (result != SubmitResult.Submitted) {
            logger.d { "Achievement $name not reported ($result); holding it" }
            return
        }

        reportedAchievements += name
        pendingAchievements -= name
        logger.logEvent("leaderboard.achievement_reported", "achievement" to name)
    }

    private fun Map<Leaderboard, Long>.bestFor(board: Leaderboard): Long = this[board] ?: 0L
}
