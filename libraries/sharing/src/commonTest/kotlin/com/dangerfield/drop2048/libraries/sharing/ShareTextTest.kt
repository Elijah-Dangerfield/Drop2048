package com.dangerfield.drop2048.libraries.sharing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareTextTest {

    @Test
    fun aRunRendersAsATitleAStatLineAndAFooter() {
        val text = ShareText.format(
            result = run(score = 83_330, biggestTier = 1024, longestCascade = 7, level = 19),
            labels = ShareLabels(title = "Drop 2048 · Endless", footer = "drop2048.app"),
        )

        assertEquals(
            """
            Drop 2048 · Endless
            🏆 83,330   🧱 1024   ⛓ x7   ⬆ 19   ⏱ 6:12

            drop2048.app
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun theStreakLineIsOmittedEntirelyWhenThereIsNoStreak() {
        val text = ShareText.format(run(), ShareLabels(title = "Drop 2048"))

        assertEquals(2, text.lines().size, "an empty line was left where the streak goes")
    }

    @Test
    fun theStreakLineSitsUnderTheStats() {
        val text = ShareText.format(
            result = run(),
            labels = ShareLabels(title = "Drop 2048", streak = "🔥 12 day streak"),
        )

        assertEquals("🔥 12 day streak", text.lines().last())
    }

    /**
     * The one property the type is shaped to guarantee. A [ShareResult] holds no
     * board, no seed and no transcript, so there is nothing the formatter could
     * print that would tell a reader who has not played today's Daily anything
     * about it.
     */
    @Test
    fun aShareCarriesNothingThatCouldGiveTheBoardAway() {
        val text = ShareText.format(
            result = run(score = 1_000, biggestTier = 512, longestCascade = 4, level = 11),
            labels = ShareLabels(title = "Drop 2048 Daily · Sep 10"),
        )

        assertFalse(text.contains("seed", ignoreCase = true))
        assertTrue(text.lines().size <= MaxLines, "the share grew a body: $text")
    }

    @Test
    fun aScoreIsGroupedInThousands() {
        val text = ShareText.statsLine(run(score = 1_234_567))

        assertTrue(text.contains("1,234,567"), text)
    }

    @Test
    fun aRunUnderAnHourReadsAsMinutesAndSeconds() {
        assertEquals("0:00", ShareText.duration(0))
        assertEquals("0:09", ShareText.duration(9_500))
        assertEquals("6:12", ShareText.duration(372_000))
        assertEquals("59:59", ShareText.duration(3_599_000))
    }

    @Test
    fun aRunPastAnHourGrowsAnHoursField() {
        assertEquals("1:00:00", ShareText.duration(3_600_000))
        assertEquals("2:05:07", ShareText.duration(7_507_000))
    }

    /** A negative duration is a clock bug, not a negative time. It reads as zero. */
    @Test
    fun aNegativeDurationIsClamped() {
        assertEquals("0:00", ShareText.duration(-1))
    }

    private fun run(
        score: Long = 0,
        biggestTier: Int = 64,
        longestCascade: Int = 1,
        level: Int = 3,
        durationMs: Long = 372_000,
    ) = ShareResult(
        score = score,
        biggestTier = biggestTier,
        longestCascade = longestCascade,
        level = level,
        durationMs = durationMs,
    )

    private companion object {
        /** Title, stats, blank, streak or footer. Anything longer is a body. */
        const val MaxLines = 4
    }
}
