package com.dangerfield.drop2048.features.paywall.impl

import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.ProductIds
import com.dangerfield.drop2048.libraries.billing.PurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.billing.StoreBilling
import com.dangerfield.drop2048.libraries.billing.StoreOwnership
import com.dangerfield.drop2048.libraries.billing.StorePurchaseOutcome
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sheet's four rules, two of which are about what it deliberately does not
 * say.
 */
class PaywallViewModelTest : CoroutineTest() {

    @Test
    fun `the price comes from the store`() = runUnitTest {
        val scenario = scenario()

        assertEquals("$2.99", scenario.viewModel.state.price)
    }

    /**
     * A store that could not be reached leaves the button with no number rather
     * than with a guess. A hardcoded price is wrong in every storefront that is
     * not the US one, and in a few places it is illegal.
     */
    @Test
    fun `a store that cannot be reached leaves the price absent`() = runUnitTest {
        val scenario = scenario(price = null)

        assertNull(scenario.viewModel.state.price)
    }

    @Test
    fun `buying closes the sheet`() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(PaywallAction.Buy)
        runCurrent()

        assertTrue(scenario.viewModel.state.isPro)
    }

    /**
     * A player who backed out of the store dialog knows they backed out. Telling
     * them so is the app arguing with a decision they just made — and it is the
     * one branch here with no message.
     */
    @Test
    fun `a cancelled purchase says nothing`() = runUnitTest {
        val scenario = scenario(purchase = PurchaseOutcome.Cancelled)

        scenario.viewModel.takeAction(PaywallAction.Buy)
        runCurrent()

        assertNull(scenario.viewModel.state.message)
    }

    @Test
    fun `a failed purchase says so`() = runUnitTest {
        val scenario = scenario(purchase = PurchaseOutcome.Failed("network"))

        scenario.viewModel.takeAction(PaywallAction.Buy)
        runCurrent()

        assertEquals(PaywallMessage.Failed, scenario.viewModel.state.message)
    }

    /**
     * Every restore branch says something. A restore that silently does nothing
     * is the single most common reason this control gets reported as broken,
     * because the player cannot tell "you never bought it" from "we could not
     * ask".
     */
    @Test
    fun `a restore that finds nothing says so`() = runUnitTest {
        val scenario = scenario(restore = RestoreOutcome.NothingToRestore)

        scenario.viewModel.takeAction(PaywallAction.Restore)
        runCurrent()

        assertEquals(PaywallMessage.NothingToRestore, scenario.viewModel.state.message)
    }

    @Test
    fun `a restore against an unreachable store says so`() = runUnitTest {
        val scenario = scenario(restore = RestoreOutcome.Unavailable)

        scenario.viewModel.takeAction(PaywallAction.Restore)
        runCurrent()

        assertEquals(PaywallMessage.Unavailable, scenario.viewModel.state.message)
    }

    /**
     * The sheet closes on the entitlement flipping rather than on the purchase
     * call returning, so a restore, a purchase made on another device and a
     * pending payment clearing all end it the same way.
     */
    @Test
    fun `becoming pro from anywhere closes the sheet`() = runUnitTest {
        val scenario = scenario()

        scenario.entitlements.becomes(true)
        runCurrent()

        assertTrue(scenario.viewModel.state.isPro)
    }

    private fun TestScope.scenario(
        price: String? = "$2.99",
        purchase: PurchaseOutcome = PurchaseOutcome.Success,
        restore: RestoreOutcome = RestoreOutcome.Restored,
    ): Scenario {
        val entitlements = ScriptedEntitlements(purchase, restore)
        val viewModel = PaywallViewModel(
            entitlements = entitlements,
            store = PricedStore(price),
        )
        runCurrent()
        return Scenario(viewModel, entitlements)
    }

    private class Scenario(
        val viewModel: PaywallViewModel,
        val entitlements: ScriptedEntitlements,
    )
}

private class ScriptedEntitlements(
    private val purchase: PurchaseOutcome,
    private val restoreOutcome: RestoreOutcome,
) : Entitlements {
    private val state = MutableStateFlow(false)

    override val isPro: StateFlow<Boolean> = state

    fun becomes(pro: Boolean) {
        state.value = pro
    }

    override suspend fun purchasePro(trigger: String?): PurchaseOutcome {
        if (purchase == PurchaseOutcome.Success || purchase == PurchaseOutcome.AlreadyOwned) {
            state.value = true
        }
        return purchase
    }

    override suspend fun restore(): RestoreOutcome {
        if (restoreOutcome == RestoreOutcome.Restored) state.value = true
        return restoreOutcome
    }
}

/** Only the price is interesting here; the ViewModel never buys through this. */
private class PricedStore(private val price: String?) : StoreBilling {
    override suspend fun ownership(productId: String): StoreOwnership = StoreOwnership.NotOwned

    override suspend fun purchase(productId: String): StorePurchaseOutcome =
        error("the ViewModel purchases through Entitlements")

    override suspend fun restore(productId: String): StoreOwnership = StoreOwnership.NotOwned

    override suspend fun priceLabel(productId: String): String? =
        price.takeIf { productId == ProductIds.pro }
}
