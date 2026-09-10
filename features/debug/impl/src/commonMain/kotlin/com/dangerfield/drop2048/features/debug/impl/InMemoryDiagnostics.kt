package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.features.debug.Diagnostics
import com.dangerfield.drop2048.features.debug.DiagnosticsSettings
import com.dangerfield.drop2048.features.debug.TickSample
import com.dangerfield.drop2048.libraries.cascade.Transcript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The overlay's switches and the last transcript, in memory.
 *
 * [record] is called from the game on every resolution whether or not the
 * overlay is on. That is a deliberate cost of one reference assignment per drop:
 * the alternative is a game that only remembers what happened when somebody was
 * already watching, which is useless the one time it matters — the merge that
 * looked wrong has already happened by the time anyone reaches for the switch.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class InMemoryDiagnostics : Diagnostics {

    private val _settings = MutableStateFlow(DiagnosticsSettings.Off)
    override val settings: StateFlow<DiagnosticsSettings> = _settings.asStateFlow()

    private val _lastResolution = MutableStateFlow(Transcript.Empty)
    override val lastResolution: StateFlow<Transcript> = _lastResolution.asStateFlow()

    private val _tick = MutableStateFlow(TickSample())
    override val tick: StateFlow<TickSample> = _tick.asStateFlow()

    override suspend fun update(transform: (DiagnosticsSettings) -> DiagnosticsSettings) {
        _settings.update(transform)
    }

    override fun record(transcript: Transcript) {
        if (transcript.isEmpty) return
        _lastResolution.value = transcript
    }

    override fun recordTick(intendedMs: Long, actualMs: Long) {
        _tick.value = TickSample(intendedMs = intendedMs, actualMs = actualMs)
    }
}
