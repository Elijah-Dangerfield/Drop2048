package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Transcript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [DebugController] that has never been opened, and never will be.
 *
 * Every consumer of these seams — the run factory, the game's clock, the run
 * recorder — has to answer "and what if the debug menu is not involved", which is
 * the case in every test and on every device that never taps the version number
 * seven times. Shipping the inert answer beside the interface is the same move
 * `AlwaysReadyAuthGate` and `NoOpAuthTokenProvider` make in `:libraries:core`,
 * and it means a test does not hand-roll a fake for a seam whose whole content is
 * "no".
 *
 * Deliberately **not** contributed to the DI graph. The real bindings are in
 * `:features:debug:impl` and a second binding here would be a graph conflict at
 * best and a silently disabled debug menu at worst.
 */
object NoDebugController : DebugController {
    override val overrides: StateFlow<DebugOverrides> = MutableStateFlow(DebugOverrides.None)
    override val isDebugSession: StateFlow<Boolean> = MutableStateFlow(false)
    override fun markDebugSession() = Unit
    override suspend fun update(transform: (DebugOverrides) -> DebugOverrides) = Unit
    override fun takeForcedBlock(): Block? = null
}

/** [Diagnostics] with the overlay off and nothing remembered. */
object NoDiagnostics : Diagnostics {
    override val settings: StateFlow<DiagnosticsSettings> = MutableStateFlow(DiagnosticsSettings.Off)
    override val lastResolution: StateFlow<Transcript> = MutableStateFlow(Transcript.Empty)
    override val tick: StateFlow<TickSample> = MutableStateFlow(TickSample())
    override suspend fun update(transform: (DiagnosticsSettings) -> DiagnosticsSettings) = Unit
    override fun record(transcript: Transcript) = Unit
    override fun recordTick(intendedMs: Long, actualMs: Long) = Unit
}
