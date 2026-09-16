package com.dangerfield.drop2048.libraries.leaderboards

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The app's whole view of the platform's game services: boards, and the badges
 * that show up on a player's profile outside this app.
 *
 * Note what the signatures refuse to offer. Nothing suspends and nothing returns
 * a result, so there is no way to write a call site that waits on a leaderboard
 * or branches on one. That is the fail-open rule made structural: Game Center is
 * absent when the player is signed out, restricted by Screen Time, offline, in a
 * region without it, or switched off by `feature.leaderboards`, and in every one
 * of those cases the only correct behaviour is that the game does not notice. An
 * API that handed back a `Boolean` would eventually get an `if` written around
 * it.
 *
 * The one thing callers may read is [isOfferable], and only to decide whether to
 * draw an entry point.
 *
 * Values are submitted, not accumulated: send the score and the platform keeps
 * the best it has seen. Sending the same number twice is harmless, which is what
 * makes "call this at the end of every run" safe.
 */
interface Leaderboards {

    /**
     * Whether showing the player a way into the leaderboards would lead
     * anywhere. False on Android, false while authentication is unresolved,
     * false when the platform has said no, and false when `feature.leaderboards`
     * is off.
     *
     * True includes [GameServicesStatus.SignInRequired], because opening the
     * dashboard in that state presents the sign-in screen we held back at
     * launch, and that is a useful thing for a tap to do.
     */
    val isOfferable: StateFlow<Boolean>

    /**
     * Records that the player's value for [board] is now [value]. Returns
     * immediately, does the work elsewhere, and cannot fail in any way the
     * caller can observe.
     *
     * Safe to call at the end of every run. A value that is not an improvement
     * never reaches the network — except on a [Leaderboard.recurring] board,
     * which is resent every time for the reason spelled out there.
     */
    fun submit(board: Leaderboard, value: Long)

    /**
     * Declares the player's complete set of earned badges, by
     * `AchievementId.name`. Not a delta: pass everything they have, every time,
     * and this works out what the platform has not been told yet.
     *
     * Whole-set rather than newly-unlocked because the interesting case is the
     * player who earned twenty badges signed out and then signed in, and because
     * it makes the caller a `map` over state it already holds rather than
     * something that has to be called at exactly the right moment or lose a
     * badge forever.
     */
    fun reportUnlocked(achievementNames: Set<String>)

    /**
     * Opens the platform's leaderboard UI, focused on [board] when one is given.
     * Only ever in response to a player action: this is the one call that can put
     * something on screen.
     */
    fun openDashboard(board: Leaderboard? = null)
}

/**
 * The binding when nothing better is in the graph, and the reason a call site
 * can be written before `:libraries:leaderboards:impl` is wired into the app.
 *
 * It lives in this api module rather than in an impl so `RealLeaderboards` can
 * replace it by name without anything crossing an impl module boundary.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoLeaderboards : Leaderboards {

    private val offerable = MutableStateFlow(false)

    override val isOfferable: StateFlow<Boolean> = offerable.asStateFlow()

    override fun submit(board: Leaderboard, value: Long) = Unit

    override fun reportUnlocked(achievementNames: Set<String>) = Unit

    override fun openDashboard(board: Leaderboard?) = Unit
}

/**
 * The Game Center id for a badge, derived from `AchievementId.name` rather than
 * typed out twenty-four times.
 *
 * Derived for the same reason `ScoreLadder` derives its score rungs: a table of
 * twenty-four hand-written constants is twenty-four chances to file a badge
 * under another badge's id, and the failure is invisible — the wrong badge
 * silently unlocks on a player's profile, and nothing in this app ever reads it
 * back to notice. `AchievementId` already documents its names as the persisted,
 * never-renamed identity of a badge, so there is a stable key here already and a
 * second one would only be a thing to keep in step.
 *
 * The owner types these into App Store Connect by hand. `PlatformAchievementTest`
 * prints the full expected list so `docs/OWNER-TODO.md` can carry it, and fails
 * if the format or the catalog moves under it.
 */
fun platformAchievementId(achievementName: String): String =
    "com.dangerfield.drop2048.achievement.$achievementName"
