package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.progress.GameMode
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

    private fun midCascadeSnapshot(): SavedRun {
        val start = Cascade.newGame(seed = 99).let { state ->
            state.copy(board = state.board.with(Cell(state.config.spawnColumn, state.config.rows - 1), NumberBlock(BlockValue.V2)))
        }
        val transition = Cascade.apply(
            start.copy(falling = start.falling?.copy(block = NumberBlock(BlockValue.V2))),
            Input.HardDrop,
        )
        return SavedRun(
            state = transition.state,
            tally = RunTally(merges = 3, bursts = 1, longestCascade = 4, highestTier = 64, playedMs = 91_000),
            seed = 99,
            mode = GameMode.ENDLESS,
            resolution = SavedResolution(
                beforeBoard = start.board,
                scoreBefore = start.score,
                transcript = transition.transcript,
                frameIndex = 1,
            ),
        )
    }
}
