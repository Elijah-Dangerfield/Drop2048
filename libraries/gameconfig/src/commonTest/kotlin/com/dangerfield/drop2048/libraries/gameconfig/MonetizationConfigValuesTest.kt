package com.dangerfield.drop2048.libraries.gameconfig

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ad, Pro and kill-switch keys have no consumer until C10, so what is worth
 * pinning now is the seam itself: the paths resolve, the compiled-in defaults are
 * SPEC 12's numbers, and a server value reaches them.
 *
 * Without this, a wired-but-unread key is indistinguishable from a typo in a
 * path string until the chunk that needs it.
 */
class MonetizationConfigValuesTest {

    @Test
    fun `with the server unreachable every gate is SPEC 12's number`() {
        val map = TestAppConfigMap.Empty

        assertTrue(AdsEnabled(map)())
        assertEquals(4, InterstitialMinSessionRuns(map)())
        assertEquals(180, InterstitialCooldownSeconds(map)())
        assertEquals(45, InterstitialRewardedGapSeconds(map)())
        assertEquals(3, InterstitialSuppressDaysSinceInstall(map)())
        assertEquals(2, RewardedContinuesPerRun(map)())
        assertTrue(ProUpsellEnabled(map)())
    }

    @Test
    fun `the kill switch defaults on so an unreachable server is not a smaller game`() {
        assertTrue(LeaderboardsEnabled(TestAppConfigMap.Empty)())
    }

    @Test
    fun `a server value reaches every gate`() {
        val map = TestAppConfigMap(
            mapOf(
                "ads.enabled" to false,
                "ads.interstitial.minSessionRuns" to 6,
                "ads.interstitial.cooldownSeconds" to 300,
                "ads.interstitial.rewardedGapSeconds" to 60,
                "ads.interstitial.suppressDaysSinceInstall" to 7,
                "ads.rewarded.continuesPerRun" to 1,
                "pro.upsell.enabled" to false,
                "feature.leaderboards" to false,
            )
        )

        assertFalse(AdsEnabled(map)())
        assertEquals(6, InterstitialMinSessionRuns(map)())
        assertEquals(300, InterstitialCooldownSeconds(map)())
        assertEquals(60, InterstitialRewardedGapSeconds(map)())
        assertEquals(7, InterstitialSuppressDaysSinceInstall(map)())
        assertEquals(1, RewardedContinuesPerRun(map)())
        assertFalse(ProUpsellEnabled(map)())
        assertFalse(LeaderboardsEnabled(map)())
    }

    @Test
    fun `a malformed number falls back to the compiled-in gate`() {
        val map = TestAppConfigMap(
            mapOf(
                "ads.interstitial.cooldownSeconds" to "five minutes",
                "ads.rewarded.continuesPerRun" to mapOf("cap" to 9),
            )
        )

        assertEquals(180, InterstitialCooldownSeconds(map)())
        assertEquals(2, RewardedContinuesPerRun(map)())
    }
}
