package com.dangerfield.drop2048.libraries.billing.impl

import com.dangerfield.drop2048.libraries.billing.StoreBilling
import com.dangerfield.drop2048.libraries.billing.StoreOwnership
import com.dangerfield.drop2048.libraries.billing.StorePurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.StorePurchaseResult
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.drop2048.AppEventBus
import com.dangerfield.drop2048.libraries.drop2048.Session
import com.dangerfield.drop2048.libraries.drop2048.SessionStartReason
import com.dangerfield.drop2048.libraries.drop2048.SessionTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A store a test can put in any of its real states, including the one that
 * matters: "could not ask".
 */
class FakeStoreBilling(
    var ownership: StoreOwnership = StoreOwnership.NotOwned,
    var purchaseResult: StorePurchaseResult = StorePurchaseResult.Purchased,
    var price: String? = "$2.99",
) : StoreBilling {

    var throwOnOwnership = false
    var restoreCalls = 0
        private set

    override suspend fun ownership(productId: String): StoreOwnership {
        if (throwOnOwnership) error("store exploded")
        return ownership
    }

    override suspend fun purchase(productId: String): StorePurchaseOutcome =
        StorePurchaseOutcome(purchaseResult)

    override suspend fun restore(productId: String): StoreOwnership {
        restoreCalls++
        return ownership(productId)
    }

    override suspend fun priceLabel(productId: String): String? = price
}

class FakeAppCache(initial: AppData = AppData()) : AppCache {
    private val state = MutableStateFlow(initial)

    override val updates: Flow<AppData> = state

    override suspend fun get(): AppData = state.value

    override suspend fun set(value: AppData) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AppData()
    }
}

/**
 * Foregrounding is the only signal the entitlement has without a server, so the
 * bus is real and only its source is faked.
 *
 * `AppEvents` is a final class over an `AppEventBus`, and substituting the bus
 * rather than the wrapper means the test exercises the same `live()` the app
 * does — including its no-replay semantics, which is the property
 * `RealEntitlements` is relying on when it re-asks the store on every
 * foreground.
 */
class FakeAppEventBus : AppEventBus {
    private val events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8)

    override fun dispatch(event: AppEvent) {
        events.tryEmit(event)
    }

    override fun eventStream(): Flow<AppEvent> = events

    override fun liveEventStream(): Flow<AppEvent> = events
}

class FakeSessionTracker : SessionTracker {
    private val state = MutableStateFlow(
        Session(id = 1, startedAtMs = 0, reason = SessionStartReason.ColdBoot, uuid = "test")
    )

    override val current: Session get() = state.value

    override fun observe(): Flow<Session> = state

    fun roll() {
        state.value = state.value.copy(id = state.value.id + 1)
    }
}

/** An empty map resolves every key to its compiled default, as an offline device does. */
class TestBillingConfigMap(overrides: Map<String, Any?> = emptyMap()) : AppConfigMap() {

    override val map: Map<String, *> = overrides.entries.fold(emptyMap<String, Any?>()) { acc, entry ->
        acc.merge(entry.key.split('.'), entry.value)
    }

    private fun Map<String, Any?>.merge(path: List<String>, value: Any?): Map<String, Any?> {
        val head = path.first()
        if (path.size == 1) return this + (head to value)
        @Suppress("UNCHECKED_CAST")
        val child = (this[head] as? Map<String, Any?>) ?: emptyMap()
        return this + (head to child.merge(path.drop(1), value))
    }
}
