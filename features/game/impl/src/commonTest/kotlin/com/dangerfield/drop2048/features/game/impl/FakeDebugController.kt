package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.debug.DebugController
import com.dangerfield.drop2048.features.debug.DebugOverrides
import com.dangerfield.drop2048.libraries.cascade.Block
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * A debug menu somebody has already used.
 *
 * `NoDebugController` in `:features:debug` covers "the menu was never opened",
 * which is every other test in this module. This is the other half, and it exists
 * so the guard that keeps QA out of `run_record` is checked by something rather
 * than trusted.
 */
class FakeDebugController(
    initial: DebugOverrides = DebugOverrides.None,
    debugSession: Boolean = true,
) : DebugController {

    private val _overrides = MutableStateFlow(initial)
    override val overrides: StateFlow<DebugOverrides> = _overrides

    private val _isDebugSession = MutableStateFlow(debugSession)
    override val isDebugSession: StateFlow<Boolean> = _isDebugSession

    var markCount: Int = 0
        private set

    override fun markDebugSession() {
        markCount += 1
        _isDebugSession.value = true
    }

    override suspend fun update(transform: (DebugOverrides) -> DebugOverrides) {
        _overrides.update(transform)
    }

    override fun takeForcedBlock(): Block? {
        var taken: Block? = null
        _overrides.update { current ->
            taken = current.forcedBlocks.firstOrNull()
            if (taken == null) current else current.copy(forcedBlocks = current.forcedBlocks.drop(1))
        }
        return taken
    }
}
