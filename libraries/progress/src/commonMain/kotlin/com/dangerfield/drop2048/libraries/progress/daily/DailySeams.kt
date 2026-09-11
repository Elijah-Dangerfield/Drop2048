package com.dangerfield.drop2048.libraries.progress.daily

/**
 * SPEC 12's rewarded Daily retry placement.
 *
 * A seam, declared before the ad system existed, with its one real consumer
 * already reading it — because the alternative pattern is the one Sodogku's S15
 * is a post-mortem of: an integration with no production call site looks like
 * coverage and is not. C10 bound it to a real rewarded placement
 * (`RewardedDailyRetryAd` in `:libraries:ads:impl`) without `DailyRepositoryImpl`
 * moving a line, which is what the seam was for.
 *
 * An ad network with no fill answers [RewardOutcome.Unavailable], which the
 * repository treats as "no retry granted, and none spent" rather than as an
 * error.
 *
 * The fail-open rule is [RewardOutcome.Dismissed]: **only a deliberate dismissal
 * withholds the reward.** A network error, a timeout or a missing fill must not
 * be what costs someone their streak.
 */
fun interface DailyRetryAd {
    suspend fun show(): RewardOutcome
}

enum class RewardOutcome {
    /** Watched to the reward point. */
    Earned,

    /** Closed early. The only outcome that withholds the reward. */
    Dismissed,

    /** No ad system, no fill, or a failure. Never charged to the player. */
    Unavailable,
}
