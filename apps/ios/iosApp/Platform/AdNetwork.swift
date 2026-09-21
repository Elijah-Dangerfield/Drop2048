//
//  AdNetwork.swift
//  iosApp
//
//  AdMob on iOS, plus the two consent prompts that must run in front of it.
//
//  ORDER IS A POLICY REQUIREMENT, NOT A PREFERENCE:
//    1. UMP consent (EEA / UK)
//    2. App Tracking Transparency
//    3. MobileAds.start()
//    4. the first ad request
//
//  Google's own guidance is that ATT comes after the UMP form, because UMP can
//  present the ATT pre-prompt. Apple rejects builds that request the IDFA
//  before the ATT dialog. Neither failure shows up as a crash; both show up as
//  a rejected release, which is why the order lives in one place.
//
//  Everything policy-shaped above this — frequency gates, the new-user grace,
//  the offline grace, and the rule that a failed ad still pays the player —
//  is in `RealAdGate` in shared Kotlin. This file only loads and shows.
//
//  The `#if canImport(GoogleMobileAds)` guards are kept because the whole point
//  of them is that a missing SDK degrades to "every ad fails", which the shared
//  Kotlin already pays the player for. They are no longer the live path: the
//  `GoogleMobileAds` product (which carries `UserMessagingPlatform` with it) is
//  a package dependency of the iosApp target, so both branches are real and the
//  `#else` is the thing that should never fire.
//
//  The app id lives in `Info.plist` under `GADApplicationIdentifier` and the
//  unit ids in `AdUnits.kt`. Both are Google's published test values today;
//  they flip together when `AdUnits.useTestUnits` does.
//
//  `AdUnits` resolves here only because `:libraries:ads` is exported to the
//  framework: `ApplicationConventionPlugin` has the `export(...)` and
//  `apps/compose/build.gradle.kts` makes it an `api` dependency, because
//  Kotlin/Native refuses to export a dependency that is not one. Without both,
//  `AdUnits.shared.ios(format:)` does not compile — nothing in Kotlin reads
//  `AdUnits` on iOS, so it would not otherwise be reachable from the
//  framework's API.
//
//  Do not answer a resolution failure by hardcoding the unit id here.
//  `AdUnits.kt` is deliberately the only file that carries one, and the failure
//  it guards against is exactly a half-migrated app with a real id in Kotlin
//  and a test id in Swift.
//
import ComposeApp
import Foundation
import UIKit

#if canImport(AppTrackingTransparency)
import AppTrackingTransparency
#endif

#if canImport(GoogleMobileAds)
import GoogleMobileAds
#endif

#if canImport(UserMessagingPlatform)
import UserMessagingPlatform
#endif

class IOSAdNetwork: NSObject, AdNetwork {

    private var initialised = false

    // MARK: - AdNetwork

    func __prepare() async throws {
        guard !initialised else { return }
        #if canImport(GoogleMobileAds)
        await requestConsent()
        await requestTrackingAuthorization()
        guard canRequestAds() else { return }
        // The app is general-audience, matching Android's
        // `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE`. Set before `start()`, as on
        // Android, so no request can go out ahead of the configuration.
        //
        // `.unspecified` is this SDK's current spelling of that: the
        // age-treatment enum replaced the child-directed and
        // under-age-of-consent flags, and it has no "definitely not restricted"
        // case — declaring neither child nor teen *is* the general-audience
        // answer. It is also the default, so this line is a statement of the
        // decision rather than a change of behaviour.
        MobileAds.shared.requestConfiguration.ageRestrictedTreatment = .unspecified
        _ = await MobileAds.shared.start()
        initialised = true
        #endif
    }

    // `AdFormat` is qualified because GoogleMobileAds exports a type of that
    // name too (`GADAdFormat`), and an unqualified mention is ambiguous once
    // both modules are imported. The Kotlin one is the contract being
    // implemented here.
    func __show(format: ComposeApp.AdFormat) async throws -> AdShowOutcome {
        // A banner is a view in the game's own layout, served by
        // `BannerSurface`, so there is nothing here to show and nothing to
        // suspend on. Answered before `prepare()` so it costs no consent
        // round trip. Mirrors `AdMobAdNetwork`'s `banner_is_not_shown`.
        if format == .banner {
            return AdShowOutcome(result: .notShown, errorKind: "banner_is_not_shown")
        }
        try await __prepare()
        #if canImport(GoogleMobileAds)
        guard initialised else {
            return AdShowOutcome(result: .notShown, errorKind: "sdk_not_ready")
        }
        guard let root = await Self.rootViewController() else {
            return AdShowOutcome(result: .notShown, errorKind: "no_root_view_controller")
        }
        switch format {
        case .rewarded:
            return await showRewarded(from: root)
        case .interstitial:
            return await showInterstitial(from: root)
        default:
            return AdShowOutcome(result: .notShown, errorKind: "banner_is_not_shown")
        }
        #else
        return AdShowOutcome(result: .notShown, errorKind: "google_mobile_ads_not_linked")
        #endif
    }

    /// Android answers `false` for every banner here too: a banner's readiness
    /// is the `AdView`'s own business, not this seam's.
    func isReady(format: ComposeApp.AdFormat) -> Bool {
        #if canImport(GoogleMobileAds)
        switch format {
        case .rewarded: return cachedRewarded != nil
        case .interstitial: return cachedInterstitial != nil
        default: return false
        }
        #else
        return false
        #endif
    }

