@file:OptIn(ExperimentalObjCName::class)

package com.dangerfield.drop2048.libraries.ads

import com.dangerfield.drop2048.libraries.core.BuildInfo
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Every AdMob identifier the app knows, in one file, deliberately.
 *
 * The ids below are **Google's published test units**. They are safe to ship in a
 * debug build, they always fill, and they are the only correct thing to develop
 * against — requesting a *live* unit from a development build is what gets an
 * AdMob account suspended for invalid traffic.
 *
 * Nothing here is in remote config. An ad unit id is a store operation, not a
 * live-ops number, and a config outage that emptied it would take the ads down
 * with it. `ads.enabled` is the kill switch; this is inventory.
 *
 * ## Going live
 *
 * Nothing to flip. [useTestUnits] reads the release channel, so a `store` build
 * requests the `Live` blocks and every other build requests Google's samples.
 * Filling both `Live` blocks is the only manual step, and it is one edit in one
 * file on purpose: the failure this guards against is a half-migrated app with
 * one real unit and one test unit still in it.
 *
 * The app id is *not* here. It goes in `AndroidManifest.xml`
 * (`com.google.android.gms.ads.APPLICATION_ID`) and `Info.plist`
 * (`GADApplicationIdentifier`), because both SDKs read it before any Kotlin
 * runs, which is also why those two carry the real id unconditionally: a
 * manifest value cannot branch on a runtime channel. A real app id paired with
 * test units is a supported combination and is what every non-store build now
 * ships.
 */
@ObjCName("AdUnits", exact = true)
object AdUnits {

    /**
     * Whether to request Google's sample units instead of ours.
     *
     * **Derived from the release channel, not hand-flipped, and not from
     * `isDebug`.** `isDebug` is false for a TestFlight build and false for a
     * Play internal-track build, so keying off it would have every tester
     * requesting live inventory from a build nobody is going to install from a
     * store. That is invalid traffic, and invalid traffic is what gets an AdMob
     * account suspended. `dev` and `beta` therefore stay on test units and only
     * `store` goes live.
     *
     * `releaseChannel` is the artifact's own label, set by
     * `RELEASE_CHANNEL_OVERRIDE` in `release.yml` and defaulted to `dev` in
     * `versions.properties`. It says which pipeline built this binary, which is
     * exactly the question here. Note what it is not: a promise about who is
     * holding the phone. A `store` build handed to a tester before submission
     * does request live units, and should, because that is the binary that
     * ships.
     *
     * When this is false, `Live` must be complete on **both** platforms or the
     * placement falls back to its test unit rather than silently requesting an
     * empty string. See [pick].
     */
    val useTestUnits: Boolean
        get() = BuildInfo.releaseChannel != StoreChannel

    /**
     * The one channel that serves real ads. Matches `RELEASE_CHANNEL_OVERRIDE`
     * in `.github/workflows/release.yml`; `beta.yml` sets `beta` and gets test
     * units.
     */
    private const val StoreChannel = "store"

    /** https://developers.google.com/admob/android/test-ads — reserved sample units. */
    object AndroidTest {
        const val rewarded = "ca-app-pub-3940256099942544/5224354917"
        const val interstitial = "ca-app-pub-3940256099942544/1033173712"
        const val banner = "ca-app-pub-3940256099942544/6300978111"

        /** For the manifest, not for a request. */
        const val applicationId = "ca-app-pub-3940256099942544~3347511713"
    }

    /** https://developers.google.com/admob/ios/test-ads — reserved sample units. */
    object IosTest {
        const val rewarded = "ca-app-pub-3940256099942544/1712485313"
        const val interstitial = "ca-app-pub-3940256099942544/4411468910"
        const val banner = "ca-app-pub-3940256099942544/2934735716"

        /** For `Info.plist`'s `GADApplicationIdentifier`, not for a request. */
        const val applicationId = "ca-app-pub-3940256099942544~1458002511"
    }

    /**
     * Real Android units, from the AdMob app "Doublestack (Android)" created
     * 2026-09-21. In use on `store` builds only, per [useTestUnits].
     *
     * These are not secrets. An AdMob app id and an ad unit id ship inside every
     * binary on both stores and can be read out of any APK, so they belong in git
     * rather than in a CI secret.
     */
    object AndroidLive {
        const val rewarded = "ca-app-pub-7008637445039253/9732460258"
        const val interstitial = "ca-app-pub-7008637445039253/8064155369"
        const val banner = "ca-app-pub-7008637445039253/5042495499"

        /** For the manifest, not for a request. Already set in `AndroidManifest.xml`. */
        const val applicationId = "ca-app-pub-7008637445039253~5728891206"
    }

    /**
     * Real iOS units, from the AdMob app "Doublestack (iOS)" created 2026-09-21.
     *
     * Live since 2026-09-21: `GoogleMobileAds` is an SPM dependency of the
     * iosApp target, `IOSAdNetwork` requests these, and [applicationId] is in
     * `Info.plist` under `GADApplicationIdentifier`.
     */
    object IosLive {
        const val rewarded = "ca-app-pub-7008637445039253/8864347123"
        const val interstitial = "ca-app-pub-7008637445039253/4121040929"
        const val banner = "ca-app-pub-7008637445039253/1498747010"

        /** For `Info.plist`'s `GADApplicationIdentifier`. Already set there. */
        const val applicationId = "ca-app-pub-7008637445039253~9568808728"
    }

    /**
     * The unit to request on Android for [format]. Falls back to the test unit
     * when a live id is missing, because a blank unit id is an SDK error, and an
     * SDK error on the rewarded path is one the app pays the player for — so a
     * typo in a live id would quietly hand out free continues.
     */
    fun android(format: AdFormat): String = pick(
        test = when (format) {
            AdFormat.Rewarded -> AndroidTest.rewarded
            AdFormat.Interstitial -> AndroidTest.interstitial
            AdFormat.Banner -> AndroidTest.banner
        },
        live = when (format) {
            AdFormat.Rewarded -> AndroidLive.rewarded
            AdFormat.Interstitial -> AndroidLive.interstitial
            AdFormat.Banner -> AndroidLive.banner
        },
    )

    /** The unit to request on iOS for [format]. See [android]. */
    fun ios(format: AdFormat): String = pick(
        test = when (format) {
            AdFormat.Rewarded -> IosTest.rewarded
            AdFormat.Interstitial -> IosTest.interstitial
            AdFormat.Banner -> IosTest.banner
        },
        live = when (format) {
            AdFormat.Rewarded -> IosLive.rewarded
            AdFormat.Interstitial -> IosLive.interstitial
            AdFormat.Banner -> IosLive.banner
        },
    )

    private fun pick(test: String, live: String): String =
        if (useTestUnits || live.isBlank()) test else live
}
