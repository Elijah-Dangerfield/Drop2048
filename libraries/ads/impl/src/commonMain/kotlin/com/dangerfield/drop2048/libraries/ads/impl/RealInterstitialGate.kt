package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.InterstitialGate
import com.dangerfield.drop2048.libraries.ads.NoInterstitials
import com.dangerfield.drop2048.libraries.ads.RunActivity
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.gameconfig.AdsEnabled
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialCooldownSeconds
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialMinSessionRuns
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialRewardedGapSeconds
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialSuppressDaysSinceInstall
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * SPEC 12.3's interstitial, and the plumbing around the decision.
 *
 * The decision itself is [InterstitialPolicy], deliberately: everything
 * interesting about this class is a fact-gathering exercise — read four config
 * values, three timestamps, a session counter and an entitlement — and the rules
 * are somewhere a test can hold them one at a time.
 *
 * What is left here is worth reading for two things.
 *
 * **[showIfReady] asks the network whether it is loaded and gives up if it is
 * not.** SPEC 12 says preloaded, and skipped silently if not ready rather than
 * showing a spinner. There is no `withTimeout`, no loading state and no retry,
 * because every one of those is a way to make a player wait for an advertisement
 * they did not ask for.
 *
 * **The timestamp is written before the ad is shown, not after.** A full-screen
 * ad can take thirty seconds and can be the last thing that happens before the
 * process is killed; recording the impression on the way out would mean a player
 * who backgrounds the app during an interstitial is eligible for another one
 * immediately. Writing it on the way in costs at most one skipped ad after a
 * failed show, which is the direction to be wrong in.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = InterstitialGate::class,
    replaces = [NoInterstitials::class],
)
@Inject
class RealInterstitialGate(
    private val network: AdNetwork,
    private val entitlements: Entitlements,
    private val runActivity: RunActivity,
    private val rewardedClock: RewardedClock,
    private val adState: AdStateCache,
    private val session: AdSession,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
    private val adsEnabled: AdsEnabled,
    private val minSessionRuns: InterstitialMinSessionRuns,
    private val cooldownSeconds: InterstitialCooldownSeconds,
    private val rewardedGapSeconds: InterstitialRewardedGapSeconds,
    private val suppressDays: InterstitialSuppressDaysSinceInstall,
) : InterstitialGate {

    private val logger = KLog.withTag("Interstitial")

    override fun noteRunFinished() {
        session.noteRunFinished()
    }

    override fun noteRewardedShown() {
        appScope.launch {
            Catching { adState.update { it.copy(lastRewardedAtMs = now()) } }
                .logOnFailure { "Could not record the rewarded ad timestamp" }
        }
    }

    override suspend fun showIfReady(): Boolean =
        Catching { show() }
            .logOnFailure { "Interstitial path threw; showing nothing" }
            .getOrDefault(false)

    override fun preload() {
        if (!adsEnabled()) return
        if (entitlements.isPro.value) return
        appScope.launch {
            Catching {
                network.prepare()
                network.preload(AdFormat.Interstitial)
            }.logOnFailure { "Interstitial preload failed" }
        }
    }

    private suspend fun show(): Boolean {
        val state = Catching { adState.get() }
            .logOnFailure { "Could not read the ad state; treating it as a fresh install" }
            .getOrDefault(AdState())
        val now = now()

        val block = InterstitialPolicy.decide(
            InterstitialConditions(
                adsEnabled = adsEnabled(),
                isPro = entitlements.isPro.value,
                runAlive = runActivity.isRunAlive,
                // The only caller is the dismissal itself. It is still an input
                // rather than a constant so the rule has somewhere to be tested,
                // and so a second call site added later has to say what moment it
                // is at rather than inheriting an assumption.
                resultsDismissed = true,
                runsThisSession = session.runsFinished(),
                minSessionRuns = minSessionRuns(),
                millisSinceLastInterstitial = state.lastInterstitialAtMs.elapsedSince(now),
                cooldownMillis = cooldownSeconds() * MillisPerSecond,
                millisSinceRewarded = state.lastRewardedAtMs.elapsedSince(now),
                rewardedInFlight = rewardedClock.inFlight,
                rewardedGapMillis = rewardedGapSeconds() * MillisPerSecond,
                daysSinceInstall = daysSince(state.firstSeenAtMs, now),
                suppressDaysSinceInstall = suppressDays(),
                preloaded = network.isReady(AdFormat.Interstitial),
            )
        )

        if (block != null) {
            logger.logEvent("ads.interstitial_blocked", "reason" to block.reason)
            // Warm the next one. A player blocked by the cooldown today is
            // eligible in three minutes, and the whole point of the readiness
            // gate is that nothing is ever waited for.
            preload()
            return false
        }

        Catching { adState.update { it.copy(lastInterstitialAtMs = now) } }
            .logOnFailure { "Could not record the interstitial timestamp" }

        val outcome = network.show(AdFormat.Interstitial)
        logger.logEvent(
            "ads.interstitial_result",
            "outcome" to outcome.result.name,
            "error_kind" to outcome.errorKind,
            "runs_this_session" to session.runsFinished(),
        )
        preload()
        return outcome.result != AdShowResult.NotShown
    }

    /**
     * `0` means never, and never is not `now - 0` — an epoch of zero would read
     * as fifty-six years of elapsed time, which passes every window by accident
     * rather than on purpose. Null says "no such event" and the policy treats it
     * as a pass explicitly.
     */
    private fun Long.elapsedSince(now: Long): Long? =
        if (this == 0L) null else (now - this).coerceAtLeast(0L)

    /**
     * A device whose clock has been moved backwards reads as day zero rather
     * than as a negative number of days, which suppresses interstitials. That is
     * the safe direction, and it is also the one that cannot be farmed: the other
     * rounding would let a player change their clock to skip the install grace,
     * and there is nothing to gain by skipping *into* advertising.
     */
    private fun daysSince(fromMs: Long, now: Long): Int {
        if (fromMs == 0L) return 0
        return ((now - fromMs).coerceAtLeast(0L) / MillisPerDay).toInt()
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private companion object {
        const val MillisPerSecond = 1_000L
        const val MillisPerDay = 86_400_000L
    }
}
