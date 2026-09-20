package com.dangerfield.drop2048.libraries.sharing

/**
 * Builds the share.
 *
 * ```
 * Drop 2048 · Endless
 * 🏆 83,330   🧱 1024   ⛓ x7   ⬆ 19   ⏱ 6:12
 *
 * drop2048.app
 * ```
 *
 * Four numbers on one line, in the order somebody reads them out loud: what they
 * scored, the biggest block they made, how deep their best chain went, and how
 * far they got. The chain is on the line at all because SPEC 21 says the
 * ascending run of a long cascade is one of the two things worth keeping if
 * everything else is cut, and a share that only carried a score would say
 * nothing about the part of the game that is actually the game.
 *
 * A [ShareResult] holds no board, so there is nothing here that could leak a
 * run to somebody who has not played it.
 */
object ShareText {

    fun format(result: ShareResult, labels: ShareLabels): String = buildString {
        appendLine(labels.title)
        append(statsLine(result))
        labels.footer?.let {
            appendLine()
            appendLine()
            append(it)
        }
    }

    fun statsLine(result: ShareResult): String = listOf(
        "$TROPHY ${grouped(result.score)}",
        "$BLOCK ${result.biggestTier}",
        "$CHAIN x${result.longestCascade}",
        "$LEVEL ${result.level}",
        "$CLOCK ${duration(result.durationMs)}",
    ).joinToString(GROUP_GAP)

    /**
     * `m:ss`, or `h:mm:ss` for the rare run that crosses an hour.
     *
     * Public because the stacked-out sheet shows the same time this line does,
     * and a second copy of the arithmetic is how a shared run comes to disagree
     * with the sheet it was shared from.
     */
    fun duration(millis: Long): String {
        val total = millis.coerceAtLeast(0) / MILLIS_PER_SECOND
        val seconds = total % SECONDS_PER_MINUTE
        val minutes = (total / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
        val hours = total / SECONDS_PER_HOUR
        return if (hours > 0) {
            "$hours:${minutes.padded()}:${seconds.padded()}"
        } else {
            "$minutes:${seconds.padded()}"
        }
    }

    private fun Long.padded(): String = toString().padStart(2, '0')

    /**
     * Thousands-grouped with a comma.
     *
     * Not locale-aware, because common Kotlin has no number formatter and the
     * alternative — threading a separator through the API for a string nobody
     * parses — buys less than it costs.
     */
    private fun grouped(value: Long): String {
        val digits = value.toString()
        val sign = if (digits.startsWith("-")) "-" else ""
        val body = digits.removePrefix("-")
        return sign + body.reversed().chunked(GROUP_SIZE).joinToString(",").reversed()
    }

    private const val TROPHY = "🏆"
    private const val BLOCK = "🧱"
    private const val CHAIN = "⛓"
    private const val LEVEL = "⬆"
    private const val CLOCK = "⏱"

    /** Three spaces, wide enough to read as a gap between emoji on one line. */
    private const val GROUP_GAP = "   "

    private const val GROUP_SIZE = 3
    private const val MILLIS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
