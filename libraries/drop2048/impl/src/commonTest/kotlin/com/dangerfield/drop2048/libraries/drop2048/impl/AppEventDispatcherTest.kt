package com.dangerfield.drop2048.libraries.drop2048.impl

import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.drop2048.AppEventListener
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycle
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycleObserver
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the contract every explicit-event listener relies on:
 *  - dispatch(ConnectivityRegained) fans out to every listener's
 *    onConnectivityRegained.
 *  - Listener exceptions don't poison subsequent listeners — the
 *    dispatcher's Catching{} wrap keeps the loop going.
 *  - dispatch is synchronous on the calling thread (we don't post to
 *    a queue), so the AppEventBus contract caller can reason about
 *    ordering vs. its own work.
 *
 * Lifecycle (foreground/cold-boot dispatch) isn't tested here because
 * it's driven via the AppLifecycle observer and the test would just
 * mirror that wiring. ConnectivityRegained is the path we explicitly call
 * via AppEventBus, so that's what's worth pinning.
 */
class AppEventDispatcherTest : CoroutineTest() {

    @Test
    fun connectivityRegained_fansOutToEveryListener() {
        val a = Recording()
        val b = Recording()
        val dispatcher = AppEventDispatcher(
            listeners = setOf(a, b),
            appLifecycle = NoopLifecycle,
        )

        dispatcher.dispatch(AppEvent.ConnectivityRegained)

        assertEquals(listOf("connectivityRegained"), a.calls)
        assertEquals(listOf("connectivityRegained"), b.calls)
    }

    @Test
    fun listenerException_doesNotBlockOtherListeners() {
        val poison = Throwing()
        val healthy = Recording()
        val dispatcher = AppEventDispatcher(
            listeners = setOf(poison, healthy),
            appLifecycle = NoopLifecycle,
        )

        dispatcher.dispatch(AppEvent.ConnectivityRegained)

        // The healthy listener still ran — exact ordering across the
        // set isn't part of the contract (Set is unordered), but
        // "every non-throwing listener fires" is.
        assertEquals(listOf("connectivityRegained"), healthy.calls)
    }

    @Test
    fun eventStream_emitsEachDispatchedEvent_inOrder() = runUnitTest {
        val dispatcher = AppEventDispatcher(listeners = emptySet(), appLifecycle = NoopLifecycle)
        val seen = mutableListOf<AppEvent>()
        backgroundScope.launch { dispatcher.eventStream().collect { seen += it } }

        dispatcher.dispatch(AppEvent.ConnectivityRegained)
        dispatcher.dispatch(AppEvent.OnForeground(isColdBoot = false))

        assertEquals(
            listOf(
                AppEvent.ConnectivityRegained,
                AppEvent.OnForeground(isColdBoot = false),
            ),
            seen,
        )
    }

    @Test
    fun eventStream_and_listeners_seeTheSameEvent() = runUnitTest {
        val listener = Recording()
        val dispatcher = AppEventDispatcher(listeners = setOf(listener), appLifecycle = NoopLifecycle)
        val seen = mutableListOf<AppEvent>()
        backgroundScope.launch { dispatcher.eventStream().collect { seen += it } }

        dispatcher.dispatch(AppEvent.ConnectivityRegained)

        assertEquals(listOf("connectivityRegained"), listener.calls)
        assertEquals(listOf<AppEvent>(AppEvent.ConnectivityRegained), seen)
    }

    private class Recording : AppEventListener {
        val calls = mutableListOf<String>()
        override fun onColdBoot(event: AppEvent.ColdBoot) { calls += "coldBoot" }
        override fun onWarmBoot(event: AppEvent.WarmBoot) { calls += "warmBoot" }
        override fun onForeground(event: AppEvent.OnForeground) { calls += "foreground" }
        override fun onBackground(event: AppEvent.OnBackground) { calls += "background" }
        override fun onConnectivityRegained(event: AppEvent.ConnectivityRegained) {
            calls += "connectivityRegained"
        }
    }

    private class Throwing : AppEventListener {
        override fun onConnectivityRegained(event: AppEvent.ConnectivityRegained) {
            throw IllegalStateException("simulated listener failure")
        }
    }

    private object NoopLifecycle : AppLifecycle {
        override fun addObserver(observer: AppLifecycleObserver) { /* no-op */ }
        override fun removeObserver(observer: AppLifecycleObserver) { /* no-op */ }
    }
}
