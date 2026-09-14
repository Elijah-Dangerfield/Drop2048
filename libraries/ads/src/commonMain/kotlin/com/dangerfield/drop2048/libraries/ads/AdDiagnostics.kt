package com.dangerfield.drop2048.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Every input SPEC 12.3's interstitial decision is made from, plus the answer.
 *
 * SPEC 19 asks the debug menu for "current interstitial cooldown remaining" and
 * "session run count and days-since-install" — which are two of the eight inputs
 * — and C10 gave `ads.interstitial_blocked` a **named** reason so a live app
 * could be asked which gate was doing the work. This type is those two facts
 * joined: the same reason, on the device, before the ad is asked for rather than
 * after.
 *
 * That matters more here than it does in Loki. An interstitial that does not
 * appear looks identical whether it was suppressed by the install grace, the
 * cooldown, the session count or a missing preload, and the only thing a tester
 * can do with "nothing happened" is guess. [blockedReason] turns a silent
 * refusal into a sentence.
 *
 * @param secondsSinceLastInterstitial null means never, which passes the
 *   cooldown rather than failing it.
 */
data class AdGateSnapshot(
    val adsEnabled: Boolean,
    val isPro: Boolean,
    val runAlive: Boolean,
    val runsThisSession: Int,
    val minSessionRuns: Int,
    val secondsSinceLastInterstitial: Long?,
    val cooldownSeconds: Int,
    val secondsSinceRewarded: Long?,
    val rewardedInFlight: Boolean,
    val rewardedGapSeconds: Int,
    val daysSinceInstall: Int,
    val suppressDaysSinceInstall: Int,
    val interstitialPreloaded: Boolean,

    /**
     * The reason an interstitial would be refused **right now, at a dismissed
     * results sheet**, or null if one would show.
     *
     * The hypothetical moment is the point: asked at any other moment the answer
     * is always `results_not_dismissed`, which tells a tester nothing. It is the
     * same `InterstitialBlock.reason` string `ads.interstitial_blocked` carries,
     * so a screenshot from a device and a dashboard say the same word.
     */
    val blockedReason: String?,

    /** Which network the gates are routed to, for the one line QA actually reads. */
    val networkName: String,
) {
    /** SPEC 19's "current interstitial cooldown remaining". Zero when nothing is owed. */
    val cooldownRemainingSeconds: Long
        get() = secondsSinceLastInterstitial
            ?.let { (cooldownSeconds - it).coerceAtLeast(0L) }
            ?: 0L
}

/**
 * Read-only access to the interstitial gate's own reasoning.
 *
 * Separate from [InterstitialGate] on purpose. That interface has one method
 * that can show anything and exactly one caller, and keeping it that narrow is
 * what makes SPEC 12's governing principle hold by construction; hanging a
 * debug read on it would widen the type every future call site sees. Nothing
 * here can show, warm or record an ad.
 */
interface AdDiagnostics {
    suspend fun snapshot(): AdGateSnapshot
}

/**
 * The answer on a build with no real interstitial gate, so the debug menu can
 * be assembled without one.
 *
 * `ad_gate_not_wired` rather than a blank: a screen that says nothing is
 * indistinguishable from a screen that failed to load, which is the failure mode
 * this whole type exists to remove.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoAdDiagnostics : AdDiagnostics {
    override suspend fun snapshot(): AdGateSnapshot = AdGateSnapshot(
        adsEnabled = false,
        isPro = false,
        runAlive = false,
        runsThisSession = 0,
        minSessionRuns = 0,
        secondsSinceLastInterstitial = null,
        cooldownSeconds = 0,
        secondsSinceRewarded = null,
        rewardedInFlight = false,
        rewardedGapSeconds = 0,
        daysSinceInstall = 0,
        suppressDaysSinceInstall = 0,
        interstitialPreloaded = false,
        blockedReason = "ad_gate_not_wired",
        networkName = "none",
    )
}
