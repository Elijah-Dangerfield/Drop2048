package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

/**
 * splitmix64, carried inside [GameState] rather than injected (SPEC 4.1).
 *
 * A seed plus a sequence of [Input]s has to determine an entire run byte for
 * byte on every platform, because six features depend on it: Daily Challenge,
 * undo, save/resume, the balance harness, replay, and reproducible bug reports.
 * An injected `Random` breaks all six the moment anything reorders a draw.
 *
 * splitmix64 is chosen because its whole state is one `Long` — it serializes
 * as a single field and every platform Kotlin targets has identical 64-bit
 * arithmetic, so there is no chance of a platform-specific stream.
 *
 * Usage is always advance-then-read: `rng = rng.next()` and then `rng.value()`.
 * The seed itself is never an output.
 */
@Serializable
data class Rng(val state: Long) {

    fun next(): Rng = Rng(state + GAMMA)

    fun value(): Long {
        var z = state
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        return z xor (z ushr 31)
    }

    /** A value in `0 until bound`, deterministic on every platform. */
    fun valueIn(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        val raw = value() % bound
        return ((raw + bound) % bound).toInt()
    }

    private companion object {
        const val GAMMA = -0x61c8864680b583ebL
        const val MIX_A = -0x40a7b892e31b1a47L
        const val MIX_B = -0x6b2fb644ecceee15L
    }
}
