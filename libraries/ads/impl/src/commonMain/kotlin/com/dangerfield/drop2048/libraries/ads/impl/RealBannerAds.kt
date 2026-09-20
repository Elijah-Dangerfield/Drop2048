package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.ads.AdImpressions
import com.dangerfield.drop2048.libraries.ads.BannerAds
import com.dangerfield.drop2048.libraries.ads.NoAdImpressions
import com.dangerfield.drop2048.libraries.ads.NoBannerAds
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.gameconfig.AdsEnabled
import com.dangerfield.drop2048.libraries.gameconfig.BannerEnabled
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * D28's banner policy: three reads and no state.
 *
 * Every one of the three is resolved **per call** rather than cached in a field,
 * for the reason `ads.enabled` is: a value cached at construction is a switch
 * that works next launch, and the whole argument for `ads.banner.enabled` is
 * that it works without a release.
 *
 * There is no readiness gate and no preload here, unlike
 * [RealInterstitialGate]. A banner is requested by the view that draws it, at
 * the moment it appears, and the answer to "it did not fill" is a strip of
 * nothing that the board takes, so there is no wait to protect the player from
 * and nothing to warm.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = BannerAds::class,
    replaces = [NoBannerAds::class],
)
@Inject
class RealBannerAds(
    private val entitlements: Entitlements,
    private val adState: AdStateCache,
    private val appScope: AppCoroutineScope,
    private val clock: Clock,
    private val adsEnabled: AdsEnabled,
    private val bannerEnabled: BannerEnabled,
) : BannerAds {

    private val logger = KLog.withTag("Banner")

    override fun isAllowed(): Boolean =
        adsEnabled() && bannerEnabled() && !entitlements.isPro.value

    override fun noteFilled() {
        appScope.launch {
            Catching { adState.update { it.copy(lastBannerAtMs = clock.now().toEpochMilliseconds()) } }
                .logOnFailure { "Could not record the banner impression" }
            logger.logEvent("ads.banner_filled")
        }
    }
}

/**
 * "Has this player ever actually been shown an ad", read from the one file that
 * knows.
 *
 * Its own class rather than another face on [RealInterstitialGate], because the
 * gate is about *when an interstitial may appear* and this is about *what has
 * already happened*, and because the banner made the question span two formats.
 * Both read [AdStateCache], so there is still exactly one record.
 *
 * A failed read answers `false`, which withholds the upsell card. That is the
 * right direction: the failure mode of guessing "yes" is the exact lie D28
 * exists to stop telling.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AdImpressions::class,
    replaces = [NoAdImpressions::class],
)
@Inject
class RealAdImpressions(
    private val adState: AdStateCache,
) : AdImpressions {

    override suspend fun anyAdShown(): Boolean {
        val state = Catching { adState.get() }
            .logOnFailure { "Could not read the ad state; assuming no ad has been shown" }
            .getOrDefault(AdState())
        return state.lastInterstitialAtMs != 0L || state.lastBannerAtMs != 0L
    }
}
