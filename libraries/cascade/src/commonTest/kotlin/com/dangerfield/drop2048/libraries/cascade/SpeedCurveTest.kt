package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC 5.5's table. The engine never reads this — it stores the level and the
 * ViewModel asks how long a row takes — but the numbers ship from here so there
 * is one copy of them, and the floor is asserted because SPEC 5.5 calls it
 * load-bearing: without it the game stops being a puzzle and becomes a reflex
 * test.
 *
 * Levels 1-8 were re-cut once, in C1c, on the balance harness's clocked
 * measurement of the opening. Because the curve lives here and not in the run,
 * that change did **not** move `DeterminismTest`'s pin — confirmed by making it
 * and re-running the suite, not by assuming. `blocksPerLevel` is the other half
 * of SPEC 5.5 and does not have that property: it feeds level advancement, so
 * moving it moves the digest and every recorded score with it.
 */
class SpeedCurveTest {

    private val curve = SpeedCurve.Default

    @Test
    fun theTableMatchesTheSpec() {
        assertEquals(500, curve.msPerRow(1))
        assertEquals(listOf(470, 440, 410), (2..4).map(curve::msPerRow))
        assertEquals(listOf(380, 350, 325, 300), (5..8).map(curve::msPerRow))
        assertEquals(listOf(270, 245, 220, 200), (9..12).map(curve::msPerRow))
        assertEquals(listOf(185, 170, 158, 148), (13..16).map(curve::msPerRow))
        assertEquals(listOf(140, 133, 127, 122), (17..20).map(curve::msPerRow))
        assertEquals(118, curve.msPerRow(21))
    }

    @Test
    fun pastLevelTwentyOneItDropsTwoPerLevelAndThenStops() {
        assertEquals(116, curve.msPerRow(22))
        assertEquals(100, curve.msPerRow(30))
        assertEquals(90, curve.msPerRow(35))
        assertEquals(90, curve.msPerRow(200), "the floor is load-bearing")
    }

    @Test
    fun theCurveNeverGoesBackwards() {
        val readings = (1..300).map(curve::msPerRow)
        assertTrue(readings.zipWithNext().all { (a, b) -> b <= a })
    }
}
