@file:OptIn(ExperimentalObjCName::class)

package com.dangerfield.drop2048.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The three SDK formats the app serves.
 *
 * [Banner] arrived on 2026-09-20 and overturned SPEC 12.4's "Banners: none",
 * which this enum used to state in prose. The reasoning that rule was built on
 * has not been thrown away, it has been answered: the banner is drawn **only
 * where the arrow row would have been**, so it never takes vertical space the
 * board was using, and it occupies no space at all until an ad is really on
 * screen, so a build with no fill is a build with no strip. D28 has the full
 * trade, including the principle that was spent to get it.
 *
 * [Banner] is also the odd one out mechanically: [AdNetwork.show] cannot serve
 * it, because a banner is a view that lives in the layout rather than a
 * full-screen thing that is shown and dismissed. It is served by
 * [BannerSurface], and the entry here exists so [AdUnits] has somewhere to keep
 * the unit id and so a future network cannot forget the format exists.
 */
@ObjCName("AdFormat", exact = true)
enum class AdFormat {
    Rewarded,
    Interstitial,
    Banner,
}

/**
 * What the SDK did, flattened to something an ObjC-facing enum can carry.
 *
 * Deliberately *not* [RewardOutcome]: the Swift side has to be able to build a
 * return value, and a sealed hierarchy of Kotlin data objects is awkward to
 * construct from Swift. `RealAdGate` widens this back into the richer sealed
 * type, which is also the one place the fail-open mapping lives.
 */
@ObjCName("AdShowResult", exact = true)
enum class AdShowResult {
    /** Watched to the reward threshold. */
    Rewarded,

    /** Closed early, by the player, on purpose. The only result that withholds. */
    Dismissed,

    /** The network had nothing to serve. */
    NoFill,

    /** The SDK reported no route to the network. */
    Offline,

    /** Nothing was attempted — the SDK is not initialised, or has no ad loaded. */
    NotShown,

    /** Anything else. [AdShowOutcome.errorKind] carries the detail, for telemetry only. */
    Failed,
}

/**
 * A result plus an optional machine-readable failure kind. Never a message for
 * the player — an ad failure is invisible to them by design.
 */
@ObjCName("AdShowOutcome", exact = true)
class AdShowOutcome(
    val result: AdShowResult,
    val errorKind: String? = null,
)

/**
 * The thin platform seam under [AdGate] and [InterstitialGate]: load an ad, show
 * it, say what happened.
 *
 * Everything policy-shaped — every gate in SPEC 12.3, the caps, the rule that a
 * failed rewarded ad still pays the player — sits above this in common code, so
 * it is the same on both platforms and testable without an ad network.
 *
 * **Android binding** is `AdMobAdNetwork` in `:libraries:ads:impl/androidMain`.
 * **iOS has no real binding yet** and falls through to [NotWiredAdNetwork].
 *
 * Implementations must not throw. An ad SDK that blows up has to look like
 * [AdShowResult.Failed] from here.
 */
@ObjCName("AdNetwork", exact = true)
interface AdNetwork {

    /**
     * Consent first, then SDK init. On Android that is UMP then
     * `MobileAds.initialize`; on iOS it will be UMP **then** ATT **then**
     * `MobileAds.start`. Both orders are a store requirement rather than a
     * preference — an ad request that beats the consent form is a policy
     * violation — so every show path awaits this before it touches the SDK.
     *
     * Idempotent, and cheap after the first call.
     */
    suspend fun prepare()

    /**
     * Shows an already-loaded ad, or loads one first. Suspends until it closes.
     * The implementation resolves the ad unit itself from [AdUnits], so there is
     * exactly one file to edit when the real ids arrive.
     *
     * [AdFormat.Banner] has no answer here and must return
     * [AdShowResult.NotShown]. A banner is a view in a layout, not something
     * that is shown over the app and dismissed, so there is nothing for this
     * call to suspend on. [BannerSurface] serves it.
     */
    suspend fun show(format: AdFormat): AdShowOutcome

    /**
     * Whether a [format] is loaded and showable **right now**, without a network
     * round trip.
     *
     * SPEC 12.3's "preloaded, and skipped silently if not ready" is the only
     * reason this exists. Without it the interstitial path has to attempt a show
     * to discover there is nothing to show, and an attempt is a wait.
     */
    fun isReady(format: AdFormat): Boolean

    /** Warms a format. Fire and forget; failures are the SDK's problem, not the caller's. */
    fun preload(format: AdFormat)
}

/**
 * The network on a platform with no SDK wired, and the single most important
 * thing in this file is what it does **not** return.
 *
 * It never answers [AdShowResult.Rewarded]. Sodogku shipped an iOS ad stub that
 * paid out rewards without showing anything, and its own post-mortem is the
 * reason this class is written down rather than defaulted: a reward that grants
 * without an ad is worse than no ads at all, because it is invisible. Every log
 * line, every dashboard and every test reads exactly as it would if the ad had
 * played.
 *
 * [AdShowResult.NotShown] with a named [AdShowOutcome.errorKind] is the honest
 * answer, and it is the one the layer above already knows how to handle: the
 * rewarded path pays the player anyway because that is a deliberate rule applied
 * to a real outcome, and the interstitial path shows nothing because there is
 * nothing to show. Neither is a lie about an impression.
 *
 * iOS needs `IOSAdNetwork` in Swift plus the GoogleMobileAds package on the
 * Xcode target; both are on `OWNER-TODO.md`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NotWiredAdNetwork : AdNetwork {
    override suspend fun prepare() = Unit

    override suspend fun show(format: AdFormat): AdShowOutcome =
        AdShowOutcome(AdShowResult.NotShown, errorKind = "ad_network_not_wired")

    override fun isReady(format: AdFormat): Boolean = false

    override fun preload(format: AdFormat) = Unit
}