    func preload(format: ComposeApp.AdFormat) {
        #if canImport(GoogleMobileAds)
        guard format != .banner else { return }
        Task { [weak self] in
            try? await self?.__prepare()
            guard self?.initialised == true else { return }
            switch format {
            case .rewarded:
                guard self?.cachedRewarded == nil else { return }
                self?.cachedRewarded = try? await RewardedAd.load(
                    with: AdUnits.shared.ios(format: .rewarded), request: Request())
            case .interstitial:
                guard self?.cachedInterstitial == nil else { return }
                self?.cachedInterstitial = try? await InterstitialAd.load(
                    with: AdUnits.shared.ios(format: .interstitial), request: Request())
            default:
                return
            }
        }
        #endif
    }

    #if canImport(GoogleMobileAds)

    private var cachedRewarded: RewardedAd?
    private var cachedInterstitial: InterstitialAd?

    /// No reward and no reward handler: closing is the ordinary ending, and
    /// `.dismissed` is how the shared gate hears "it was on screen".
    private func showInterstitial(from root: UIViewController) async -> AdShowOutcome {
        let ad: InterstitialAd
        do {
            if let cached = cachedInterstitial {
                ad = cached
                cachedInterstitial = nil
            } else {
                ad = try await InterstitialAd.load(
                    with: AdUnits.shared.ios(format: .interstitial), request: Request())
            }
        } catch {
            return Self.loadFailure(error)
        }

        let delegate = DismissalDelegate()
        ad.fullScreenContentDelegate = delegate
        let dismissal = await withCheckedContinuation { (continuation: CheckedContinuation<Dismissal, Never>) in
            delegate.onFinished = { continuation.resume(returning: $0) }
            Task { @MainActor in
                ad.present(from: root)
            }
        }

        switch dismissal {
        case .failed(let kind): return AdShowOutcome(result: .failed, errorKind: kind)
        case .closed: return AdShowOutcome(result: .dismissed, errorKind: nil)
        }
    }

    private func showRewarded(from root: UIViewController) async -> AdShowOutcome {
        let ad: RewardedAd
        do {
            if let cached = cachedRewarded {
                ad = cached
                cachedRewarded = nil
            } else {
                ad = try await RewardedAd.load(
                    with: AdUnits.shared.ios(format: .rewarded), request: Request())
            }
        } catch {
            return Self.loadFailure(error)
        }

        let delegate = DismissalDelegate()
        ad.fullScreenContentDelegate = delegate
        var earned = false
        let dismissal = await withCheckedContinuation { (continuation: CheckedContinuation<Dismissal, Never>) in
            delegate.onFinished = { continuation.resume(returning: $0) }
            Task { @MainActor in
                ad.present(from: root) { earned = true }
            }
        }
        preload(format: .rewarded)

        switch dismissal {
        case .failed(let kind): return AdShowOutcome(result: .failed, errorKind: kind)
        case .closed: return AdShowOutcome(result: earned ? .rewarded : .dismissed, errorKind: nil)
        }
    }

    /// Main-actor because the second half presents a view controller. Both
    /// failures are swallowed on purpose: a consent update that errors leaves
    /// `canRequestAds` false, which `prepare` already reads as "no ads today".
    @MainActor
    private func requestConsent() async {
        try? await ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters())
        guard let root = Self.rootViewController() else { return }
        try? await ConsentForm.loadAndPresentIfRequired(from: root)
    }

    /// `canRequestAds` is the gate, not "did the form show". UMP answers true
    /// for a user outside the EEA who was never shown anything, and false for
    /// one who declined; reading the form's presence would block ads for most
    /// of the world.
    private func canRequestAds() -> Bool {
        ConsentInformation.shared.canRequestAds
    }

    private static func loadFailure(_ error: Error) -> AdShowOutcome {
        let code = (error as NSError).code
        let kind = "load_\(code)"
        // Qualified: UserMessagingPlatform exports a `RequestError` of its own.
        switch GoogleMobileAds.RequestError.Code(rawValue: code) {
        // A timed-out load is Android's `load_timeout`, and it says the same
        // thing about the player's situation: nothing to watch right now.
        case .noFill, .timeout: return AdShowOutcome(result: .noFill, errorKind: kind)
        case .networkError: return AdShowOutcome(result: .offline, errorKind: kind)
        default: return AdShowOutcome(result: .failed, errorKind: kind)
        }
    }

    private enum Dismissal {
        case closed
        case failed(String)
    }

    private final class DismissalDelegate: NSObject, FullScreenContentDelegate {
        var onFinished: ((Dismissal) -> Void)?

        func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
            finish(.closed)
        }

        func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
            finish(.failed("show_\((error as NSError).code)"))
        }

        private func finish(_ dismissal: Dismissal) {
            let handler = onFinished
            onFinished = nil
            handler?(dismissal)
        }
    }

    #endif

    /// ATT goes in front of the first ad request, and it is asked at a moment
    /// where the value is legible — which is why `prepare()` is called lazily by
    /// the first ad gate rather than at launch.
    private func requestTrackingAuthorization() async {
        #if canImport(AppTrackingTransparency)
        guard #available(iOS 14.5, *) else { return }
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return }
        _ = await ATTrackingManager.requestTrackingAuthorization()
        #endif
    }

    @MainActor
    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }?
            .keyWindow?
            .rootViewController
    }
}
