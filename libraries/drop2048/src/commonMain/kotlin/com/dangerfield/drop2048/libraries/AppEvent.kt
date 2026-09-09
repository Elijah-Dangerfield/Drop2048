package com.dangerfield.drop2048.libraries.drop2048

import kotlinx.coroutines.flow.Flow

sealed class AppEvent {
    data object ColdBoot : AppEvent()
    data object WarmBoot : AppEvent()
    data class OnForeground(val isColdBoot: Boolean) : AppEvent()
    data object OnBackground : AppEvent()

    /**
     * Connectivity was just regained — an offline→online edge. Dispatched by
     * [com.dangerfield.drop2048.libraries.drop2048.impl.ConnectivityEdgeDispatcher],
     * which watches `AppState.isOffline`, drops the optimistic initial value,
     * debounces flapping, and only fires on the true→false transition (so an
     * online cold boot never emits one).
     *
     * This is a *data-freshness* trigger: remote-config refresh and offline
     * outboxes hang their reconnect flush off it. Like
     * the other events, listeners must run synchronously and hand heavy work off
     * to their own scope.
     */
    data object ConnectivityRegained : AppEvent()
}

interface AppEventListener {
    fun onColdBoot(event: AppEvent.ColdBoot) {}
    fun onWarmBoot(event: AppEvent.WarmBoot) {}
    fun onForeground(event: AppEvent.OnForeground) {}
    fun onBackground(event: AppEvent.OnBackground) {}
    fun onConnectivityRegained(event: AppEvent.ConnectivityRegained) {}
}

/**
 * Public dispatch surface for [AppEvent]. The concrete
 * [com.dangerfield.drop2048.libraries.drop2048.impl.AppEventDispatcher] handles
 * boot/foreground/background lifecycle implicitly; explicit events
 * (currently just [AppEvent.ConnectivityRegained]) are dispatched by callers
 * that hold this bus.
 *
 * Lives in the api module so feature impls + libraries that can't see
 * the dispatcher's impl module can still publish events.
 */
interface AppEventBus {
    fun dispatch(event: AppEvent)

    /**
     * The same events [dispatch] fans out to listeners, as a hot stream — for
     * consumers that prefer to react reactively (filter, combine, carry state
     * across events) instead of implementing [AppEventListener]. Inject [AppEvents]
     * rather than depending on the bus directly.
     */
    fun eventStream(): Flow<AppEvent>

    /**
     * Like [eventStream] but without the one-event replay: a subscriber sees
     * only events dispatched *after* it attached. This is the stream for
     * **edge** semantics (re-fire triggers) — a replayed edge is by definition
     * stale and re-acting to it double-fires. Consumers that need the no-miss
     * guarantee for state activation should key off a level (e.g. an
     * `AppState` flag) instead, not off replayed edges.
     */
    fun liveEventStream(): Flow<AppEvent>
}

interface AppLifecycleObserver {
    fun onEnterForeground()
    fun onEnterBackground()
}

interface AppLifecycle {
    fun addObserver(observer: AppLifecycleObserver)
    fun removeObserver(observer: AppLifecycleObserver)
}


