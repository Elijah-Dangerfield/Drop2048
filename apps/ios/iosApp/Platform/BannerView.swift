//
//  BannerView.swift
//  iosApp
//
//  The iOS banner, which until 2026-09-28 did not exist.
//
//  `NoBannerSurface` was bound on iOS and drew nothing, so the format the
//  shared code itself calls "the most-seen advertising the app has" was absent
//  on one of two platforms, with no error and nothing in telemetry. A tester who
//  allowed tracking and waited for a banner waited forever, and the only way to
//  find out was to read `BannerAds.kt` and notice the KDoc said so.
//
//  This file only builds a view and says when it filled. Every policy question
//  above it — whether a banner is allowed at all, whether Pro removes it,
//  whether the arrow row already owns the strip — is answered in shared Kotlin
//  by `RealBannerAds` and `GameScreen`. The zero-height contract is enforced by
//  `IosBannerSurface`, which only puts the view in the layout after `onFilled`.
//
//  The unit id comes from `AdUnits.kt`, the single file in the codebase allowed
//  to carry one, so a QA build cannot request live inventory. Do not answer a
//  resolution failure by hardcoding one here.
//
//  `#if canImport(GoogleMobileAds)` is kept for the same reason `AdNetwork.swift`
//  keeps it: a missing SDK degrades to "no banner ever", which is a state the
//  shared Kotlin already draws correctly. The `#else` should never fire.
//
import ComposeApp
import Foundation
import UIKit

#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

class IOSBannerViewFactory: NSObject, IosBannerViewFactory {

    /// Kept so the delegate is not deallocated while its banner is alive.
    ///
    /// `BannerView.delegate` is weak, which is correct for a view controller
    /// that owns its banner and wrong here: nothing else holds this delegate,
    /// so without the map it would be released at the end of `makeBanner` and
    /// the fill callback would silently never arrive. The banner would load,
    /// and the strip would stay collapsed forever.
    private var delegates: [ObjectIdentifier: AnyObject] = [:]

    func makeBanner(
        widthPoints: Double,
        onFilled: @escaping (KotlinBoolean) -> Void
    ) -> UIView? {
        #if canImport(GoogleMobileAds)
        let size = currentOrientationAnchoredAdaptiveBanner(width: CGFloat(widthPoints))
        let banner = BannerView(adSize: size)
        banner.adUnitID = AdUnits.shared.ios(format: .banner)

        // The view controller that *contains* the banner, not the top of the
        // presentation stack. A banner uses this to present its landing page on
        // a tap; handing it a sheet that is currently up would present from a
        // controller that may be gone by the time anyone taps.
        banner.rootViewController = Self.hostViewController()

        let listener = BannerFillListener { [weak self] filled, view in
            onFilled(KotlinBoolean(bool: filled))
            if !filled, let self { self.delegates[ObjectIdentifier(view)] = nil }
        }
        delegates[ObjectIdentifier(banner)] = listener
        banner.delegate = listener

        banner.load(Request())
        return banner
        #else
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
/// Turns the SDK's two delegate callbacks into the one boolean Kotlin wants.
///
/// A failure reports `false` rather than staying silent, because the shared
/// surface uses that to collapse the strip and hand the space back to the
/// board. Silence would leave a gap sized for an ad that is not coming.
private class BannerFillListener: NSObject, BannerViewDelegate {

    private let report: (Bool, UIView) -> Void

    init(report: @escaping (Bool, UIView) -> Void) {
        self.report = report
    }

    func bannerViewDidReceiveAd(_ bannerView: BannerView) {
        report(true, bannerView)
    }

    func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
        report(false, bannerView)
    }
}
#endif
