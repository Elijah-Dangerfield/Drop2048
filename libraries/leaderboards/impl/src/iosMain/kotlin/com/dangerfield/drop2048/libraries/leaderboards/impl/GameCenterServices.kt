package com.dangerfield.drop2048.libraries.leaderboards.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.leaderboards.GameServices
import com.dangerfield.drop2048.libraries.leaderboards.GameServicesStatus
import com.dangerfield.drop2048.libraries.leaderboards.SubmitResult
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSError
import platform.Foundation.NSOperationQueue
import platform.GameKit.GKAchievement
import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.GameKit.GKGameCenterViewControllerStateLeaderboards
import platform.GameKit.GKLeaderboard
import platform.GameKit.GKLeaderboardPlayerScopeGlobal
import platform.GameKit.GKLeaderboardTimeScopeAllTime
import platform.GameKit.GKLocalPlayer
import platform.GameKit.authenticateHandler
import platform.GameKit.create
import platform.GameKit.gameCenterDelegate
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume

/**
 * Game Center, reached through the GameKit platform bindings rather than a
 * Swift shim. GameKit is an ordinary Objective-C system framework, so there is
 * nothing here Kotlin/Native cannot call, and a Swift file would only be a
 * second place for the seam to drift from.
 *
 * ## The authentication handler is the whole problem
 *
 * `GKLocalPlayer.authenticateHandler` is not a callback, it is a subscription,
 * and three things about it are easy to get wrong.
 *
 * **It is set exactly once, ever.** Assigning it again restarts authentication;
 * assigning it per call site produces an app that re-authenticates whenever
 * anybody asks a question. [installed] is what makes [startAuthentication]
 * idempotent, and it is only ever touched on the main queue, which is also the
 * only queue GameKit calls the handler on.
 *
 * **It fires more than once.** The first call usually hands over a sign-in
 * screen; a later one says the player signed in; another says they signed out
 * again mid-session. So this holds no continuation to resume (resuming one
 * twice is a crash) and instead writes each answer into [state], which callers
 * observe. That is why [GameServices.startAuthentication] returns nothing.
 *
 * **The view controller it hands you is not a notification, it is a request.**
 * We keep it and present it only if the player asks to see a leaderboard. A
 * sign-in sheet that appears over the game at launch, for a feature the game
 * does not need, is the exact failure the fail-open rule is about.
 *
 * ## Everything else is a refusal, not an error
 *
 * Signed out, restricted by Screen Time, underage, offline, unavailable in the
 * region: all of them arrive here as an error or a false `isAuthenticated`, all
 * of them end as [GameServicesStatus.Unavailable] or [SubmitResult.Failed], and
 * none of them reach the player.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class GameCenterServices : GameServices {

    private val logger = KLog.withTag("GameCenter")

    private val state = MutableStateFlow(GameServicesStatus.Unknown)

    override val status: StateFlow<GameServicesStatus> = state.asStateFlow()

    /** Main queue only, like everything GameKit hands back. */
    private var installed = false

    /**
     * Main queue only. The sign-in screen GameKit gave us, held until asked for,
     * and dropped **only** by [onAuthenticationChanged] — never by presenting it.
     * [present] says why.
     */
    private var signInViewController: UIViewController? = null

    /**
     * A field rather than a local, because the delegate is held weakly by the
     * view controller. A local would be collected between presenting the screen
     * and the player closing it, and the screen would then have no way to
     * dismiss itself.
     */
    private val dismissOnFinish = DismissingDelegate()

    override fun startAuthentication() {
        NSOperationQueue.mainQueue.addOperationWithBlock {
            if (installed) return@addOperationWithBlock
            installed = true
            GKLocalPlayer.local.authenticateHandler = { viewController, error ->
                onAuthenticationChanged(viewController, error)
            }
        }
    }

    override suspend fun submit(leaderboardId: String, value: Long): SubmitResult {
        val player = GKLocalPlayer.local
        if (!player.authenticated) return SubmitResult.NotAuthenticated

        return Catching {
            suspendCancellableCoroutine { continuation ->
                GKLeaderboard.submitScore(
                    score = value,
                    context = 0uL,
                    player = player,
                    leaderboardIDs = listOf(leaderboardId),
                ) { error ->
                    if (!continuation.isActive) return@submitScore
                    if (error == null) {
                        continuation.resume(SubmitResult.Submitted)
                    } else {
                        logger.w { "Game Center rejected $leaderboardId: ${error.localizedDescription}" }
                        continuation.resume(SubmitResult.Failed)
                    }
                }
            }
        }.logOnFailure { "Game Center submission threw for $leaderboardId" }
            .getOrDefault(SubmitResult.Failed)
    }

    /**
     * `reportAchievements` takes an array because Game Center would rather have
     * one call for a batch; it gets one at a time here anyway, because the layer
     * above holds an unreported badge until the platform accepts it and a batch
     * that half-succeeded would report a single verdict for several badges. A
     * badge is reported once in a player's lifetime, so there is no volume to
     * optimise for.
     *
     * `percentComplete = 100.0`, and `showsCompletionBanner` **explicitly**
     * `false`: see [GameServices.reportAchievement] and [completedAchievement].
     */
    override suspend fun reportAchievement(achievementId: String): SubmitResult {
        if (!GKLocalPlayer.local.authenticated) return SubmitResult.NotAuthenticated

        return Catching {
            val achievement = completedAchievement(achievementId)
            suspendCancellableCoroutine { continuation ->
                GKAchievement.reportAchievements(listOf(achievement)) { error ->
                    if (!continuation.isActive) return@reportAchievements
                    if (error == null) {
                        continuation.resume(SubmitResult.Submitted)
                    } else {
                        logger.w { "Game Center rejected $achievementId: ${error.localizedDescription}" }
                        continuation.resume(SubmitResult.Failed)
                    }
                }
            }
        }.logOnFailure { "Game Center achievement report threw for $achievementId" }
            .getOrDefault(SubmitResult.Failed)
    }

    override suspend fun presentDashboard(leaderboardId: String?) {
        withContext(Dispatchers.Main) {
            Catching { present(leaderboardId) }
                .logOnFailure { "Could not present Game Center" }
        }
    }

    /**
     * The sign-in screen wins when we are holding one: there is nothing to show
     * a signed-out player on a leaderboard, and this is the moment they asked
     * for it.
     *
     * **The held screen is not cleared here**, and that is the fix for a dead
     * entry point. Clearing it on presentation assumed the player would either
     * sign in or be told they could not, and that GameKit would call the
     * authentication handler again to say which. A player who swipes the sheet
     * away without finishing may produce neither: the status stays
     * `SignInRequired`, so the row is still drawn, and with the screen already
     * thrown away every later tap did *nothing at all* — no sheet, no dashboard,
     * no error. Keeping it means the second tap offers the sheet again, which is
     * the only useful answer available. [onAuthenticationChanged] is the one
     * place it is dropped, once the platform has actually changed its mind.
     *
     * `presentingViewController` is the guard against handing UIKit a screen it
     * is already showing, which is a thrown exception rather than a no-op.
     *
     * `GKGameCenterViewController.create` is one of the Objective-C factory
     * bridges Kotlin/Native still marks as beta, hence the opt-in. The
     * alternative is the deprecated `initWithState` / `initWithLeaderboardID`
     * pair, which is worse.
     */
    @OptIn(BetaInteropApi::class)
    private fun present(leaderboardId: String?) {
        val host = topViewController() ?: return

        signInViewController?.let { signIn ->
            if (signIn.presentingViewController == null) {
                host.presentViewController(signIn, animated = true, completion = null)
            }
            return
        }

        if (!GKLocalPlayer.local.authenticated) return

        val controller = if (leaderboardId == null) {
            GKGameCenterViewController.create(GKGameCenterViewControllerStateLeaderboards)
        } else {
            GKGameCenterViewController.create(
                leaderboardID = leaderboardId,
                playerScope = GKLeaderboardPlayerScopeGlobal,
                timeScope = GKLeaderboardTimeScopeAllTime,
            )
        }
        controller.gameCenterDelegate = dismissOnFinish
        host.presentViewController(controller, animated = true, completion = null)
    }

    private fun onAuthenticationChanged(viewController: UIViewController?, error: NSError?) {
        when {
            viewController != null -> {
                signInViewController = viewController
                state.value = GameServicesStatus.SignInRequired
            }

            GKLocalPlayer.local.authenticated -> {
                signInViewController = null
                state.value = GameServicesStatus.Authenticated
            }

            else -> {
                signInViewController = null
                state.value = GameServicesStatus.Unavailable
                logger.i { "Game Center unavailable: ${error?.localizedDescription ?: "not signed in"}" }
            }
        }
    }

    private fun topViewController(): UIViewController? {
        val keyWindow = UIApplication.sharedApplication.connectedScenes
            .asSequence()
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>().asSequence() }
            .firstOrNull { it.isKeyWindow() }
            ?: return null

        var top: UIViewController? = keyWindow.rootViewController
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        return top
    }
}

