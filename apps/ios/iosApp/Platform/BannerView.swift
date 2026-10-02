//
//  BannerView.swift
//  iosApp
//
//  The iOS banner. Until 2026-09-28 iOS had none at all: `NoBannerSurface` was
//  bound there and drew nothing, so the format the shared code calls "the
//  most-seen advertising the app has" was absent on one of two platforms with
//  no error and nothing in telemetry.
//
//  This file only builds a view and reports what happened to it. Every policy
//  question above it — whether a banner is allowed, whether Pro removes it,
//  whether the arrow row already owns the strip — is answered in shared Kotlin.
//  The zero-height contract is enforced by `IosBannerSurface`, which only puts
//  the view in the layout once a fill arrives with a height.
//
//  The unit id comes from `AdUnits.kt`, the only file allowed to carry one, so
//  a QA build cannot request live inventory. Do not hardcode one here.
//
import ComposeApp
import Foundation
import UIKit

#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

class IOSBannerViewFactory: NSObject, IosBannerViewFactory {

    /// Keeps each banner's delegate alive for as long as the banner is.
    ///
    /// `BannerView.delegate` is weak, which is right for a view controller that
    /// owns its banner and wrong here, because nothing else holds it. Without
    /// this map the delegate is released at the end of `makeBanner` and no
    /// callback ever arrives: the ad loads, Kotlin is never told, and the strip
    /// stays collapsed looking exactly like a no-fill.
    private var delegates: [ObjectIdentifier: AnyObject] = [:]

    func makeBanner(widthPoints: Double, listener: IosBannerListener) -> UIView? {
        #if canImport(GoogleMobileAds)
        let size = currentOrientationAnchoredAdaptiveBanner(width: CGFloat(widthPoints))
        let banner = BannerView(adSize: size)
        banner.adUnitID = AdUnits.shared.ios(format: .banner)

        // The controller that *contains* the banner, not the top of the
        // presentation stack. A banner uses this to present its landing page on
        // a tap, and a sheet that happens to be up may be gone by then.
        banner.rootViewController = Self.hostViewController()

        let fillListener = BannerFillListener(
            onFilled: { height in listener.onFilled(heightPoints: Double(height)) },
            onFailed: { reason in listener.onFailed(reason: reason) }
        )
        delegates[ObjectIdentifier(banner)] = fillListener
        banner.delegate = fillListener

        banner.load(Request())
        return banner
        #else
        // The SDK is not in the binary. Reported rather than returned silently,
        // because this is a build problem and every other failure is a load
        // that did not fill.
        listener.onFailed(reason: "sdk_not_linked")
        return nil
        #endif
    }

    func disposeBanner(view: UIView) {
        delegates[ObjectIdentifier(view)] = nil
        view.removeFromSuperview()
    }

    private static func hostViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive }
            ?? scenes.first { $0.keyWindow != nil }
        return scene?.keyWindow?.rootViewController
    }
}

#if canImport(GoogleMobileAds)
/// Turns the SDK's delegate callbacks into the two Kotlin wants.
///
/// The reason travels with the failure because "no banner appeared" is the same
/// picture whether consent was refused, the unit id is wrong, or the network
/// had no inventory. It is logged as `ads.banner_failed`, which is the only way
/// to tell those apart once the app is on someone else's phone.
private class BannerFillListener: NSObject, BannerViewDelegate {

    private let onFilled: (CGFloat) -> Void
    private let onFailed: (String) -> Void

    init(onFilled: @escaping (CGFloat) -> Void, onFailed: @escaping (String) -> Void) {
        self.onFilled = onFilled
        self.onFailed = onFailed
    }

    func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        // The view's own frame after a load, falling back to the requested ad
        // size. An adaptive banner is told a width and chooses its height.
        let height = bannerView.frame.height > 0
            ? bannerView.frame.height
            : bannerView.adSize.size.height
        onFilled(height)
    }

    func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
        // The SDK's numeric code is the stable part. The message is localised
        // and changes between versions, so it is not what telemetry groups on.
        onFailed("load_failed_\((error as NSError).code)")
    }
}
#endif
