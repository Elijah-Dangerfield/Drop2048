package com.dangerfield.drop2048.libraries.billing.impl

import com.dangerfield.drop2048.libraries.billing.InMemoryProGrant
import com.dangerfield.drop2048.libraries.billing.PaywallRequest
import com.dangerfield.drop2048.libraries.billing.PaywallTrigger
import com.dangerfield.drop2048.libraries.billing.StoreOwnership
import com.dangerfield.drop2048.libraries.drop2048.AppEvents
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.gameconfig.ProUpsellEnabled
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SPEC 12's two upsell surfaces, and the one thing that is capped.
 *
 * *A small persistent entry in Settings, and one non-modal card on the
 * stacked-out screen at most once per session.* The cap belongs to the card,
 * because the card is the only one of the two that appears without being asked
 * for — and a cap on the Settings entry would be the shop refusing to serve
 * someone who walked in.
 */
class RealPaywallCoordinatorTest : CoroutineTest() {

    @Test
    fun `an offer reaches the bus`() = runUnitTest {
        val coordinator = coordinator()
        val seen = mutableListOf<PaywallRequest>()
        val job = launch { coordinator.requests.toList(seen) }
        runCurrent()

        assertTrue(coordinator.requestOffer(PaywallTrigger.Direct))
        runCurrent()

        assertEquals(listOf<PaywallRequest>(PaywallRequest.Offer(PaywallTrigger.Direct)), seen)
        job.cancel()
    }

    @Test
    fun `pro is never offered anything`() = runUnitTest {
        val coordinator = coordinator(pro = true)

        assertFalse(coordinator.requestOffer(PaywallTrigger.Direct))
        assertFalse(coordinator.requestOffer(PaywallTrigger.StackedOut))
        assertFalse(coordinator.claimStackedOutCard())
    }

    @Test
    fun `the card is taken once per session`() = runUnitTest {
        val coordinator = coordinator()

        assertTrue(coordinator.claimStackedOutCard())
        assertFalse(coordinator.claimStackedOutCard())
        assertFalse(coordinator.claimStackedOutCard())
    }

    @Test
    fun `a new session gets a new card`() = runUnitTest {
        val scenario = scenarioWithSessions()

        assertTrue(scenario.coordinator.claimStackedOutCard())
        assertFalse(scenario.coordinator.claimStackedOutCard())

        scenario.sessions.roll()

        assertTrue(scenario.coordinator.claimStackedOutCard())
    }

    /**
     * The kill switch takes the offers away and leaves the shop open. Turning
     * `pro.upsell.enabled` off is a decision about how much the app asks, not
     * about whether it will sell to someone who came looking.
     */
    @Test
    fun `the upsell switch silences everything except the direct entry`() = runUnitTest {
        val coordinator = coordinator(upsellEnabled = false)

        assertFalse(coordinator.claimStackedOutCard())
        assertFalse(coordinator.requestOffer(PaywallTrigger.StackedOut))
        assertTrue(coordinator.requestOffer(PaywallTrigger.Direct))
    }

    /**
     * The card is capped; asking is not. A player who taps the card, backs out
     * and comes back through Settings is asking twice, and refusing the second
     * one would be a shop that closes when you leave it.
     */
    @Test
    fun `taking the card does not cap the paywall behind it`() = runUnitTest {
        val coordinator = coordinator()

        assertTrue(coordinator.claimStackedOutCard())
        assertTrue(coordinator.requestOffer(PaywallTrigger.StackedOut))
        assertTrue(coordinator.requestOffer(PaywallTrigger.StackedOut))
    }

    private fun TestScope.coordinator(
        pro: Boolean = false,
        upsellEnabled: Boolean = true,
    ) = scenarioWithSessions(pro, upsellEnabled).coordinator

    private fun TestScope.scenarioWithSessions(
        pro: Boolean = false,
        upsellEnabled: Boolean = true,
    ): Scenario {
        val sessions = FakeSessionTracker()
        val entitlements = RealEntitlements(
            store = FakeStoreBilling(
                ownership = if (pro) StoreOwnership.Owned else StoreOwnership.NotOwned,
            ),
            appCache = FakeAppCache(),
            appScope = AppCoroutineScope(dispatchers),
            proGrant = InMemoryProGrant(),
            appEventsProvider = { AppEvents(FakeAppEventBus()) },
        )
        runCurrent()
        val coordinator = RealPaywallCoordinator(
            entitlements = entitlements,
            sessionTracker = sessions,
            upsellEnabled = ProUpsellEnabled(
                TestBillingConfigMap(mapOf("pro.upsell.enabled" to upsellEnabled)),
            ),
        )
        return Scenario(coordinator, sessions)
    }

    private class Scenario(
        val coordinator: RealPaywallCoordinator,
        val sessions: FakeSessionTracker,
    )
}
