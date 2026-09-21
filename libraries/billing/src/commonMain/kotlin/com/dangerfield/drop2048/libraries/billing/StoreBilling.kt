@file:OptIn(ExperimentalObjCName::class)

package com.dangerfield.drop2048.libraries.billing

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The store's answer to "does this device own the product".
 *
 * [Unknown] is the whole reason this is three-valued. A network failure, a
 * signed-out Play account or a StoreKit hiccup all mean *we could not ask*, and a
 * two-valued answer collapses that into "not owned" — which is how a paying
 * customer starts seeing interstitials on a train. Only [NotOwned] is allowed to
 * clear the cached entitlement.
 */
@ObjCName("StoreOwnership", exact = true)
enum class StoreOwnership {
    Owned,
    NotOwned,
    Unknown,
}

/** How a purchase attempt ended, flattened for the Swift side. */
@ObjCName("StorePurchaseResult", exact = true)
enum class StorePurchaseResult {
    Purchased,
    Cancelled,
    AlreadyOwned,

    /** Billing is unavailable on this device: no Play Services, parental controls, sandbox off. */
    Unavailable,
    Failed,
}

/** A result plus a machine-readable failure kind for `iap.purchase_result`. */
@ObjCName("StorePurchaseOutcome", exact = true)
class StorePurchaseOutcome(
    val result: StorePurchaseResult,
    val errorKind: String? = null,
)

/**
 * The thin platform seam under [Entitlements]: talk to Play or StoreKit, say what
 * the store said. No caching, no policy, no state.
 *
 * **Android binding** is `PlayStoreBilling` in `:libraries:billing:impl/androidMain`.
 * **iOS binding** is `IOSStoreBilling` (Swift, StoreKit 2) handed to the graph
 * through `IosAppComponent`, the same route `ReviewLauncher` takes.
 *
 * Implementations must not throw — a store that is down has to arrive here as
 * [StoreOwnership.Unknown] or [StorePurchaseResult.Failed], because the layer
 * above treats a thrown exception and a "no" identically and only one of those is
 * safe.
 */
@ObjCName("StoreBilling", exact = true)
interface StoreBilling {

    /**
     * Ask the store whether [productId] is owned right now.
     *
     * On Android this is `queryPurchasesAsync`, which reads Play's local cache
     * and refreshes it, so it works offline for a device that has seen the
     * purchase before. On iOS it is `Transaction.currentEntitlements`, likewise
     * locally verified.
     */
    suspend fun ownership(productId: String): StoreOwnership

    /** Runs the purchase flow. Must be safe to call when the product is already owned. */
    suspend fun purchase(productId: String): StorePurchaseOutcome

    /**
     * On Android there is nothing to restore — `queryPurchasesAsync` already is
     * the restore — so the Play implementation forwards to [ownership].
     */
    suspend fun restore(productId: String): StoreOwnership

    /**
     * Localised price as the store formats it ("$2.99", "2,99 €"), or null when
     * the store could not be reached.
     *
     * Never hardcode a price. It is set per storefront and the store owns it,
     * which is why SPEC 10 keeps the amount out of remote config. The *product*
     * is not remote either — see [ProductIds.pro], and D25 for the key that
     * claimed otherwise and was deleted.
     */
    suspend fun priceLabel(productId: String): String?
}

@ObjCName("ProductIds", exact = true)
object ProductIds {
    /**
     * One non-consumable, SPEC 12.
     *
     * The same string on both stores by design — Play calls it a managed
     * product, Apple a non-consumable — so there is one constant rather than a
     * platform branch at every call site. It is **not** in remote config: a
     * product id is a store operation, and a config outage that emptied it would
     * take Pro down with it.
     */
    const val pro: String = "drop2048_pro"
}

/**
 * The shape of the answer on a platform that has not wired a store.
 *
 * **Nothing binds this any more, and it is no longer reachable at runtime.**
 * Android has `PlayStoreBilling` and iOS has `IOSStoreBilling`, handed to the
 * graph through `IosAppComponent`, so both platforms answer from a real store.
 *
 * It deliberately carries no `@ContributesBinding`: a contributed binding here
 * plus the `@Provides` in `IosAppComponent` is a duplicate the graph rejects,
 * which is the same resolution `NotWiredAdNetwork` reached. The class stays as
 * the documented shape of a correct no-store answer, and as the thing to bind
 * if a third platform ever arrives before its billing does.
 *
 * [StoreOwnership.Unknown] rather than [StoreOwnership.NotOwned], because
 * "we could not ask" is true and "they do not own it" is a claim this has no
 * standing to make. It is also what keeps a cached entitlement from being
 * cleared by a build that simply cannot see the store. The opposite, a stub
 * that answered [StoreOwnership.Owned], would hand Pro out for nothing.
 */
@SingleIn(AppScope::class)
@Inject
class NoStoreBilling : StoreBilling {
    override suspend fun ownership(productId: String): StoreOwnership = StoreOwnership.Unknown

    override suspend fun purchase(productId: String): StorePurchaseOutcome =
        StorePurchaseOutcome(StorePurchaseResult.Unavailable, errorKind = "no_store_wired")

    override suspend fun restore(productId: String): StoreOwnership = StoreOwnership.Unknown

    override suspend fun priceLabel(productId: String): String? = null
}
