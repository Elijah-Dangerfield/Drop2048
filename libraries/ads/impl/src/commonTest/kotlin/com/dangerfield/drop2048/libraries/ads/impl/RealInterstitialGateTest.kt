package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.gameconfig.AdsEnabled
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialCooldownSeconds
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialMinSessionRuns
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialRewardedGapSeconds
import com.dangerfield.drop2048.libraries.gameconfig.InterstitialSuppressDaysSinceInstall
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The gate with real config values, a real clock and a real cache behind it.
 *
 * [AdPolicyTest] holds the rules; this holds the wiring, which is where a
 * different set of mistakes live: a timestamp read as "never" because zero is
 * fifty-six years of elapsed time, a counter that never rolls at a session
 * boundary, an impression recorded after the ad instead of before it.
 *
 * Every test starts from a player the gates are happy with — [gate] stamps an
 * install date a fortnight back and counts four finished runs — so a failure
 * means the thing under test, not the baseline.
 */
@OptIn(ExperimentalTime::class)
class RealInterstitialGateTest : CoroutineTest() {

    @Test
    fun `an eligible player sees one`() = runUnitTest {
        val scenario = scenario()
        assertTrue(scenario.gate.showIfReady())
        assertEquals(listOf(AdFormat.Interstitial), scenario.network.shown)
    }

    /**
     * SPEC 12's governing principle, from the outside: the gate refuses on the
     * app's own answer to "is a run alive", not on an argument the caller passed.
     * Nothing reaches the network.
     */
    @Test
    fun `nothing is shown while a run is alive`() = runUnitTest {
        val scenario = scenario()
        scenario.runActivity.runStarted()

        assertFalse(scenario.gate.showIfReady())
        assertTrue(scenario.network.shown.isEmpty())
    }

    @Test
    fun `pro sees nothing`() = runUnitTest {
        val scenario = scenario(pro = true)

        assertFalse(scenario.gate.showIfReady())
        assertTrue(scenario.network.shown.isEmpty())
    }

    /**
     * SPEC 12.3's "skipped silently if not ready". The assertion that matters is
     * the second one: nothing was asked of the network, so there is nothing to
     * wait for and no spinner to draw.
     */
    @Test
    fun `an empty cache shows nothing and asks for nothing`() = runUnitTest {
        val scenario = scenario()
        scenario.network.ready.clear()

        assertFalse(scenario.gate.showIfReady())
        assertTrue(scenario.network.shown.isEmpty())
    }

    @Test
    fun `a second interstitial inside the cooldown is refused`() = runUnitTest {
        val scenario = scenario()
        assertTrue(scenario.gate.showIfReady())

        scenario.clock.advance(179_000)
        assertFalse(scenario.gate.showIfReady())

        scenario.clock.advance(2_000)
        assertTrue(scenario.gate.showIfReady())
    }

    /**
     * The impression is recorded on the way *in*, so an app killed during a
     * thirty-second ad is still inside its cooldown on the next launch. The
     * cache is asserted directly because that is the thing that survives.
     */
    @Test
    fun `the impression is recorded before the ad is shown`() = runUnitTest {
        val scenario = scenario()
        scenario.network.throwOnShow = true

        assertFalse(scenario.gate.showIfReady())
        assertEquals(Now, scenario.cache.get().lastInterstitialAtMs)
    }

    @Test
    fun `a rewarded ad suppresses the next interstitial for the gap`() = runUnitTest {
        val scenario = scenario()
        scenario.gate.noteRewardedShown()
        runCurrent()

        assertFalse(scenario.gate.showIfReady())

        scenario.clock.advance(45_000)
        assertTrue(scenario.gate.showIfReady())
    }

    @Test
    fun `the fourth run of a session is the first that can carry one`() = runUnitTest {
        val scenario = scenario(runsFinished = 0)
        repeat(3) { scenario.gate.noteRunFinished() }

        assertFalse(scenario.gate.showIfReady())

        scenario.gate.noteRunFinished()
        assertTrue(scenario.gate.showIfReady())
    }

    /**
     * The run count is in memory and keyed on the session, so a fifteen-minute
     * absence puts the player back behind the fourth-run gate. That is the rule
     * a persisted counter would break in the direction nobody notices.
     */
    @Test
    fun `a new session starts the run count again`() = runUnitTest {
        val scenario = scenario(runsFinished = 0)
        repeat(4) { scenario.gate.noteRunFinished() }
        assertTrue(scenario.gate.showIfReady())

        scenario.sessions.roll()
        scenario.clock.advance(900_000)
        repeat(3) { scenario.gate.noteRunFinished() }

        assertFalse(scenario.gate.showIfReady())
    }

    @Test
    fun `nothing is shown in the first three days after install`() = runUnitTest {
        val scenario = scenario(installedDaysAgo = 2)

        assertFalse(scenario.gate.showIfReady())

        scenario.clock.advance(OneDayMs)
        assertTrue(scenario.gate.showIfReady())
    }

    @Test
    fun `the kill switch stops interstitials`() = runUnitTest {
        val scenario = scenario(config = mapOf("ads.enabled" to false))

        assertFalse(scenario.gate.showIfReady())
        assertTrue(scenario.network.shown.isEmpty())
    }

