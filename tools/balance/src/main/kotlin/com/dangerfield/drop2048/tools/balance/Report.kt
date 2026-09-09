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

    fun render(policy: Policy, table: String, clock: String, runs: List<RunOutcome>, millis: Long): String {
        val name = if (policy.cheats) "${policy.name} (ceiling, reads a block the player never sees)" else policy.name
        return render(name, table, clock, runs, millis)
    }

    @Suppress("LongMethod")
    fun render(policy: String, table: String, clock: String, runs: List<RunOutcome>, millis: Long): String {
        val levels = runs.map { it.level }.sorted()
        val drops = runs.map { it.blocksDropped }.sorted()
        val scores = runs.map { it.score }.sorted()
        val depths = sum(runs.map { it.depths })
        val clutter = sum(runs.map { it.clutter })

        return buildString {
            appendLine("policy=$policy  table=$table  clock=$clock  runs=${runs.size}  ${millis}ms")
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
            appendLine("  highest tier at run end")
            appendLine(tierRows(runs))
            appendLine("  cause of death")
            appendLine(deathRows(runs))
            appendLine("  cascade depth per drop")
            appendLine(depthRows(depths))
            append("  clutter (sampled every ${Harness.CLUTTER_SAMPLE_EVERY} drops)")
            appendLine(clutterLine(clutter))
            val clocks = runs.mapNotNull { it.clock }
            if (clocks.isNotEmpty()) append(clockSection(runs, clocks))
        }
    }

    /**
     * The part of the report that only exists with a clock in the loop.
     *
     * Three numbers, and they have to be read together. `timer-placed` is the
     * share of blocks the lock delay put down rather than a drop input.
     * `off preferred column` is the share where the policy did not get the column
     * it asked for, which is the only one of the three that means pressure.
     * `slack` is the time the policy spent with nothing left to do while the
     * block kept falling, which is the one that means theatre.
     */
    private fun clockSection(runs: List<RunOutcome>, clocks: List<ClockStats>): String {
        val drops = runs.sumOf { it.blocksDropped.toLong() }
        return buildString {
            appendLine("  wall clock  median run=${format(median(clocks.map { it.elapsedMillis }) / 1000.0)}s" +
                "  mean per drop=${format(clocks.sumOf { it.elapsedMillis }.toDouble() / drops)}ms")
            appendLine("  timer-placed drops (whole run)=${share(clocks.sumOf { it.timerPlaced.toLong() }, drops)}" +
                "  off preferred column=${share(clocks.sumOf { it.offTarget.toLong() }, drops)}" +
                "  preferred unreachable=${share(clocks.sumOf { it.compromised.toLong() }, drops)}")
            appendLine("  first ${Harness.EARLY_DROPS} drops, per level")
            appendLine(earlyRows(clocks))
            append(levelClockLine(clocks))
        }
    }

    private fun earlyRows(clocks: List<ClockStats>): String {
        val drops = sum(clocks.map { it.earlyDrops })
        val timer = sum(clocks.map { it.earlyTimerPlaced })
        val off = sum(clocks.map { it.earlyOffTarget })
        val slack = total(clocks.map { it.earlySlackMillis })
        val elapsed = total(clocks.map { it.earlyElapsedMillis })
        return drops.indices.mapNotNull { level ->
            val count = drops[level]
            if (count == 0L) return@mapNotNull null
            "    level ${level.toString().padStart(2)}  drops=${count.toString().padStart(7)}" +
                "  timer-placed=${share(timer[level], count).padStart(7)}" +
                "  off preferred=${share(off[level], count).padStart(7)}" +
                "  slack=${format(slack[level].toDouble() / count).padStart(7)}ms" +
                "  per drop=${format(elapsed[level].toDouble() / count).padStart(7)}ms"
        }.joinToString("\n").ifEmpty { "    none" }
    }

    private fun levelClockLine(clocks: List<ClockStats>): String {
        val reached = (1 until Harness.LEVEL_BUCKETS).mapNotNull { level ->
            val times = clocks.mapNotNull { it.reachedLevelAtMillis[level].takeIf { at -> at >= 0 } }
            if (times.size * 2 < clocks.size) return@mapNotNull null
            "    level $level at ${format(median(times) / 1000.0)}s (${share(times.size, clocks.size)} of runs)"
        }.take(TIME_TO_LEVEL_ROWS)
        return "  median time to reach\n" + reached.joinToString("\n") + "\n"
    }

    private fun total(arrays: List<LongArray>): LongArray {
        val out = LongArray(arrays.first().size)
        arrays.forEach { array -> array.indices.forEach { out[it] += array[it] } }
        return out
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    private const val TIME_TO_LEVEL_ROWS = 8

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
