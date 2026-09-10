package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.cascade.Transcript
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * What the in-game diagnostics overlay draws (SPEC 19).
 *
 * Separate from [DebugOverrides] because none of it changes the game. An overlay
 * that is only ever read cannot make a run unrepresentative, so it does not need
 * to travel with a starting state and does not want to be switched off when the
 * overrides are cleared.
 */
@Serializable
data class DiagnosticsSettings(
    /** Frames per second and frame time, sampled in `drawBehind`. */
    val showFrameRate: Boolean = false,

    /** Measured milliseconds per row against what SPEC 5.5's curve asked for. */
    val showTick: Boolean = false,

    /** Column and row printed on every cell. */
    val showCellCoordinates: Boolean = false,

    /** SPEC 4.3's priority order, drawn on the falling block. */
    val showMergeArrows: Boolean = false,

    /**
     * The last resolution, step by step, scrollable.
     *
     * The valuable half of the whole menu: the engine already returns a
     * transcript (SPEC 4.2), so this is a formatter over data that exists rather
     * than a new system, and it is the fastest way to answer "why did that merge
     * go left".
     */
    val showTranscript: Boolean = false,
) {
    val anythingOn: Boolean
        get() = showFrameRate || showTick || showCellCoordinates || showMergeArrows || showTranscript

    companion object {
        val Off = DiagnosticsSettings()
    }
}

/**
 * The overlay's two inputs: what to draw, and the last thing the engine
 * resolved.
 *
 * [lastResolution] is written by the game as each resolution starts and read by
 * the overlay. It is a `StateFlow` of the engine's own [Transcript] rather than
 * of formatted lines, so the formatter ([describe]) stays pure and testable and
 * the overlay stays the only thing that knows about strings.
 */
interface Diagnostics {
    val settings: StateFlow<DiagnosticsSettings>

    val lastResolution: StateFlow<Transcript>

    val tick: StateFlow<TickSample>

    suspend fun update(transform: (DiagnosticsSettings) -> DiagnosticsSettings)

    fun record(transcript: Transcript)

    fun recordTick(intendedMs: Long, actualMs: Long)
}

/**
 * What SPEC 5.5's curve asked for against what the player actually got.
 *
 * Measured in the ViewModel's ticker, which is the only place that knows both
 * numbers: the interval it asked `delay` for, and the wall time that elapsed. A
 * gap between them is the whole reason SPEC 19 asks for this — a drop timer that
 * has drifted feels like a difficulty bug and reads like a rendering one.
 */
data class TickSample(val intendedMs: Long = 0, val actualMs: Long = 0)
