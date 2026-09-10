package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two destructive controls, and the promises their copy makes.
 *
 * `Set<ClearableDao>` had no consumer for seven chunks; this is the test that
 * says the set is what does the wiping, rather than a hand-written list of the
 * tables somebody remembered.
 */
class PlayerDataEraserTest : CoroutineTest() {

    @Test
    fun `reset progress empties every table in the multibinding`() = runUnitTest {
        val a = RecordingDao()
        val b = RecordingDao()
        val c = RecordingDao()
        val eraser = PlayerDataEraser(setOf(a, b, c), FakeAppCache())

        eraser.resetProgress()

        assertEquals(1, a.cleared)
        assertEquals(1, b.cleared)
        assertEquals(1, c.cleared)
    }

    /**
     * A half-refused reset is the worst outcome available here — the player has
     * already confirmed twice and typed a word — so one failing table must not
     * stop the rest.
     */
    @Test
    fun `a table that refuses does not spare the others`() = runUnitTest {
        val failing = RecordingDao(failing = true)
        val healthy = RecordingDao()
        val eraser = PlayerDataEraser(setOf(failing, healthy), FakeAppCache())

        eraser.resetProgress()

        assertEquals(1, failing.cleared)
        assertEquals(1, healthy.cleared)
    }

    @Test
    fun `reset progress drops the run in flight and keeps everything else`() = runUnitTest {
        val cache = FakeAppCache(
            AppData(
                savedRun = "a board mid-cascade",
                installId = "install-1",
                hasUserOnboarded = true,
                acceptedTermsVersion = 3,
                legalAcceptedAt = 42L,
                blockPalette = "HighContrast",
            ),
        )
        PlayerDataEraser(setOf(RecordingDao()), cache).resetProgress()

        assertNull(cache.snapshot.savedRun)
        // Progress is what the player asked to lose. The install id, the legal
        // record and the accessibility choices are not progress, and
        // re-accepting the terms is not what they asked for.
        assertEquals("install-1", cache.snapshot.installId)
        assertEquals(3, cache.snapshot.acceptedTermsVersion)
        assertEquals(42L, cache.snapshot.legalAcceptedAt)
        assertEquals("HighContrast", cache.snapshot.blockPalette)
        assertTrue(cache.snapshot.hasUserOnboarded)
    }

    @Test
    fun `delete local data forgets the install and keeps the accessibility choices`() = runUnitTest {
        val cache = FakeAppCache(
            AppData(
                installId = "install-1",
                hasUserOnboarded = true,
                acceptedTermsVersion = 3,
                legalAcceptedAt = 42L,
                blockPalette = "Deuteranopia",
                reduceMotion = true,
                largeBlockNumbers = true,
                leftHandedControls = true,
            ),
        )
        val dao = RecordingDao()

        PlayerDataEraser(setOf(dao), cache).deleteLocalData()

        assertEquals(1, dao.cleared)
        assertNull(cache.snapshot.installId)
        assertEquals(0, cache.snapshot.acceptedTermsVersion)
        assertEquals(0L, cache.snapshot.legalAcceptedAt)
        // The tutorial runs again, which is the point of the control.
        assertEquals(false, cache.snapshot.hasUserOnboarded)
        // Resetting the palette out from under somebody mid-tap on "Delete"
        // would read as a bug rather than as a deletion.
        assertEquals("Deuteranopia", cache.snapshot.blockPalette)
        assertTrue(cache.snapshot.reduceMotion)
        assertTrue(cache.snapshot.largeBlockNumbers)
        assertTrue(cache.snapshot.leftHandedControls)
    }
}
