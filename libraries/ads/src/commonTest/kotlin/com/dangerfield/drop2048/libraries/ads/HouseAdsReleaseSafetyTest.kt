package com.dangerfield.drop2048.libraries.ads

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The house network cannot be selected in a release build.
 *
 * ### What this test can and cannot see
 *
 * It cannot see a release build. `BuildInfo.isDebug` is an `expect object`
 * reading `BuildConfig.DEBUG`, and a unit test runs on the debug variant by
 * construction — a test that tried to flip it would be testing a mock of the
 * thing whose honesty is the entire point.
 *
 * What it *can* pin is the release build's answer, because the release build's
 * answer is a **different class**. Anvil resolves `HouseAds` to [NoHouseAds] in
 * any compilation that does not have `:libraries:ads:fake` on its classpath, and
 * `:libraries:ads:fake` is a `debugImplementation` of `:apps:compose` — verified
 * against the generated graph, where `InjectKotlinInjectAndroidAppComponent`
 * provides `HouseAdNetwork` on debug and `NoHouseAds` on release.
 *
 * So the guarantee has three legs and this file is one of them:
 *
 * 1. `:apps:compose:verifyNoHouseAdsInRelease` fails the build if the module
 *    reaches the release runtime classpath at all. iOS has no build-type source
 *    sets to hang that on, so it gets the same leg from Xcode's `CONFIGURATION`
 *    instead, checked by `verifyNoHouseAdsInIosRelease` on the graph and by
 *    `verifyNoHouseAdsInIosSimulatorArm64ReleaseFramework` on the linked binary.
 * 2. This test: the class a release build binds has no network and cannot be
 *    made to have one.
 * 3. `HouseAdNetwork.select` ands with `BuildInfo.isDebug`, which covers a
 *    binary that contains the class when it should not.
 */
class HouseAdsReleaseSafetyTest {

    @Test
    fun theBindingAReleaseBuildGetsHasNoNetwork() {
        assertNull(NoHouseAds().network)
    }

    @Test
    fun selectingHouseAdsDoesNothingWithoutAHouseNetwork() {
        val houseAds = NoHouseAds()

        houseAds.select(useHouseAds = true)

        assertFalse(houseAds.isSelected.value)
    }

    /**
     * The routing itself, which is what every gate actually calls. A release
     * build has no branch here that can reach anything but the platform
     * network.
     */
    @Test
    fun theGatesRouteToThePlatformNetworkWhenThereIsNoHouseNetwork() {
        val houseAds = NoHouseAds()
        val platform = NotWiredAdNetwork()

        houseAds.select(useHouseAds = true)

        assertSame(platform, houseAds.networkOr(platform))
    }

    /**
     * Belt and braces on the one that would be a real incident: a selected
     * house network that could grant a reward without an ad. The dial cannot be
     * turned either.
     */
    @Test
    fun theForcedOutcomeDialDoesNothingWithoutAHouseNetwork() {
        val houseAds = NoHouseAds()

        houseAds.forceOutcome(AdShowResult.Rewarded)

        assertNull(houseAds.forcedOutcome.value)
    }
}
