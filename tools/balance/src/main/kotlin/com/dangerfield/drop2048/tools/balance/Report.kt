package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.BlockValue

/**
 * Turns a pile of [RunOutcome]s into the six things SPEC 4.4 asks for: median
 * and p90 level, the highest-tier distribution, cause of death, the cascade
 * depth histogram, and clutter.
 *
 * Everything is printed as a share as well as a count, because the only two
 * numbers SPEC 17 says are watched weekly — median level and the highest-tier
 * distribution — are read against each other and not on their own.
 */
object Report {

    fun render(policy: String, table: String, runs: List<RunOutcome>, millis: Long): String {
        val levels = runs.map { it.level }.sorted()
        val drops = runs.map { it.blocksDropped }.sorted()
        val scores = runs.map { it.score }.sorted()
        val depths = sum(runs.map { it.depths })
        val clutter = sum(runs.map { it.clutter })

        return buildString {
            appendLine("policy=$policy  table=$table  runs=${runs.size}  ${millis}ms")
            appendLine(
                "  level      median=${levels.median()}  p90=${levels.p90()}  max=${levels.last()}"
            )
            appendLine(
                "  drops      median=${drops.median()}  p90=${drops.p90()}  max=${drops.last()}"
            )
            appendLine(
                "  score      median=${scores.median()}  p90=${scores.p90()}  max=${scores.last()}"
            )
            appendLine("  bursts     runs with at least one=${share(runs.count { it.bursts > 0 }, runs.size)}")
            appendLine(
                "  stale cap  drops above the cap the board would impose now=" +
                    share(runs.sumOf { it.staleCapDrops.toLong() }, runs.sumOf { it.blocksDropped.toLong() })
            )
            appendLine("  highest tier at run end")
            appendLine(tierRows(runs))
            appendLine("  cause of death")
            appendLine(deathRows(runs))
            appendLine("  cascade depth per drop")
            appendLine(depthRows(depths))
            append("  clutter (sampled every ${Harness.CLUTTER_SAMPLE_EVERY} drops)")
            appendLine(clutterLine(clutter))
        }
    }

    private fun tierRows(runs: List<RunOutcome>): String {
        val counts = runs.groupingBy { it.highestTier }.eachCount()
        return BlockValue.entries.mapNotNull { tier ->
            val count = counts[tier] ?: return@mapNotNull null
            "    ${tier.points.toString().padStart(4)}  ${bar(count, runs.size)}"
        }.joinToString("\n").ifEmpty { "    none" }
    }

    private fun deathRows(runs: List<RunOutcome>): String {
        val counts = runs.groupingBy { it.death }.eachCount()
        return Death.entries.mapNotNull { cause ->
            val count = counts[cause] ?: return@mapNotNull null
            "    ${cause.name.lowercase().padEnd(18)}${bar(count, runs.size)}"
        }.joinToString("\n")
    }

    private fun depthRows(depths: LongArray): String {
        val total = depths.sum()
        if (total == 0L) return "    none"
        return depths.indices.mapNotNull { depth ->
            val count = depths[depth]
            if (count == 0L) return@mapNotNull null
            val label = if (depth == depths.size - 1) "${depth}+" else "$depth"
            "    ${label.padStart(4)}  ${bar(count, total)}"
        }.joinToString("\n") + "\n    mean=${format(weightedMean(depths))}" +
            "  share of drops that cascaded=${share(total - depths[0], total)}" +
            "  share depth>=2=${share(depths.drop(2).sum(), total)}"
    }

    private fun clutterLine(clutter: LongArray): String {
        val total = clutter.sum()
        if (total == 0L) return "  no samples"
        return "  mean=${format(weightedMean(clutter))}" +
            "  median=${quantile(clutter, 0.5)}" +
            "  p90=${quantile(clutter, 0.9)}" +
            "  max=${clutter.indexOfLast { it > 0 }}"
    }

    private fun bar(count: Int, total: Int): String = bar(count.toLong(), total.toLong())

    private fun bar(count: Long, total: Long): String {
        val pct = 100.0 * count / total
        return "${format(pct).padStart(6)}%  ($count)"
    }

    private fun share(count: Long, total: Long): String = "${format(100.0 * count / total)}%"

    private fun share(count: Int, total: Int): String = share(count.toLong(), total.toLong())

    private fun weightedMean(histogram: LongArray): Double {
        val total = histogram.sum()
        if (total == 0L) return 0.0
        return histogram.indices.sumOf { it * histogram[it] }.toDouble() / total
    }

    private fun quantile(histogram: LongArray, fraction: Double): Int {
        val target = (histogram.sum() * fraction).toLong()
        var seen = 0L
        histogram.indices.forEach { index ->
            seen += histogram[index]
            if (seen >= target) return index
        }
        return histogram.size - 1
    }

    private fun sum(arrays: List<IntArray>): LongArray {
        val out = LongArray(arrays.first().size)
        arrays.forEach { array -> array.indices.forEach { out[it] += array[it] } }
        return out
    }

    private fun format(value: Double): String = ((value * 100).toLong() / 100.0).toString()

    private fun List<Int>.median(): Int = this[size / 2]

    private fun List<Int>.p90(): Int = this[(size * 9) / 10]

    @JvmName("medianLong")
    private fun List<Long>.median(): Long = this[size / 2]

    @JvmName("p90Long")
    private fun List<Long>.p90(): Long = this[(size * 9) / 10]
}