private class DismissingDelegate : NSObject(), GKGameCenterControllerDelegateProtocol {
    override fun gameCenterViewControllerDidFinish(
        gameCenterViewController: GKGameCenterViewController,
    ) {
        gameCenterViewController.dismissViewControllerAnimated(flag = true, completion = null)
    }
}

/**
 * A `GKAchievement` at 100% that will **not** draw Game Center's own banner.
 *
 * `showsCompletionBanner = false` is set explicitly, and the explicitness is
 * the whole point. `GKAchievement` defaults it to **`true`**, not false, so an
 * achievement constructed and reported without touching it puts Game Center's
 * banner on screen at the same instant the app draws its own unlock toast. Two
 * banners for one badge is a bug the player attributes to us, and it is a bug
 * only an iOS device shows: nothing in the JVM suite can reach GameKit.
 *
 * This is not a guess about Apple's default. `GKAchievementIosTest` reads it
 * back off a real `GKAchievement`, so if Apple ever flips it the test says so
 * rather than this comment quietly becoming wrong. It was wrong once already:
 * the KDoc here claimed the default was `false` and shipped the double banner
 * it was written to prevent.
 *
 * Extracted from `reportAchievement` so there is something to assert against
 * without a signed-in Game Center player.
 */
internal fun completedAchievement(achievementId: String): GKAchievement =
    GKAchievement(identifier = achievementId).apply {
        percentComplete = 100.0
        showsCompletionBanner = false
    }
