package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdGate
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ads.HouseAds
import com.dangerfield.drop2048.libraries.ads.networkOr
import com.dangerfield.drop2048.libraries.ads.RewardOutcome
import com.dangerfield.drop2048.libraries.ads.UnservedAdGate
import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.gameconfig.AdsEnabled
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The real [AdGate]: the two rewarded placements, and the one rule about what
 * happens when an ad cannot be served.
 *
 * ## A failed ad pays the player
 *
 * SPEC 12 separates [RewardOutcome.Dismissed] from every kind of failure, and
 * this is where that separation is spent: no fill, an SDK that threw, no route
 * out of the device, ads switched off from the console — every one of them ends
 * in something the call sites treat as a grant. That is not politeness. A
 * rewarded continue is the only thing standing between a player and a board they
 * have spent ten minutes building, and an ad network having a bad afternoon must
 * not be able to take it.
 *
 * There is exactly one branch below that returns `Dismissed`, and it is the one
 * where the player closed the ad.
 *
 * ## Pro still gets the offer
 *
 * SPEC 12 keeps rewarded video available to Pro holders, because removing it is
 * taking something away — a second continue is worth more to someone who bought
 * the game than a saved thirty seconds. So there is no `isPro` branch here at
 * all. Pro removes *interstitials*, and that is [RealInterstitialGate]'s job.
 *
 * ## Every config value is read at the point of use
 *
 * Nothing is captured in a field. `ads.enabled` is a kill switch, and a kill
 * switch cached for the life of the process is a kill switch that works next
 * launch.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AdGate::class,
    replaces = [UnservedAdGate::class],
)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RealAdGate(
    private val platformNetwork: AdNetwork,
    private val houseAds: HouseAds,
    private val adState: AdStateCache,
    private val rewardedClock: RewardedClock,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
    private val adsEnabled: AdsEnabled,
) : AdGate, AutoInit {

    private val logger = KLog.withTag("AdGate")

    /**
     * Resolved per call rather than held, for the reason every config read here
     * is: a network captured in a field is a switch that takes effect next
     * launch. In a release build [HouseAds.network] is null and this is always
     * the platform network.
     */
    private val network: AdNetwork get() = houseAds.networkOr(platformNetwork)

    init {
        // SPEC 12's three-day install suppression counts from the first launch
        // the ad layer saw, not from the first ad request. Stamping it lazily
        // would mean a player who never reached an ad gate in three days got
        // three more days from the moment they did, which is the grace measuring
        // itself.
        appScope.launch {
            Catching {
                adState.update { state ->
                    if (state.firstSeenAtMs == 0L) state.copy(firstSeenAtMs = now()) else state
                }
            }.logOnFailure { "Could not stamp the ad install date" }
        }
    }

    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome =
        Catching { rewarded(placement) }
            .logOnFailure { "Rewarded ad path threw for $placement; the caller pays anyway" }
            .getOrElse { RewardOutcome.Failed(it::class.simpleName ?: "unknown") }

    override fun preload(placement: AdPlacement) {
        if (!adsEnabled()) return
        appScope.launch {
            Catching {
                network.prepare()
                network.preload(AdFormat.Rewarded)
            }.logOnFailure { "Preload failed for $placement" }
        }
    }

    private suspend fun rewarded(placement: AdPlacement): RewardOutcome {
        logger.logEvent("ads.rewarded_requested", "placement" to placement.id)

        if (!adsEnabled()) {
            logger.logEvent(
                "ads.rewarded_result",
                "placement" to placement.id,
                "outcome" to "granted_without_ad",
                "reason" to "ads_disabled",
            )
            return RewardOutcome.Rewarded
        }

        // Held across the whole show, and this is the forward half of SPEC 12's
        // 45-second rule: while it is true no interstitial can fire, so a
        // stacked-out sheet dismissed underneath a rewarded ad cannot stack a
        // second full-screen ad on top of the first.
        rewardedClock.enter()
        val started = now()
        val outcome = try {
            network.prepare()
            network.show(AdFormat.Rewarded)
        } finally {
            rewardedClock.leave()
            Catching { adState.update { it.copy(lastRewardedAtMs = now()) } }
                .logOnFailure { "Could not record the rewarded ad timestamp" }
        }

        logger.logEvent(
            "ads.rewarded_result",
            "placement" to placement.id,
            "outcome" to outcome.result.name,
            "latency_ms" to (now() - started),
            "error_kind" to outcome.errorKind,
        )

        return when (outcome.result) {
            AdShowResult.Rewarded -> RewardOutcome.Rewarded
            AdShowResult.Dismissed -> RewardOutcome.Dismissed
            AdShowResult.NoFill -> RewardOutcome.NoFill
            AdShowResult.NotShown -> RewardOutcome.NoFill
            AdShowResult.Offline -> RewardOutcome.Offline
            AdShowResult.Failed -> RewardOutcome.Failed(outcome.errorKind ?: "sdk")
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()
}

/**
 * Whether a rewarded ad is on screen right now, shared between the two gates.
 *
 * A counter rather than a boolean because `enter`/`leave` are in a `finally` and
 * two placements could in principle overlap — a boolean would have the second
 * `leave` clear a flag the first still needs. It is not a lock: nothing waits on
 * it, and the interstitial gate reading `true` refuses rather than blocks.
 */
@SingleIn(AppScope::class)
@Inject
class RewardedClock {
    private var depth: Int = 0

    val inFlight: Boolean get() = depth > 0

    fun enter() {
        depth++
    }

    fun leave() {
        if (depth > 0) depth--
    }
}
