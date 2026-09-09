package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The test that protects Daily Challenge, undo, save/resume, the balance
 * harness, replay and every bug report (SPEC 4.1).
 *
 * Comparing two runs inside one process only proves the engine is not reading a
 * clock. What has to hold is stronger: the **same bytes on every platform**. So
 * the run is serialized and reduced to a digest that is pinned as a constant
 * here. This file is in `commonTest`, so the same constants are asserted on
 * JVM, Android and iOS — a platform whose `Long` arithmetic, iteration order or
 * enum ordinals diverged would fail here and nowhere else.
 *
 * If one of these constants has to change, that is a **breaking change to every
 * recorded Daily Challenge score**, not a test fixup. Read SPEC 10's "never
 * remote" list before touching it.
 */
class DeterminismTest {

    private val json = Json { prettyPrint = false }

    @Test
    fun splitmix64ProducesItsPublishedStreamForSeedZero() {
        var rng = Rng(0)
        val stream = (0 until 3).map {
            rng = rng.next()
            rng.value()
        }

        assertEquals(
            listOf(-0x1DDF57C684E23251L, 0x6E789E6AA1B965F4L, 0x06C45D188009454FL),
            stream,
        )
    }

    @Test
    fun theSameSeedAndInputsProduceByteIdenticalState() {
        val inputs = scriptedInputs(seed = SCRIPT_SEED, count = 600)
        val first = Cascade.play(Cascade.newGame(RUN_SEED), inputs)
        val second = Cascade.play(Cascade.newGame(RUN_SEED), inputs)

        assertEquals(json.encodeToString(first), json.encodeToString(second))
    }

    @Test
    fun aPinnedRunHasTheSameDigestOnEveryPlatform() {
        val inputs = scriptedInputs(seed = SCRIPT_SEED, count = 600)
        val finished = Cascade.play(Cascade.newGame(RUN_SEED), inputs)

        assertEquals(PINNED_SCORE, finished.score, "score drifted")
        assertEquals(PINNED_BLOCKS_DROPPED, finished.blocksDropped, "block count drifted")
        assertEquals(PINNED_LEVEL, finished.level, "level drifted")
        assertEquals(PINNED_DIGEST, digest(json.encodeToString(finished)), "serialized state drifted")
    }

    @Test
    fun aRunResumedFromItsSerializedFormContinuesIdentically() {
        val inputs = scriptedInputs(seed = SCRIPT_SEED, count = 600)
        val halfway = Cascade.play(Cascade.newGame(RUN_SEED), inputs.take(200))
        val resumed = json.decodeFromString<GameState>(json.encodeToString(halfway))

        val straightThrough = Cascade.play(halfway, inputs.drop(200))
        val afterReload = Cascade.play(resumed, inputs.drop(200))

        assertEquals(json.encodeToString(straightThrough), json.encodeToString(afterReload))
    }

    @Test
    fun differentSeedsProduceDifferentRuns() {
        val inputs = scriptedInputs(seed = SCRIPT_SEED, count = 200)
        val a = Cascade.play(Cascade.newGame(RUN_SEED), inputs)
        val b = Cascade.play(Cascade.newGame(RUN_SEED + 1), inputs)

        assertTrue(json.encodeToString(a) != json.encodeToString(b), "the seed has to matter")
    }

    /**
     * A deliberately dumb scripted player, generated from the engine's own RNG so
     * the script needs no platform randomness of its own.
     */
    private fun scriptedInputs(seed: Long, count: Int): List<Input> {
        var rng = Rng(seed)
        return (0 until count).map {
            rng = rng.next()
            when (rng.valueIn(8)) {
                0, 1 -> Input.MoveLeft
                2, 3 -> Input.MoveRight
                4 -> Input.Tick
                5 -> Input.Hold
                else -> Input.HardDrop
            }
        }
    }

    /** FNV-1a over UTF-16 code units. Small, exact, and identical on every target. */
    private fun digest(text: String): Long {
        var hash = FNV_OFFSET
        text.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }

    private companion object {
        const val RUN_SEED = 20_480_909L
        const val SCRIPT_SEED = 1_337L
        const val FNV_OFFSET = -0x340d631b7bdddcdbL
        const val FNV_PRIME = 0x100000001b3L

        const val PINNED_SCORE = 942L
        const val PINNED_BLOCKS_DROPPED = 27
        const val PINNED_LEVEL = 2
        const val PINNED_DIGEST = 1_066_253_885_288_734_400L
    }
}
