@file:OptIn(ExperimentalObjCName::class)

package com.dangerfield.drop2048.libraries.ads

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import platform.UIKit.UIView

/**
 * The Swift half of the iOS banner, and the narrowest seam that can express one.
 *
 * Implemented by `IOSBannerViewFactory` in
 * `apps/ios/iosApp/Platform/BannerView.swift` and handed to the graph by
 * `IosAppComponent`, for the same reason [AdNetwork] is: GoogleMobileAds ships
 * as an iOS framework, and reaching it through cinterop would mean maintaining a
 * Kotlin binding for an SDK Google changes on their own schedule.
 *
 * Until 2026-09-28 there was no iOS banner at all. `NoBannerSurface` was bound
 * there and drew nothing, so the format the code itself calls "the most-seen
 * advertising the app has" was absent on one of two platforms with no error
 * anywhere.
 *
 * ### Why the factory returns the view rather than drawing it
 *
 * The zero-height contract in [BannerSurface] is the whole design: a strip that
 * reserved space for an ad that never arrives is the dead space it exists to
 * avoid. Kotlin can only honour that if it decides *whether* to put the view in
 * the layout, which means Swift hands back a view and says when it filled, and
 * Kotlin does the rest.
 */
@ObjCName("IosBannerViewFactory", exact = true)
interface IosBannerViewFactory {

    /**
     * Builds an adaptive banner for [widthPoints] and starts loading it.
     *
     * Call only after [AdNetwork.prepare] has returned: UMP consent, then ATT,
     * then `MobileAds.start()`, then the first request. That order is a policy
     * requirement rather than a preference, and getting it wrong is a rejected
     * release rather than a crash.
     *
     * @return the view, or null when the SDK is not present. Null degrades to
     *   the same nothing a no-fill already draws.
     */
    fun makeBanner(widthPoints: Double, listener: IosBannerListener): UIView?

    /**
     * Releases [view]. Kotlin owns the lifetime because Kotlin decides when the
     * composable leaves, and a banner that outlives its composition keeps
     * refreshing against a screen nobody is looking at.
     */
    fun disposeBanner(view: UIView)
}

/**
 * What Swift reports back about one banner.
 *
 * An interface rather than a `(Boolean) -> Unit` so that a failure can carry a
 * reason. The first version reported nothing at all, and a banner that does not
 * appear is the same picture whether the SDK is missing, consent was refused,
 * the unit id is wrong, or the network simply had no inventory. "Nothing
 * happened" is not something a tester can act on, and it is not something a
 * dashboard can alert on either.
 */
@ObjCName("IosBannerListener", exact = true)
interface IosBannerListener {

    /**
     * An ad is loaded, and the view is [heightPoints] tall.
     *
     * The height travels with the fill so the slot can be sized explicitly. See
     * `IosBannerSurface` for why that is deliberate rather than defensive.
     *
     * May be called more than once: the SDK's own refresh replaces the creative
     * inside a view that is already the right size.
     */
    fun onFilled(heightPoints: Double)

    /** No ad. [reason] is logged as `ads.banner_failed`, so keep it short and stable. */
    fun onFailed(reason: String)
}
