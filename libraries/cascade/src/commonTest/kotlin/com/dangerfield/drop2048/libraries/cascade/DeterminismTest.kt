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
 *
 * Re-pinned once, 2026-09-09, when SPEC 4.3's merge position was ruled to be the
 * partner's cell in both orientations. Merge outcomes moved, so the pin had to
 * move with them; it was re-derived from the engine, not from the old numbers.
 * No scores existed yet. Every later change to these constants needs the same
 * kind of written reason.
 *
 * Re-pinned a second time, 2026-09-09, for decision D11. Three things moved the
 * digest at once and they shipped together so it only had to move once:
 *
 * 1. `Input` lost `HardDrop` and `Hold` and gained `Nudge`, so the scripted
 *    player below plays a different, shorter run.
 * 2. SPEC 7's `2 x rowsSkipped` hard drop bonus was struck and the nudge did not
 *    inherit it, so the score is lower and `HardDropBonus` no longer appears in
 *    any transcript.
 * 3. `GameState` lost `preview`, `hold` and `holdUsedThisDrop`, and the spawn
 *    draw moved from enqueue time to spawn time — so both the serialized shape
 *    and the RNG stream's alignment with the board changed.
 *
 * Re-derived by running the engine and reading the values out, not by copying
 * the "actual" out of the assertion failure. Those look identical in a diff and
 * are not: pasting the actual makes the test agree with whatever the code now
 * does, which is the same as deleting it. Still no recorded scores and no Daily
 * Challenge, which is the only reason a second re-pin is affordable at all.
 *
 * `862 / 25 drops` became `592 / 22 drops`, and the arithmetic is the check that
 * the re-derivation is the right number rather than merely a number: 25 drops at
 * roughly six skipped rows was about 300 points of hard drop bonus, and 300 is
 * most of the 270-point fall. The rest is three fewer drops, because the run
 * plays out differently once the draw moves to spawn time.
 *
 * Re-pinned a **third** time, 2026-09-10, for decision D21. ▼ became a hard drop
 * and soft drop was deleted, so `Input` lost `Nudge` and SPEC 7's
 * `2 x rowsSkipped` bonus came back with the input that pays it. Re-derived by
 * running the engine and reading the values out, exactly as the second re-pin
 * was, and not by copying the "actual" out of the assertion failure (L17). Still
 * **no recorded scores and no Daily Challenge result anywhere**, which is the
 * only reason a third re-pin is affordable — D18 freezes `EngineConfig.Default`
 * from the first Daily score, so this is the last cheap moment.
 *
 * `592 / 22 drops` became `772 / 22 drops`, and this time the arithmetic is
 * unusually tight. The drop count, the level and every board outcome are
 * **unchanged**, because vertical position is not an input to the engine (L40)
 * and the only script change swapped `Nudge` for `Tick` — two controls that move
 * the block down and nothing else. So the entire 180-point difference is the
 * reinstated bonus: 90 skipped rows at two points, or 4.1 rows a drop across 22
 * drops, which is what a script that locks from wherever it happens to be should
 * average on an eight-row board.
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
     *
     * The weights have kept the same shape through both re-pins, with removed
     * inputs replaced in place rather than the distribution being rewritten:
     * `Hold` became `Nudge` in D11 and `Nudge` became `Tick` in D21, while
     * `HardDrop` has been `Lock` throughout because D21 made that literally true
     * again. A comparable run is what makes the arithmetic below a check on the
     * new pin rather than a restatement of it.
     */
    private fun scriptedInputs(seed: Long, count: Int): List<Input> {
        var rng = Rng(seed)
        return (0 until count).map {
            rng = rng.next()
            when (rng.valueIn(8)) {
                0, 1 -> Input.MoveLeft
                2, 3 -> Input.MoveRight
                4, 5 -> Input.Tick
                else -> Input.Lock
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

        const val PINNED_SCORE = 772L
        const val PINNED_BLOCKS_DROPPED = 22
        const val PINNED_LEVEL = 2
        const val PINNED_DIGEST = 931_206_270_441_098_336L
    }
}