    /**
     * A malformed `ads.enabled` must resolve to the compiled default of `true`
     * rather than silently switching advertising off (L48). It is asserted here
     * rather than only in `:libraries:config` because this is the key that has a
     * consumer, and the direction of the old bug was "arguably safe" only for
     * this one.
     */
    @Test
    fun `a malformed kill switch falls back to the compiled default`() = runUnitTest {
        val scenario = scenario(config = mapOf("ads.enabled" to "nope"))

        assertTrue(scenario.gate.showIfReady())
    }

    /** An SDK that throws is a skipped ad, never a crashed game. */
    @Test
    fun `a network that throws is swallowed`() = runUnitTest {
        val scenario = scenario()
        scenario.network.throwOnShow = true

        assertFalse(scenario.gate.showIfReady())
    }

    /**
     * A QA override reaches [InterstitialPolicy], and the snapshot says so.
     *
     * This is the end of the chain the debug menu's config screen starts: an
     * override written at `ads.interstitial.suppressDaysSinceInstall` lands in
     * the merged `AppConfigMap`, `InterstitialSuppressDaysSinceInstall` resolves
     * it, and the rule that has made this app's interstitial unreachable on
     * every fresh install since C10 stops firing. Without the override the same
     * player is refused with `new_install`.
     */
    @Test
    fun `an overridden suppression window reaches the policy`() = runUnitTest {
        val blocked = scenario(installedDaysAgo = 0)
        assertFalse(blocked.gate.showIfReady())
        assertEquals(
            InterstitialBlock.NewInstall.reason,
            blocked.gate.snapshot().blockedReason,
        )

        val overridden = scenario(
            installedDaysAgo = 0,
            config = mapOf("ads.interstitial.suppressDaysSinceInstall" to 0),
        )

        assertTrue(overridden.gate.showIfReady())
        assertEquals(listOf(AdFormat.Interstitial), overridden.network.shown)
    }

    /**
     * The same numbers the decision was made from, which is the only property
     * that makes a debug readout worth drawing. A snapshot with its own second
     * set of reads could agree with nothing and still look right.
     */
    @Test
    fun `the snapshot reports the gate's own inputs`() = runUnitTest {
        val scenario = scenario(
            runsFinished = 2,
            config = mapOf("ads.interstitial.minSessionRuns" to 4),
        )

        val snapshot = scenario.gate.snapshot()

        assertEquals(2, snapshot.runsThisSession)
        assertEquals(4, snapshot.minSessionRuns)
        assertEquals(InterstitialBlock.TooFewRunsThisSession.reason, snapshot.blockedReason)
        assertFalse(scenario.gate.showIfReady())
    }

    /** SPEC 19's "current interstitial cooldown remaining", in the unit QA reads. */
    @Test
    fun `the snapshot reports the cooldown remaining rather than the time elapsed`() = runUnitTest {
        val scenario = scenario()
        assertTrue(scenario.gate.showIfReady())

        scenario.clock.advance(60_000)
        val snapshot = scenario.gate.snapshot()

        assertEquals(180, snapshot.cooldownSeconds)
        assertEquals(120, snapshot.cooldownRemainingSeconds)
        assertEquals(InterstitialBlock.Cooldown.reason, snapshot.blockedReason)
    }

    private fun TestScope.scenario(
        pro: Boolean = false,
        runsFinished: Int = 4,
        installedDaysAgo: Int = 14,
        config: Map<String, Any?> = emptyMap(),
    ): Scenario {
        val clock = MovableClock(Instant.fromEpochMilliseconds(Now))
        val network = FakeAdNetwork(interstitialResult = AdShowResult.Dismissed).apply {
            ready += AdFormat.Interstitial
        }
        val cache = FakeAdStateCache(
            AdState(firstSeenAtMs = Now - installedDaysAgo * OneDayMs)
        )
        val sessions = FakeSessionTracker()
        val runActivity = FakeRunActivity()
        val map = TestAdConfigMap(config)
        val session = AdSession(sessions)
        repeat(runsFinished) { session.noteRunFinished() }

        val gate = RealInterstitialGate(
            platformNetwork = network,
            houseAds = TestHouseAds(),
            entitlements = FakeEntitlements(pro),
            runActivity = runActivity,
            rewardedClock = RewardedClock(),
            adState = cache,
            session = session,
            appScope = AppCoroutineScope(dispatchers),
            clock = clock,
            adsEnabled = AdsEnabled(map),
            minSessionRuns = InterstitialMinSessionRuns(map),
            cooldownSeconds = InterstitialCooldownSeconds(map),
            rewardedGapSeconds = InterstitialRewardedGapSeconds(map),
            suppressDays = InterstitialSuppressDaysSinceInstall(map),
        )
        return Scenario(gate, network, cache, clock, sessions, runActivity)
    }

    private class Scenario(
        val gate: RealInterstitialGate,
        val network: FakeAdNetwork,
        val cache: FakeAdStateCache,
        val clock: MovableClock,
        val sessions: FakeSessionTracker,
        val runActivity: FakeRunActivity,
    )

    private companion object {
        const val Now = 1_760_000_000_000L
        const val OneDayMs = 86_400_000L
    }
}
