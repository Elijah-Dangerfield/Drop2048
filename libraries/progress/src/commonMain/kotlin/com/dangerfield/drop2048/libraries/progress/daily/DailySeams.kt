package com.dangerfield.drop2048.libraries.progress.daily

/**
 * Whether the player holds Pro (SPEC 12).
 *
 * Pro does not exist until C10 and the shipped binding answers false. It is
 * declared now, with its one real consumer already reading it, because the
 * alternative pattern is the one Sodogku's S15 is a post-mortem of: an
 * integration with no production call site looks like coverage and is not. When
 * `:libraries:billing` lands it replaces the binding and the second Daily
 * attempt starts working, with no edit to the rule that uses it.
 *
 * Deliberately synchronous and deliberately not a `Flow`. The question is asked
 * once, at the moment an attempt is requested, and an entitlement that changed
 * mid-run must not retroactively change how many attempts the day had.
 */
fun interface ProEntitlement {
    fun isPro(): Boolean
}

/**
 * SPEC 12's rewarded Daily retry placement.
 *
 * The same seam as [ProEntitlement] and the same reason. Until C10 the shipped
 * binding answers [RewardOutcome.Unavailable], which the repository treats as
 * "no retry" rather than as an error — and when the real one lands, an ad
 * network with no fill answers the same way.
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
