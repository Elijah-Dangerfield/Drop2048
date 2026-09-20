package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.drop2048.AppData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The codec, against the real thing.
 *
 * `GameState` is a sealed hierarchy of blocks inside a board inside a config,
 * and a transcript is a sealed hierarchy of steps. Nothing about that is checked
 * by the compiler at the point it is written to disk, so this round-trips an
 * actual mid-cascade snapshot rather than a hand-built one.
 */
class SavedRunStoreTest {

    @Test
    fun aMidCascadeSnapshot_survivesTheRoundTrip() = runTest {
        val cache = FakeAppCache()
        val store = AppCacheSavedRunStore(cache)
        val saved = midCascadeSnapshot()

        store.save(saved)

        assertNotNull(cache.snapshot.savedRun, "something was written")
        assertEquals(saved, store.load())
    }

    @Test
    fun clear_leavesNothingToResume() = runTest {
        val cache = FakeAppCache()
        val store = AppCacheSavedRunStore(cache)
        store.save(midCascadeSnapshot())

        store.clear()

        assertNull(store.load())
        assertNull(cache.snapshot.savedRun)
    }

    /**
     * The whole reason `AppData.savedRun` is a string. A blob written by a build
     * whose engine had a different shape costs the player that run and nothing
     * else — the install id and the onboarding flag beside it are untouched.
     */
    @Test
    fun anUnreadableBlob_readsAsNoSavedRun() = runTest {
        val cache = FakeAppCache(AppData(savedRun = "{\"state\":\"this is not a GameState\"}"))

        assertNull(AppCacheSavedRunStore(cache).load())
        assertEquals("{\"state\":\"this is not a GameState\"}", cache.snapshot.savedRun)
    }

    /**
     * The real upgrade path, and the one a device run cannot surface: someone has
     * the C4 build installed with a run in progress, takes the D11 update, and
     * opens the app.
     *
     * That blob is not garbage. It is a **well-formed** `SavedRun` from a build
     * whose `GameState` still had `preview`, `hold` and `holdUsedThisDrop`, and
     * with `ignoreUnknownKeys` it would decode without complaint into the new
     * shape. It has to be refused anyway — see [SavedRun]'s KDoc — and the thing
     * that refuses it is the absent `version` field.
     *
     * Written out by hand rather than generated, because the point is that this
     * exact text is what is sitting in someone's `AppData` right now and no build
     * from here on can produce it again.
     *
     * The second half of the test is what keeps the first half honest. Refusing a
     * blob is easy to do by accident — any malformed field would also produce
     * null, and the test would pass while proving nothing. So the same bytes are
     * loaded again with nothing changed but a `version` spliced in, and that one
     * *must* resume. The only difference between the two runs is the version, so
     * the version is what did the refusing.
     */
    @Test
    fun aBlobFromTheBuildBeforeTheRulingIsRefusedRatherThanResumed() = runTest {
        val cache = FakeAppCache(AppData(savedRun = PRE_RULING_BLOB))

        assertNull(
            AppCacheSavedRunStore(cache).load(),
            "a saved run written against the old GameState was resumed into the new rules",
        )
        assertEquals(PRE_RULING_BLOB, cache.snapshot.savedRun, "and nothing else in AppData was touched")

        val versioned = PRE_RULING_BLOB.replaceFirst("{", "{\"version\":$SAVE_FORMAT_VERSION,")
        val resumed = AppCacheSavedRunStore(FakeAppCache(AppData(savedRun = versioned))).load()

        assertNotNull(resumed, "the blob is well-formed old data, not garbage")
        assertEquals(1_240L, resumed.state.score, "and the version is the only thing that refused it")
    }

    /**
     * The same upgrade path one release later: someone has the nudge build
     * installed with a run in progress and takes the D21 update.
     *
     * This blob is the dangerous shape rather than the obviously broken one. It
     * is a **version 4 `SavedRun`** whose `EngineConfig` carries `nudgeRows` and
     * `speed.softDropMsPerRow` and does not carry `scoring.hardDropPerRow`. With
     * `ignoreUnknownKeys` it decodes perfectly: the two dead keys are dropped and
     * the new one takes its compiled-in default, so the run would resume under a
     * scoring table it was never played under with nothing on screen to say so.
     * Only the version number can catch it.
     *
     * Positive control (L35), and it is the whole point of the test: the same
     * bytes are loaded again with **only** the version changed from 4 to 5, and
     * that one must resume with its score intact. Without it this would pass on
     * any malformed field and prove only that something was wrong with the blob.
     */
    @Test
    fun aBlobFromTheNudgeBuildIsRefusedRatherThanResumed() = runTest {
        val cache = FakeAppCache(AppData(savedRun = NUDGE_ERA_BLOB))

        assertNull(
            AppCacheSavedRunStore(cache).load(),
            "a run saved under the nudge's scoring table was resumed under D21's",
        )

        val current = NUDGE_ERA_BLOB.replaceFirst("\"version\":4", "\"version\":$SAVE_FORMAT_VERSION")
        val resumed = AppCacheSavedRunStore(FakeAppCache(AppData(savedRun = current))).load()

        assertNotNull(resumed, "the blob is well-formed old data, not garbage")
        assertEquals(3_050L, resumed.state.score, "and the version is the only thing that refused it")
    }

