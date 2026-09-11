package com.dangerfield.drop2048.libraries.ads.impl

/**
 * Why an interstitial was not shown. One entry per rule in SPEC 12.3, plus the
 * two that are about the product rather than the frequency.
 *
 * Named rather than boolean because these are the only evidence that the gates
 * work: `ads.interstitial_blocked` carries one of these, so a live app can be
 * asked "which gate is doing the work" without a code change. A single `false`
 * would make every gate look identical in Loki and make a broken one invisible.
 */
enum class InterstitialBlock(val reason: String) {
    /** `ads.enabled` is off. The kill switch, and it covers rewarded too. */
    AdsDisabled("ads_disabled"),

    /** SPEC 12: Pro removes all interstitial advertising permanently. */
    Pro("pro"),

    /**
     * A run is alive. **The governing principle, and the only block here that is
     * not negotiable** — SPEC 12: the player never sees an ad they did not choose
     * while a run is alive.
     */
    RunAlive("run_alive"),

    /** The results sheet is still up. SPEC 12: only after the player dismisses it. */
    ResultsNotDismissed("results_not_dismissed"),

    /** SPEC 12: not before the 4th run of a session. */
    TooFewRunsThisSession("too_few_runs_this_session"),

    /** SPEC 12: 180s minimum since the last one. */
    Cooldown("cooldown"),

    /** SPEC 12: never within 45s of a rewarded ad, in either direction. */
    NearRewarded("near_rewarded"),

    /** SPEC 12: suppressed entirely for the first 3 days after install. */
    NewInstall("new_install"),

    /** SPEC 12: preloaded, and skipped silently if not ready rather than showing a spinner. */
    NotReady("not_ready"),
}

/**
 * Everything SPEC 12.3 needs to know, gathered by the caller so that the decision
 * itself can be a pure function.
 *
 * The clock arrives as elapsed millis rather than as an instant, and the config
 * arrives as resolved numbers rather than as `ConfiguredValue`s, so this file has
 * no dependency on time, storage, config or DI. That is what lets each rule have
 * a test that fails for exactly one reason.
 *
 * @param millisSinceLastInterstitial null means never, which is a **pass**: a
 *   player who has never seen one is not in a cooldown.
 * @param millisSinceRewarded null likewise means never.
 */
data class InterstitialConditions(
    val adsEnabled: Boolean,
    val isPro: Boolean,
    val runAlive: Boolean,
    val resultsDismissed: Boolean,
    val runsThisSession: Int,
    val minSessionRuns: Int,
    val millisSinceLastInterstitial: Long?,
    val cooldownMillis: Long,
    val millisSinceRewarded: Long?,
    val rewardedInFlight: Boolean,
    val rewardedGapMillis: Long,
    val daysSinceInstall: Int,
    val suppressDaysSinceInstall: Int,
    val preloaded: Boolean,
)

/**
 * SPEC 12.3, as one expression.
 *
 * ### Why a pure function and not a chain of `if`s inside the gate
 *
 * Eight rules that all have to hold is eight ways to be wrong, and the wrong
 * versions are all silent: nobody notices an interstitial that is 10% too
 * frequent, and nobody notices a gate that stopped being evaluated. Putting the
 * decision here means `AdPolicyTest` can hold one rule at a time against a
 * baseline that would otherwise show, which is a test that fails for exactly one
 * reason — and can hold all eight in combination without a network, a clock, a
 * coroutine or a DI graph.
 *
 * ### The order is deliberate
 *
 * [InterstitialBlock.RunAlive] is evaluated **before** anything about frequency,
 * because it is the one rule SPEC 12 calls non-negotiable and the only one whose
 * violation is a bug rather than a tuning error. Putting it after the cheap
 * config reads would mean a live-run interstitial suppressed by a cooldown looked
 * identical in telemetry to one correctly refused, and the evidence that the
 * principle holds would be gone.
 *
 * [InterstitialBlock.NotReady] is last for the opposite reason: it is the only
 * block that says nothing about whether an ad was *allowed*, and reporting it
 * ahead of a real refusal would make a Pro player's suppressed ad look like an
 * inventory problem.
 */
object InterstitialPolicy {

    fun decide(conditions: InterstitialConditions): InterstitialBlock? = with(conditions) {
        when {
            runAlive -> InterstitialBlock.RunAlive
            !resultsDismissed -> InterstitialBlock.ResultsNotDismissed
            !adsEnabled -> InterstitialBlock.AdsDisabled
            isPro -> InterstitialBlock.Pro
            daysSinceInstall < suppressDaysSinceInstall -> InterstitialBlock.NewInstall
            runsThisSession < minSessionRuns -> InterstitialBlock.TooFewRunsThisSession
            withinCooldown() -> InterstitialBlock.Cooldown
            nearRewarded() -> InterstitialBlock.NearRewarded
            !preloaded -> InterstitialBlock.NotReady
            else -> null
        }
    }

    /**
     * A configured zero disables the cooldown rather than blocking forever, which
     * is the reading that matches every other window in the app. The protection
     * against a typo is upstream: an unparseable remote value resolves to null and
     * the compiled default (180s) wins, so only a deliberate zero can get here.
     */
    private fun InterstitialConditions.withinCooldown(): Boolean {
        if (cooldownMillis <= 0) return false
        val since = millisSinceLastInterstitial ?: return false
        return since < cooldownMillis
    }

    /**
     * Both halves of "never within 45s of a rewarded ad in either direction".
     *
     * Backwards is the arithmetic one. **Forwards is [rewardedInFlight]**, and it
     * is the half that is easy to miss because the future is not a number you can
     * subtract: the only moment the app knows a rewarded ad is about to happen is
     * while the player is looking at the offer for one. That offer sits on the
     * stacked-out sheet, which is the same sheet whose dismissal fires the
     * interstitial — so without this flag the 8-second continue countdown and the
     * interstitial are one mistimed dismissal away from racing each other.
     */
    private fun InterstitialConditions.nearRewarded(): Boolean {
        if (rewardedInFlight) return true
        if (rewardedGapMillis <= 0) return false
        val since = millisSinceRewarded ?: return false
        return since < rewardedGapMillis
    }
}
