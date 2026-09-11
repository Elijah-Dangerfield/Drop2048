package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ads.RewardOutcome
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.gameconfig.AdsEnabled
import com.dangerfield.drop2048.libraries.progress.daily.RewardOutcome as DailyRewardOutcome
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The rewarded gate, and the one rule it exists to enforce: **only a deliberate
 * dismissal withholds the reward.**
 *
 * Most of these are the same assertion from different failures, which is the
 * point. There is exactly one input that may produce [RewardOutcome.Dismissed],
 * and a refactor that collapsed "didn't watch" into a boolean would be caught by
 * every one of the others.
 */
@OptIn(ExperimentalTime::class)
class RealAdGateTest : CoroutineTest() {

    @Test
    fun `a watched ad rewards`() = runUnitTest {
        val scenario = scenario(AdShowResult.Rewarded)

        assertEquals(RewardOutcome.Rewarded, scenario.gate.showRewarded(AdPlacement.ContinueRun))
        assertEquals(listOf(AdFormat.Rewarded), scenario.network.shown)
    }

    @Test
    fun `closing the ad early is the only thing that withholds`() = runUnitTest {
        val scenario = scenario(AdShowResult.Dismissed)

        assertEquals(RewardOutcome.Dismissed, scenario.gate.showRewarded(AdPlacement.ContinueRun))
    }

    @Test
    fun `no fill does not withhold`() = runUnitTest {
        val scenario = scenario(AdShowResult.NoFill)

        val outcome = scenario.gate.showRewarded(AdPlacement.ContinueRun)
        assertNotEquals(RewardOutcome.Dismissed, outcome)
    }

    @Test
    fun `an offline device does not withhold`() = runUnitTest {
        val scenario = scenario(AdShowResult.Offline)

        assertEquals(RewardOutcome.Offline, scenario.gate.showRewarded(AdPlacement.ContinueRun))
    }

    /**
     * The iOS case, named explicitly. A network with nothing wired answers
     * [AdShowResult.NotShown] rather than pretending — Sodogku shipped a stub
     * that answered `Rewarded` and paid out for free, which every log read as a
     * watched ad. `NotShown` becoming [RewardOutcome.NoFill] means the *caller*
     * decides what an unserved ad is worth, and the two callers decide
     * differently: a continue is granted, a Daily retry is not spent.
     */
    @Test
    fun `an unwired network reports nothing shown rather than a reward`() = runUnitTest {
        val scenario = scenario(AdShowResult.NotShown)

        assertEquals(RewardOutcome.NoFill, scenario.gate.showRewarded(AdPlacement.ContinueRun))
    }

    @Test
    fun `an sdk that throws does not withhold and does not escape`() = runUnitTest {
        val scenario = scenario(AdShowResult.Rewarded)
        scenario.network.throwOnShow = true

        val outcome = scenario.gate.showRewarded(AdPlacement.ContinueRun)
        assertIs<RewardOutcome.Failed>(outcome)
    }

    /**
     * With advertising switched off from the console there is no ad to show and
     * the player still asked for one, so the reward is free and the network is
     * never touched.
     */
    @Test
    fun `the kill switch grants without an ad`() = runUnitTest {
        val scenario = scenario(AdShowResult.Rewarded, config = mapOf("ads.enabled" to false))

        assertEquals(RewardOutcome.Rewarded, scenario.gate.showRewarded(AdPlacement.ContinueRun))
        assertTrue(scenario.network.shown.isEmpty())
    }

    /**
     * SPEC 12 keeps rewarded video available to Pro holders, because removing it
     * takes something away. There is no `isPro` branch in the gate at all, and
     * this is the test that would go red if one were added.
     */
    @Test
    fun `pro still gets rewarded ads`() = runUnitTest {
        val scenario = scenario(AdShowResult.Rewarded, pro = true)

        assertEquals(RewardOutcome.Rewarded, scenario.gate.showRewarded(AdPlacement.ContinueRun))
        assertEquals(listOf(AdFormat.Rewarded), scenario.network.shown)
    }

    /** Feeds SPEC 12's 45-second rule, and it is stamped for a dismissal too. */
    @Test
    fun `every rewarded attempt stamps the clock`() = runUnitTest {
        val scenario = scenario(AdShowResult.Dismissed)

        scenario.gate.showRewarded(AdPlacement.ContinueRun)

        assertEquals(Now, scenario.cache.get().lastRewardedAtMs)
    }

    /**
     * The Daily's asymmetry, from the other side. An unserved ad grants a
     * continue and does **not** grant a retry — because on the continue an
     * ad failure would take the player's board, and on the retry it would take
     * the day's one cap, which is worse and cannot be bought back.
     */
    @Test
    fun `an unserved daily retry is unavailable rather than dismissed`() = runUnitTest {
        val scenario = scenario(AdShowResult.NoFill)
        val retry = RewardedDailyRetryAd(scenario.gate)

        assertEquals(DailyRewardOutcome.Unavailable, retry.show())
    }

    @Test
    fun `a watched daily retry is earned`() = runUnitTest {
        val scenario = scenario(AdShowResult.Rewarded)
        val retry = RewardedDailyRetryAd(scenario.gate)

        assertEquals(DailyRewardOutcome.Earned, retry.show())
    }

    @Test
    fun `a dismissed daily retry is the only one that costs the player`() = runUnitTest {
        val scenario = scenario(AdShowResult.Dismissed)
        val retry = RewardedDailyRetryAd(scenario.gate)

        assertEquals(DailyRewardOutcome.Dismissed, retry.show())
    }

    /**
     * [pro] is accepted and deliberately never used. [RealAdGate] has no
     * entitlement in its constructor at all — SPEC 12's "rewarded video stays
     * available to Pro holders" expressed as a missing dependency rather than as
     * a branch somebody could add back — and a parameter that visibly goes
     * nowhere is what makes `pro still gets rewarded ads` read as the assertion
     * it is rather than as a test that forgot to set something up.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun TestScope.scenario(
        result: AdShowResult,
        pro: Boolean = false,
        config: Map<String, Any?> = emptyMap(),
    ): Scenario {
        val network = FakeAdNetwork(rewardedResult = result)
        val cache = FakeAdStateCache()
        val gate = RealAdGate(
            network = network,
            adState = cache,
            rewardedClock = RewardedClock(),
            appScope = AppCoroutineScope(dispatchers),
            clock = MovableClock(Instant.fromEpochMilliseconds(Now)),
            adsEnabled = AdsEnabled(TestAdConfigMap(config)),
        )
        runCurrent()
        return Scenario(gate, network, cache)
    }

    private class Scenario(
        val gate: RealAdGate,
        val network: FakeAdNetwork,
        val cache: FakeAdStateCache,
    )

    private companion object {
        const val Now = 1_760_000_000_000L
    }
}
