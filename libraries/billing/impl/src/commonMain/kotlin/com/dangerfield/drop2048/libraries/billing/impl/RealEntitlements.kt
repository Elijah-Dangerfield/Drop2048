package com.dangerfield.drop2048.libraries.billing.impl

import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.FreeEntitlements
import com.dangerfield.drop2048.libraries.billing.ProGrant
import com.dangerfield.drop2048.libraries.billing.ProductIds
import com.dangerfield.drop2048.libraries.billing.PurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.billing.StoreBilling
import com.dangerfield.drop2048.libraries.billing.StoreOwnership
import com.dangerfield.drop2048.libraries.billing.StorePurchaseResult
import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.drop2048.AppEvents
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The device's Pro entitlement, cached **true until proven false**, with QA's
 * grant folded in above it.
 *
 * ## Unknown changes nothing
 *
 * There is one interesting rule here and the class is built around it:
 * [StoreOwnership.Unknown] is not a "no". A store that is unreachable, a Play
 * account mid-signout, a StoreKit call that timed out — all of those mean *we
 * could not ask*, and the difference between "we could not ask" and "no" is a
 * paying customer watching an interstitial on a train.
 *
 * Only [StoreOwnership.NotOwned] clears the flag. The opposite failure — a
 * refunded purchase that stays Pro forever — is real too, and it is both much
 * rarer and much less annoying.
 *
 * ## The debug grant is folded here, once
 *
 * [ProGrant] is ORed into [isPro] rather than bound as a competing
 * [Entitlements], so there is one answer to "does this device own Pro" and one
 * place that decides it. This is also where C12's warning lands: the settings
 * binding used to do the fold, so the Daily Challenge — which asked a different
 * type entirely — never saw the grant. Folding it above the store instead means
 * every consumer, present and future, gets it for free.
 *
 * ## No server, no receipt store, no user id
 *
 * The app has no accounts. The entitlement is this cache plus the store's own
 * restore, which is why [restore] exists as a visible control in Settings (Apple
 * requires it) and why a reinstall gets Pro back through Play or StoreKit rather
 * than through us.
 *
 * [AutoInit] because the hydrate-from-disk in `init` is what stops the first
 * frame of the game rendering as a free player for one dispatch.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = Entitlements::class,
    replaces = [FreeEntitlements::class],
)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RealEntitlements(
    private val store: StoreBilling,
    private val appCache: AppCache,
    private val appScope: AppCoroutineScope,
    proGrant: ProGrant,
    appEventsProvider: () -> AppEvents,
) : Entitlements, AutoInit {

    private val logger = KLog.withTag("Entitlements")
    private val purchased = MutableStateFlow(false)

    override val isPro: StateFlow<Boolean> = combine(purchased, proGrant.granted) { bought, granted ->
        bought || granted
    }.stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        appScope.launch {
            purchased.value = Catching { appCache.get().isProEntitled }
                .logOnFailure { "Could not read the cached entitlement" }
                .getOrElse { false }
            refreshFromStore()
        }

        // A purchase made on another device, a refund, or a Play account switch
        // all land while the app is backgrounded. Re-asking on every foreground
        // is cheap — both stores answer from a local cache — and it is the only
        // signal there is without a server.
        appScope.launch {
            appEventsProvider()
                .live()
                .filterIsInstance<AppEvent.OnForeground>()
                .collect { refreshFromStore() }
        }
    }

    override suspend fun purchasePro(trigger: String?): PurchaseOutcome {
        // SPEC 17's "purchase started". Paired with `iap.purchase_result` so a
        // store sheet the player abandoned, and one that never opened at all,
        // are two different shapes rather than one absence — the second is a
        // bug in us and the first is a decision by them.
        logger.logEvent("iap.purchase_started", "trigger" to trigger)
        val outcome = Catching { store.purchase(ProductIds.pro) }
            .logOnFailure { "Purchase threw" }
            .getOrNull()

        val result = when (outcome?.result) {
            StorePurchaseResult.Purchased -> {
                setPurchased(true)
                PurchaseOutcome.Success
            }

            StorePurchaseResult.AlreadyOwned -> {
                setPurchased(true)
                PurchaseOutcome.AlreadyOwned
            }

            StorePurchaseResult.Cancelled -> PurchaseOutcome.Cancelled
            StorePurchaseResult.Unavailable -> PurchaseOutcome.Unavailable
            StorePurchaseResult.Failed -> PurchaseOutcome.Failed(outcome.errorKind ?: "store")
            null -> PurchaseOutcome.Failed("threw")
        }

        logger.logEvent(
            "iap.purchase_result",
            "outcome" to result.name,
            "error_kind" to (result as? PurchaseOutcome.Failed)?.kind,
            "trigger" to trigger,
        )
        return result
    }

    override suspend fun restore(): RestoreOutcome {
        // SPEC 17 asks for "restore attempted" specifically, and the result
        // event below cannot answer it: a restore that hangs on an
        // unreachable store never produces one.
        logger.logEvent("iap.restore_started")
        val ownership = Catching { store.restore(ProductIds.pro) }
            .logOnFailure { "Restore threw" }
            .getOrElse { StoreOwnership.Unknown }

        val result = when (ownership) {
            StoreOwnership.Owned -> {
                setPurchased(true)
                RestoreOutcome.Restored
            }

            // A deliberate restore is the one moment a "no" is trustworthy: the
            // player asked, the store answered, and leaving a stale `true`
            // behind would make the button lie.
            StoreOwnership.NotOwned -> {
                setPurchased(false)
                RestoreOutcome.NothingToRestore
            }

            StoreOwnership.Unknown -> RestoreOutcome.Unavailable
        }

        logger.logEvent("iap.restore_result", "outcome" to result.name)
        return result
    }

    private suspend fun refreshFromStore() {
        when (
            Catching { store.ownership(ProductIds.pro) }
                .logOnFailure { "Ownership check threw" }
                .getOrElse { StoreOwnership.Unknown }
        ) {
            StoreOwnership.Owned -> setPurchased(true)
            StoreOwnership.NotOwned -> setPurchased(false)
            StoreOwnership.Unknown -> logger.d { "Store unreachable; keeping the cached entitlement" }
        }
    }

    /**
     * Only the *purchase* is persisted. A debug grant deliberately is not: it
     * lives in [ProGrant]'s memory and dies with the process, because a granted
     * entitlement that survived a relaunch is indistinguishable from a billing
     * bug — and the person most likely to hit it is the tester who granted it and
     * forgot.
     */
    private suspend fun setPurchased(entitled: Boolean) {
        if (purchased.value == entitled) return
        purchased.value = entitled
        Catching { appCache.update { it.copy(isProEntitled = entitled) } }
            .logOnFailure { "Could not persist the entitlement" }
    }
}
