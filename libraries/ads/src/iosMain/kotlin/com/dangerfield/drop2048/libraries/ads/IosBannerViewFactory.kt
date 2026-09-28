package com.dangerfield.drop2048.libraries.ads

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
 * there and drew nothing, so the format that the code itself calls "the
 * most-seen advertising the app has" was absent on one of two platforms with no
 * error anywhere, and a tester who allowed tracking still saw nothing.
 *
 * ### Why the factory returns the view rather than drawing it
 *
 * The zero-height contract in [BannerSurface] is the whole design: a strip that
 * reserved space for an ad that never arrives is the dead space it exists to
 * avoid. Kotlin can only honour that if it decides *whether* to put the view in
 * the layout, which means Swift hands back a view and says when it filled, and
 * Kotlin does the rest.
 */
interface IosBannerViewFactory {

    /**
     * Builds an adaptive banner for [widthPoints] and starts loading it.
     *
     * Call only after [AdNetwork.prepare] has returned: UMP consent, then ATT,
     * then `MobileAds.start()`, then the first request. That order is a policy
     * requirement rather than a preference, and getting it wrong is a rejected
     * release rather than a crash.
     *
     * @param onFilled true when an ad is on screen, false when the load failed
     *   or the creative went away. Called on the main thread. It may be called
     *   more than once: the SDK's own refresh replaces the creative inside a
     *   view that is already the right size.
     * @return the view, or null when the SDK is not present. Null degrades to
     *   the same nothing a no-fill already draws.
     */
    fun makeBanner(widthPoints: Double, onFilled: (Boolean) -> Unit): UIView?

    /**
     * Releases [view]. Kotlin owns the lifetime because Kotlin decides when the
     * composable leaves, and a banner that outlives its composition keeps
     * refreshing against a screen nobody is looking at.
     */
    fun disposeBanner(view: UIView)
}
