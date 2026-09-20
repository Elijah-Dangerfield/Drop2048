package com.dangerfield.drop2048.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Every rewarded slot the app has. SPEC 12 named two; D27 removed the Daily and
 * its retry with it, so there is one.
 *
 * It is **player-initiated**, which is not a coincidence to be tidied away
 * later: the enum exists so that adding a slot is a visible edit here rather than
 * a new string threaded through a call. Sodogku shipped a `LevelComplete`
 * interstitial placement in the same enum, gave it three remote keys, and never
 * called it from anything — the one ad nobody asked for was a single call site
 * away from shipping by accident. Interstitials live in [InterstitialGate]
 * instead, where the type itself says they are not chosen.
 */
enum class AdPlacement(val id: String) {
    /** SPEC 12: continue after stacked out. 1 free per run, a 2nd at higher friction, hard cap 2. */
    ContinueRun("continue_run"),
}

/**
 * How a rewarded ad ended.
 *
 * The shape here is the most load-bearing thing in the module: [Dismissed] is
 * separated from every kind of failure **because only a deliberate dismissal may
 * withhold the reward.** A network with no fill, an SDK that threw, a device with
 * no route out — none of those is the player saying no, and modelling "didn't
 * watch" as one boolean is exactly how a bad afternoon at the ad network ends up
 * costing someone the run they were in the middle of.
 */
sealed interface RewardOutcome {
    /** Watched to the reward point. Grant it. */
    data object Rewarded : RewardOutcome

    /** The player closed it early. The only outcome that withholds. */
    data object Dismissed : RewardOutcome

    /** No ad was available to serve. */
    data object NoFill : RewardOutcome

    /** No route to the network. */
    data object Offline : RewardOutcome

    /** The SDK failed. [kind] is for telemetry, never for the player. */
    data class Failed(val kind: String) : RewardOutcome
}

/**
 * The rewarded half of advertising: the player asked, so show them one.
 *
 * There is no `mayShow` here on purpose. Every caller has already drawn a control
 * that says an ad is coming and the player has already pressed it, so the only
 * question left is what happened — and the answer to "we could not" is a granted
 * reward rather than a refusal.
 */
interface AdGate {
    suspend fun showRewarded(placement: AdPlacement): RewardOutcome

    /** Warms a placement so it is ready when the moment arrives. Fire and forget. */
    fun preload(placement: AdPlacement)
}

/**
 * The rewarded gate on a build with no ad network wired, and the reason this
 * class is not the obvious one.
 *
 * The obvious binding grants the reward — the app pays on every failure anyway,
 * and "no network at all" is the largest failure there is. Sodogku shipped
 * exactly that on iOS and its own history records the result: a stub that paid
 * out for free, indistinguishable in every log from an ad nobody watched, for
 * as long as nobody looked.
 *
 * So this one answers [RewardOutcome.NoFill] and the **call sites** decide what
 * an unserved ad is worth. `RealAdGate` is where "a failure still pays" lives,
 * and it is a real implementation sitting on a real network; a placeholder that
 * hands out continues is not a safe default, it is a free continue button with a
 * misleading name.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class UnservedAdGate : AdGate {
    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome = RewardOutcome.NoFill

    override fun preload(placement: AdPlacement) = Unit
}