    /** A version this build has never heard of is refused the same way. */
    @Test
    fun aBlobFromAFutureFormatIsRefused() = runTest {
        val cache = FakeAppCache()
        val store = AppCacheSavedRunStore(cache)
        store.save(midCascadeSnapshot().copy(version = SAVE_FORMAT_VERSION + 1))

        assertNull(store.load())
    }

    private companion object {
        /**
         * A real `SavedRun` in the shape C4 shipped: a `GameState` with
         * `preview`, `hold` and `holdUsedThisDrop`, and no `version` anywhere.
         *
         * Level 3, 47 blocks in, mid-run with a 2 resting in the bottom row —
         * a run somebody would mind losing, which is the case worth being sure
         * about.
         */
        const val PRE_RULING_BLOB = "{\"state\":{\"board\":{\"cols\":5,\"rows\":8,\"cells\":[" +
            "null,null,null,null,null,null,null,null,null,null,null,null,null,null," +
            "null,null,null,null,null,null,null,null,null,null,null,null,null,null," +
            "null,null,null,null,null,null,null,null,null," +
            "{\"type\":\"number\",\"value\":\"V2\"},null,null]}," +
            "\"falling\":{\"block\":{\"type\":\"number\",\"value\":\"V4\"}," +
            "\"cell\":{\"col\":2,\"row\":1},\"lastDirection\":null}," +
            "\"preview\":[{\"type\":\"number\",\"value\":\"V2\"}," +
            "{\"type\":\"special\",\"special\":\"WILDCARD\"}]," +
            "\"hold\":{\"type\":\"number\",\"value\":\"V8\"},\"holdUsedThisDrop\":true," +
            "\"score\":1240,\"level\":3,\"blocksDropped\":47,\"drawsMade\":49," +
            "\"rng\":{\"state\":-8123456789012345678},\"lastDrawWasSpecial\":false," +
            "\"status\":\"PLAYING\",\"inDanger\":false}," +
            "\"tally\":{\"merges\":12,\"bursts\":0,\"longestCascade\":3,\"highestTier\":64," +
            "\"playedMs\":91000},\"seed\":99,\"mode\":\"ENDLESS\"}"

        /**
         * A version 4 `SavedRun` with the nudge era's `EngineConfig` in it.
         *
         * Trimmed to the keys that changed — a real blob spells out the whole
         * spawn table and speed curve, and every field elided here has a default
         * that decodes to the same value the shipping build wrote. What is *not*
         * trimmed is the point: `nudgeRows` and `speed.softDropMsPerRow` are keys
         * this build no longer has, and `scoring.hardDropPerRow` is one it now
         * needs. The blob decodes cleanly regardless, which is why the version
         * has to be the thing that stops it.
         */
        const val NUDGE_ERA_BLOB = "{\"version\":4,\"state\":{" +
            "\"config\":{\"nudgeRows\":2,\"speed\":{\"softDropMsPerRow\":40}}," +
            "\"board\":{\"cols\":5,\"rows\":8,\"cells\":[" +
            "null,null,null,null,null,null,null,null,null,null,null,null,null,null," +
            "null,null,null,null,null,null,null,null,null,null,null,null,null,null," +
            "null,null,null,null,null,null,null,{\"type\":\"number\",\"value\":\"V8\"}," +
            "{\"type\":\"number\",\"value\":\"V2\"},null,null,null]}," +
            "\"falling\":{\"block\":{\"type\":\"number\",\"value\":\"V4\"}," +
            "\"cell\":{\"col\":2,\"row\":2},\"lastDirection\":null}," +
            "\"score\":3050,\"level\":5,\"blocksDropped\":88,\"drawsMade\":89," +
            "\"rng\":{\"state\":-4477112233445566778},\"lastDrawWasSpecial\":false," +
            "\"status\":\"PLAYING\",\"inDanger\":false}," +
            "\"tally\":{\"merges\":41,\"bursts\":1,\"longestCascade\":4,\"highestTier\":256," +
            "\"playedMs\":204000},\"seed\":4242,\"mode\":\"ENDLESS\"}"
    }

    private fun midCascadeSnapshot(): SavedRun {
        val start = Cascade.newGame(seed = 99).let { state ->
            state.copy(board = state.board.with(Cell(state.config.centreColumn, state.config.rows - 1), NumberBlock(BlockValue.V2)))
        }
        val transition = Cascade.apply(
            start.copy(falling = start.falling?.copy(block = NumberBlock(BlockValue.V2))),
            Input.Lock,
        )
        return SavedRun(
            version = SAVE_FORMAT_VERSION,
            state = transition.state,
            tally = RunTally(merges = 3, bursts = 1, longestCascade = 4, highestTier = 64, playedMs = 91_000),
            seed = 99,
            resolution = SavedResolution(
                beforeBoard = start.board,
                scoreBefore = start.score,
                transcript = transition.transcript,
                frameIndex = 1,
            ),
        )
    }
}
