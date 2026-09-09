package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.SpeedCurve

/**
 * Named speed curves the harness can be pointed at with `--curve`.
 *
 * C3 played the shipped screen and reported that levels 1-3 are slack — that the
 * timer, not the player, places the early blocks. That is a hypothesis from one
 * player over ninety seconds, and these are what turned it into a measurement.
 *
 * Every alternative here **rejoins [Original] at level 8** and is identical from
 * level 9 on. The complaint was about the opening, and a curve that also moved
 * the middle would answer two questions with one number.
 *
 * [Fast500] is what shipped. It is kept here beside the curve it replaced so
 * `BUILD-PLAN.md`'s C1c before-and-after stays re-runnable rather than being a
 * claim nobody can check.
 */
object Curves {

    /** The curve SPEC 5.5 shipped with until C1c: a 700ms level 1. */
    val Original = SpeedCurve(
        msPerRow = listOf(
            700,
            620, 550, 490,
            430, 380, 340, 300,
            270, 245, 220, 200,
            185, 170, 158, 148,
            140, 133, 127, 122,
            118,
        )
    )

    /** C3's own suggestion, and what SPEC 5.5 now says. Equal to `SpeedCurve.Default`. */
    val Fast500 = Original.copy(
        msPerRow = listOf(500, 470, 440, 410, 380, 350, 325, 300) + Original.msPerRow.drop(8)
    )

    /** Half of [Fast500], measured as the other bracket on the same question and not adopted. */
    val Fast600 = Original.copy(
        msPerRow = listOf(600, 560, 520, 480, 430, 380, 340, 300) + Original.msPerRow.drop(8)
    )

    /**
     * Faster than what shipped, added by C1e so the re-check of L28 has a bracket
     * on both sides of [Fast500] rather than only above it.
     *
     * C1c only ever measured curves slower than the one it adopted, which was
     * sound while hard drop existed: the question then was how much dead time a
     * patient player sat through. Without hard drop the fall is a larger share of
     * every drop for every player, so "is 500 already too fast" is a question
     * with something at stake, and it cannot be answered by three curves that are
     * all slower.
     */
    val Fast400 = Original.copy(
        msPerRow = listOf(400, 385, 370, 355, 340, 325, 312, 300) + Original.msPerRow.drop(8)
    )

    val Named: Map<String, SpeedCurve> = mapOf(
        "default" to SpeedCurve.Default,
        "original" to Original,
        "fast600" to Fast600,
        "fast500" to Fast500,
        "fast400" to Fast400,
    )
}
