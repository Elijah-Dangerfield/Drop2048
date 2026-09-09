package com.dangerfield.drop2048.libraries.ui.components.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The score roll takes longer for a bigger jump, and the HUD's copy of the number
 * is short enough to sit in a header.
 *
 * A merge pays 40 and a row burst pays four thousand (SPEC 7), so one duration
 * covering both means the burst blurs and the number simply appears to change,
 * which is precisely what the animation exists to avoid.
 *
 * These test the **shape** rather than the constants. A tuning pass should be able
 * to move the floor, the ceiling and the slope without rewriting the tests, and
 * still be caught if it makes the duration constant again or lets it run away.
 */
class ScoreCounterTest {

    @Test
    fun aBiggerJumpRollsForLonger() {
        val merge = countUpMillis(MERGE_POINTS)
        val burst = countUpMillis(BURST_POINTS)

        assertTrue(
            burst > merge,
            "a $BURST_POINTS point burst rolls for ${burst}ms and a $MERGE_POINTS point merge " +
                "for ${merge}ms, so the duration is not scaling",
        )
    }

    @Test
    fun aMergeStillFeelsImmediate() {
        assertTrue(
            countUpMillis(MERGE_POINTS) < IMMEDIATE_CEILING,
            "a merge rolls for ${countUpMillis(MERGE_POINTS)}ms, which reads as lag",
        )
    }

    @Test
    fun anEnormousJumpIsCapped() {
        val huge = countUpMillis(HUGE_POINTS)
        val absurd = countUpMillis(HUGE_POINTS * 1000)

        assertEquals(huge, absurd, "the duration keeps growing with the delta")
        assertTrue(absurd < HOLDS_THE_SCREEN, "${absurd}ms holds the screen too long")
    }

    /**
     * Score climbs during a run, but an undo (SPEC 5.6) hands the counter a
     * smaller number than it is holding. A negative delta computed as a duration
     * is a negative duration, which `tween` rejects at runtime rather than at
     * compile time. Cheap to make impossible.
     */
    @Test
    fun aDropRollsRatherThanFreezing() {
        assertTrue(countUpMillis(-BURST_POINTS) > 0, "a downward jump produced a non-positive duration")
        assertEquals(
            countUpMillis(BURST_POINTS),
            countUpMillis(-BURST_POINTS),
            "a jump down should roll for as long as the same jump up",
        )
    }

    /**
     * `ScoreCounter` returns early on a zero delta, so this never runs in
     * practice. It is here because zero is the one duration `tween` treats as
     * "snap", and a refactor that removed that early return would otherwise
     * silently kill the animation.
     */
    @Test
    fun noJumpStillHasAPositiveDuration() {
        assertTrue(countUpMillis(0) > 0)
    }

    /** `0.8K` is longer than `845` and less true. There is nothing to save below a thousand. */
    @Test
    fun aSmallScoreIsDrawnExactly() {
        assertEquals("845", abbreviateScore(845))
        assertEquals("0", abbreviateScore(0))
        assertEquals("999", abbreviateScore(999))
    }

    @Test
    fun thousandsCarryOneDecimal() {
        assertEquals("130.5K", abbreviateScore(130_450))
        assertEquals("1.2K", abbreviateScore(1_234))
        assertEquals("1K", abbreviateScore(1_000))
    }

    /** `12.0K` is a decimal place spent saying nothing. */
    @Test
    fun aWholeNumberDropsItsDecimal() {
        assertEquals("12K", abbreviateScore(12_000))
        assertEquals("12K", abbreviateScore(12_004), "the rounding stopped short of a whole number")
    }

    /**
     * The counter rolls between these two, and without a decimal place both would
     * read `130K` — a merge worth 400 points would animate between two identical
     * strings.
     */
    @Test
    fun theDecimalIsWhatKeepsTheRollWorthWatching() {
        assertNotEquals(abbreviateScore(130_050), abbreviateScore(130_450))
    }

    @Test
    fun millionsGetTheirOwnSuffix() {
        assertEquals("1.2M", abbreviateScore(1_234_567))
        assertEquals("1M", abbreviateScore(1_000_000))
    }

    /**
     * 999,999 rounds to `1000.0K`, which is longer than the number it is
     * abbreviating and reads as a bug.
     */
    @Test
    fun theTopOfTheThousandsPromotesRatherThanRoundingToFourDigits() {
        assertEquals("1M", abbreviateScore(999_999))
        assertEquals("999.9K", abbreviateScore(999_949), "the promotion reached too far down")
    }

    private companion object {
        /** What one 32-into-64 merge pays (SPEC 7). */
        const val MERGE_POINTS = 40

        /** About what a row burst pays, cascade multiplier included. */
        const val BURST_POINTS = 4_000

        const val HUGE_POINTS = 50_000

        /** Above this a per-drop animation reads as the app being slow. */
        const val IMMEDIATE_CEILING = 500

        /** Above this the player is waiting on an animation rather than watching one. */
        const val HOLDS_THE_SCREEN = 2000
    }
}
