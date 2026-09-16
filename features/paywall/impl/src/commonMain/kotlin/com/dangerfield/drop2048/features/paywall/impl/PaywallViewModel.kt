package com.dangerfield.drop2048.features.paywall.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.ProductIds
import com.dangerfield.drop2048.libraries.billing.PurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.billing.StoreBilling
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.flowroutines.collectIn
import me.tatarka.inject.annotations.Inject

/**
 * SPEC 12's Pro sheet: the four things it does, what it costs, and two buttons.
 *
 * ### The price comes from the store or not at all, and nothing waits for it
 *
 * [PaywallState.price] is null until the store answers, and the screen draws the
 * buy button without a number rather than with a guess. Hardcoding "$2.99" would
 * be wrong in every storefront that is not the US one and illegal in a few, so
 * the number is whatever `priceLabel` returns for [ProductIds.pro] and nothing
 * else. There is no config key in this path: `pro.price.tier` used to claim the
 * product id was remote, was never read, and was deleted by D25.
 *
 * **There is no loading state, and that is a fix rather than an omission.** The
 * first version gated the whole sheet on `loading` until the price arrived. On a
 * device where Play Billing cannot connect — an emulator, a device signed out of
 * Play, a product not yet live in the console — `connect()` spends its fifteen
 * second timeout and `queryProductDetailsAsync` another twenty, so the sheet was
 * **blank for thirty-five seconds** and then drew correctly. Found by installing
 * the build and tapping the row (L56); no test was ever going to see it, because
 * every double answers instantly.
 *
 * Nothing on this screen actually depends on the price: the title, the four
 * perks and both buttons render identically without it. So they render
 * immediately and the number appears in the button when and if it arrives.
 *
 * ### It closes itself when the player becomes Pro
 *
 * Rather than on the purchase call returning. A purchase can also land from a
 * restore, from another device, or from a pending payment clearing while this
 * screen is open, and all of them arrive as [Entitlements.isPro] flipping. One
 * exit condition, whatever caused it.
 */
@Inject
class PaywallViewModel(
    private val entitlements: Entitlements,
    private val store: StoreBilling,
) : SEAViewModel<PaywallState, PaywallEvent, PaywallAction>(initialStateArg = PaywallState()) {

    init {
        takeAction(PaywallAction.Load)
        entitlements.isPro.collectIn(viewModelScope) { pro ->
            if (pro) takeAction(PaywallAction.BecamePro)
        }
    }

    override suspend fun handleAction(action: PaywallAction) {
        when (action) {
            PaywallAction.Load -> action.load()
            PaywallAction.Buy -> action.buy()
            PaywallAction.Restore -> action.restore()
            PaywallAction.BecamePro -> action.updateState { it.copy(isPro = true, busy = false) }
            PaywallAction.Close -> sendEvent(PaywallEvent.Leave)
        }
    }

    private suspend fun PaywallAction.load() {
        val price = Catching { store.priceLabel(ProductIds.pro) }
            .logOnFailure { "Could not read the Pro price" }
            .getOrNull()
        updateState { it.copy(price = price, isPro = entitlements.isPro.value) }
    }

    /**
     * [PaywallMessage.Cancelled] is deliberately **not** a message.
     *
     * A player who backed out of the store dialog knows they backed out, and
     * telling them so is the app arguing with a decision they just made. Every
     * other branch says something, because every other branch is the app
     * failing at something the player asked for.
     */
    private suspend fun PaywallAction.buy() {
        if (state.busy) return
        updateState { it.copy(busy = true, message = null) }
        val outcome = Catching { entitlements.purchasePro(trigger = null) }
            .logOnFailure { "Purchase threw" }
            .getOrDefault(PurchaseOutcome.Failed("threw"))
        updateState {
            it.copy(
                busy = false,
                message = when (outcome) {
                    PurchaseOutcome.Success, PurchaseOutcome.AlreadyOwned -> null
                    PurchaseOutcome.Cancelled -> null
                    PurchaseOutcome.Unavailable -> PaywallMessage.Unavailable
                    is PurchaseOutcome.Failed -> PaywallMessage.Failed
                },
            )
        }
    }

    private suspend fun PaywallAction.restore() {
        if (state.busy) return
        updateState { it.copy(busy = true, message = null) }
        val outcome = Catching { entitlements.restore() }
            .logOnFailure { "Restore threw" }
            .getOrDefault(RestoreOutcome.Unavailable)
        updateState {
            it.copy(
                busy = false,
                message = when (outcome) {
                    RestoreOutcome.Restored -> null
                    RestoreOutcome.NothingToRestore -> PaywallMessage.NothingToRestore
                    RestoreOutcome.Unavailable -> PaywallMessage.Unavailable
                },
            )
        }
    }
}

data class PaywallState(
    /**
     * As the store formats it. Null means the store has not answered **yet**, or
     * could not be asked at all, and the screen deliberately cannot tell those
     * apart — because it draws the same thing either way. See the class KDoc for
     * why there is no third state here.
     */
    val price: String? = null,

    /** Set while the store dialog is up, so neither button can be pressed twice. */
    val busy: Boolean = false,

    /**
     * True once the purchase lands, which is also what closes the sheet. It is on
     * the state rather than only an event so the screen can draw the owned state
     * for the instant between the two.
     */
    val isPro: Boolean = false,

    val message: PaywallMessage? = null,
)

/** The three things that can go wrong, and nothing that went right. */
enum class PaywallMessage {
    NothingToRestore,
    Unavailable,
    Failed,
}

sealed interface PaywallEvent {
    data object Leave : PaywallEvent
}

sealed interface PaywallAction {
    data object Load : PaywallAction
    data object Buy : PaywallAction
    data object Restore : PaywallAction
    data object BecamePro : PaywallAction
    data object Close : PaywallAction
}
