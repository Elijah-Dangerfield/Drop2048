package com.dangerfield.drop2048.libraries.billing.impl

import com.dangerfield.drop2048.libraries.billing.InMemoryProGrant
import com.dangerfield.drop2048.libraries.billing.PurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.billing.StoreOwnership
import com.dangerfield.drop2048.libraries.billing.StorePurchaseResult
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.drop2048.AppEvents
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The entitlement's one interesting rule, and the fold that C12 warned about.
 *
 * **"We could not ask" is not "no".** A store that is unreachable leaves a cached
 * entitlement alone, because the alternative is a paying customer seeing
 * interstitials in a tunnel. The opposite failure — a refund that stays Pro until
 * the next successful check — is real and is both rarer and much less annoying.
 */
class RealEntitlementsTest : CoroutineTest() {

    @Test
    fun `a fresh install owns nothing`() = runUnitTest {
        val scenario = scenario()

        assertFalse(scenario.entitlements.isPro.value)
    }

    @Test
    fun `a store that says owned makes the player pro`() = runUnitTest {
        val scenario = scenario(ownership = StoreOwnership.Owned)

        assertTrue(scenario.entitlements.isPro.value)
        assertTrue(scenario.cache.get().isProEntitled)
    }

    /**
     * The rule the whole class is built around, asserted on the path that
     * actually produces it: the entitlement was cached, the store cannot be
     * reached on the next launch, and Pro survives.
     */
    @Test
    fun `an unreachable store leaves a cached entitlement alone`() = runUnitTest {
        val scenario = scenario(
            ownership = StoreOwnership.Unknown,
            cached = AppData(isProEntitled = true),
        )

        assertTrue(scenario.entitlements.isPro.value)
    }

    @Test
    fun `a store that throws leaves a cached entitlement alone`() = runUnitTest {
        val scenario = scenario(
            ownership = StoreOwnership.Unknown,
            cached = AppData(isProEntitled = true),
        )
        scenario.store.throwOnOwnership = true
        scenario.events.dispatch(AppEvent.OnForeground(isColdBoot = false))
        runCurrent()

        assertTrue(scenario.entitlements.isPro.value)
    }

    /** The one answer that is trustworthy enough to clear it. */
    @Test
    fun `a store that says not owned clears the entitlement`() = runUnitTest {
        val scenario = scenario(
            ownership = StoreOwnership.NotOwned,
            cached = AppData(isProEntitled = true),
        )

        assertFalse(scenario.entitlements.isPro.value)
    }

    /**
     * A purchase made on another device lands while the app is backgrounded, and
     * a foreground is the only signal there is without a server.
     */
    @Test
    fun `foregrounding re-asks the store`() = runUnitTest {
        val scenario = scenario()
        assertFalse(scenario.entitlements.isPro.value)

        scenario.store.ownership = StoreOwnership.Owned
        scenario.events.dispatch(AppEvent.OnForeground(isColdBoot = false))
        runCurrent()

        assertTrue(scenario.entitlements.isPro.value)
    }

    @Test
    fun `a successful purchase is pro and is persisted`() = runUnitTest {
        val scenario = scenario()

        assertEquals(PurchaseOutcome.Success, scenario.entitlements.purchasePro())
        assertTrue(scenario.entitlements.isPro.value)
        assertTrue(scenario.cache.get().isProEntitled)
    }

    @Test
    fun `a cancelled purchase changes nothing`() = runUnitTest {
        val scenario = scenario()
        scenario.store.purchaseResult = StorePurchaseResult.Cancelled

        assertEquals(PurchaseOutcome.Cancelled, scenario.entitlements.purchasePro())
        assertFalse(scenario.entitlements.isPro.value)
    }

    @Test
    fun `already owned is pro`() = runUnitTest {
        val scenario = scenario()
        scenario.store.purchaseResult = StorePurchaseResult.AlreadyOwned

        assertEquals(PurchaseOutcome.AlreadyOwned, scenario.entitlements.purchasePro())
        assertTrue(scenario.entitlements.isPro.value)
    }

    /**
     * A deliberate restore is the one moment a "no" from the store is
     * trustworthy: the player asked, the store answered, and leaving a stale
     * `true` behind would make the button lie.
     */
    @Test
    fun `a restore that finds nothing clears the entitlement and says so`() = runUnitTest {
        val scenario = scenario(
            ownership = StoreOwnership.Unknown,
            cached = AppData(isProEntitled = true),
        )
        scenario.store.ownership = StoreOwnership.NotOwned

        assertEquals(RestoreOutcome.NothingToRestore, scenario.entitlements.restore())
        assertFalse(scenario.entitlements.isPro.value)
    }

    @Test
    fun `a restore against an unreachable store says so rather than clearing`() = runUnitTest {
        val scenario = scenario(
            ownership = StoreOwnership.Unknown,
            cached = AppData(isProEntitled = true),
        )

        assertEquals(RestoreOutcome.Unavailable, scenario.entitlements.restore())
        assertTrue(scenario.entitlements.isPro.value)
    }

    /**
     * The seam unification, from the consumer's side.
     *
     * Before C10 the debug grant folded into a `ProEntitlement` that only
     * Settings read, and the Daily Challenge asked a different type that could
     * never see it — so QA's Pro switch silently failed to give the Daily its
     * second attempt. Folding it here, above the store, means every consumer of
     * `Entitlements` gets it, and there is only one `Entitlements`.
     */
    @Test
    fun `the debug grant makes the player pro everywhere`() = runUnitTest {
        val scenario = scenario()
        assertFalse(scenario.entitlements.isPro.value)

        scenario.proGrant.setGranted(true)
        runCurrent()

        assertTrue(scenario.entitlements.isPro.value)
    }

    /**
     * A granted entitlement that survived a relaunch is indistinguishable from a
     * billing bug, and the person most likely to hit it is the tester who
     * granted it and forgot. So the grant never reaches the cache.
     */
    @Test
    fun `the debug grant is never persisted`() = runUnitTest {
        val scenario = scenario()
        scenario.proGrant.setGranted(true)
        runCurrent()

        assertFalse(scenario.cache.get().isProEntitled)
    }

    @Test
    fun `revoking the debug grant takes pro away again`() = runUnitTest {
        val scenario = scenario()
        scenario.proGrant.setGranted(true)
        runCurrent()
        scenario.proGrant.setGranted(false)
        runCurrent()

        assertFalse(scenario.entitlements.isPro.value)
    }

    private fun TestScope.scenario(
        ownership: StoreOwnership = StoreOwnership.NotOwned,
        cached: AppData = AppData(),
    ): Scenario {
        val store = FakeStoreBilling(ownership = ownership)
        val cache = FakeAppCache(cached)
        val events = FakeAppEventBus()
        val proGrant = InMemoryProGrant()
        val entitlements = RealEntitlements(
            store = store,
            appCache = cache,
            appScope = AppCoroutineScope(dispatchers),
            proGrant = proGrant,
            appEventsProvider = { AppEvents(events) },
        )
        runCurrent()
        return Scenario(entitlements, store, cache, events, proGrant)
    }

    private class Scenario(
        val entitlements: RealEntitlements,
        val store: FakeStoreBilling,
        val cache: FakeAppCache,
        val events: FakeAppEventBus,
        val proGrant: InMemoryProGrant,
    )
}
