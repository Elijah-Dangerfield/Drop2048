package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.ads.AdGate
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.RewardOutcome as AdRewardOutcome
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryAd
import com.dangerfield.drop2048.libraries.progress.daily.RewardOutcome
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 12's rewarded Daily retry, at last attached to a network.
 *
 * C6 built [DailyRetryAd] as a seam with a binding that always answered
 * "unavailable", and the whole value of that shape shows up here: this class is
 * the only thing that changed, `DailyRepositoryImpl` did not move a line, and the
 * cap it enforces was already on disk in `daily_result.retriesUsed` so a
 * force-quit could never reset it.
 *
 * This is now the **only** binding for the seam. C6's `NoRetryAdYet` is deleted
 * rather than replaced: a `replaces` would have needed `:libraries:ads:impl` to
 * depend on `:libraries:progress:impl`, and one impl module reaching into
 * another's internals to switch off a placeholder is a worse dependency than the
 * placeholder was worth.
 *
 * ### The three-way mapping is the point
 *
 * The Daily's [RewardOutcome] has the same shape as the ad layer's for one
 * reason: **only a deliberate dismissal withholds the reward.** A no-fill, an
 * offline device or an SDK failure all become [RewardOutcome.Unavailable], which
 * the repository treats as "no retry granted, no retry spent" — the player is
 * exactly where they were. Collapsing those into `Dismissed` would spend the
 * day's one retry on an ad that never played, and there is no way to buy that
 * back.
 *
 * Note which way the asymmetry runs. On the continue placement an unserved ad
 * *grants*, because the player is mid-run and losing the board is the harm. Here
 * an unserved ad grants **nothing and costs nothing**, because the harm would be
 * spending a cap. Same principle, opposite mechanic: never let an ad failure take
 * something from the player.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DailyRetryAd::class)
@Inject
class RewardedDailyRetryAd(
    private val gate: AdGate,
) : DailyRetryAd {

    override suspend fun show(): RewardOutcome = when (gate.showRewarded(AdPlacement.DailyRetry)) {
        AdRewardOutcome.Rewarded -> RewardOutcome.Earned
        AdRewardOutcome.Dismissed -> RewardOutcome.Dismissed
        AdRewardOutcome.NoFill,
        AdRewardOutcome.Offline,
        is AdRewardOutcome.Failed,
        -> RewardOutcome.Unavailable
    }
}
