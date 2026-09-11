package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.features.debug.DebugController
import com.dangerfield.drop2048.features.debug.DebugOverrides
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.core.DebugSessionFlag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The debug menu's state, in memory and nowhere else.
 *
 * **Deliberately not persisted**, unlike the unlock flag beside it in
 * `PlayerSettings`. A forced seed that survived a relaunch would be the worst
 * kind of bug report — a tester says "the board is always the same" and nothing
 * on any screen explains why. Relaunching is the reset, and it is the only reset
 * that cannot itself be forgotten.
 *
 * That also makes [isDebugSession] honest: it latches for the life of the
 * process because that is exactly as long as anything it did can still be in
 * flight.
 *
 * No `AutoInit` marker. `AGENTS.md` names debug-only QA singletons as the case
 * to skip it: there is no `init {}` here to run, nothing to hydrate, and forcing
 * this to construct at boot would put a menu nobody opened on the warm path.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class InMemoryDebugController : DebugController {

    private val _overrides = MutableStateFlow(DebugOverrides.None)
    override val overrides: StateFlow<DebugOverrides> = _overrides.asStateFlow()

    private val _isDebugSession = MutableStateFlow(false)
    override val isDebugSession: StateFlow<Boolean> = _isDebugSession.asStateFlow()

    override fun markDebugSession() {
        _isDebugSession.value = true
    }

    override suspend fun update(transform: (DebugOverrides) -> DebugOverrides) {
        _overrides.update(transform)
    }

    /**
     * Pops the head of the queue.
     *
     * `update` on a `MutableStateFlow` retries its lambda under contention, so
     * reading the head and dropping it in two statements could hand the same
     * block out twice. The value is captured from the result of one atomic
     * update instead.
     */
    override fun takeForcedBlock(): Block? {
        var taken: Block? = null
        _overrides.update { current ->
            taken = current.forcedBlocks.firstOrNull()
            if (taken == null) current else current.copy(forcedBlocks = current.forcedBlocks.drop(1))
        }
        return taken
    }
}

/**
 * The library-side view of [InMemoryDebugController]'s latch, so
 * `GrafanaLogTree` can stamp `debug_session` on every exported record without
 * `:libraries:telemetry:impl` depending on a feature (L63, SPEC 17).
 *
 * A separate class rather than a second interface on the controller because
 * `DebugController.isDebugSession` is a `StateFlow` and
 * [DebugSessionFlag.isDebugSession] is a plain read — one name, two types, and
 * Kotlin will not let one class own both. The plain read is what the logging
 * path wants: it runs on every event and has nothing to collect into.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DebugSessionLatch(
    private val controller: DebugController,
) : DebugSessionFlag {
    override val isDebugSession: Boolean get() = controller.isDebugSession.value
}
