package com.dangerfield.drop2048.libraries.achievements


/**
 * Every number an achievement can be earned against.
 *
 * Typed rather than string-keyed. Nothing here is persisted — counters are
 * folded from the stored [RunOutcome] log on demand — so there is no schema to
 * migrate and no reason to give up the compiler. Adding a stat forces a branch
 * in [AchievementCounters.fold] and a branch in the reachability test's
 * exhaustive `when`, which is the question that catches a counter nothing in the
 * game can ever increment.
 */
enum class Stat {
    /** Every merge of every run, added up. SPEC 15's "total merges". */
    TotalMerges,

    /** Points value of the highest tier *reached*, ever (decision D7). */
    HighestTier,

    /** Every row burst of every run, added up. */
    TotalBursts,

    /** The most bursts in any one run. */
    MostBurstsInARun,

    /** The deepest single cascade, ever. A chain, not a sum of chains. */
    LongestCascade,

    /** Runs' worth of SPEC 7's board-cleared bonus, added up. */
    BoardsCleared,

    /** Bursts that took at least [RunFacts.STONES_FOR_A_BADGE] Stones with them. */
    StoneBursts,

    /** Wildcard merges that produced a 2048. */
    WildcardBursts,

    /** The most consecutive drops survived in the danger state in any one run. */
    LongestDangerRun,

    /** The highest level any run reached. */
    HighestLevel,

    /** The most blocks placed in any one run. */
    MostBlocksInARun,

    /**
     * The best score of any run.
     *
     * It was Endless-only while the Daily existed (D19), because a score set on
     * a seed everybody else also played would have been a ladder measuring the
     * seed. D27 removed the mode, so there is one kind of run and one best.
     */
    BestScore,

    /**
     * Whole minutes spent playing, summed over every recorded run.
     *
     * Decision D12's duration, so time on a lock screen is not time on the
     * board. It is the honest version of "total playtime" and it is the same
     * number SPEC 15's stats page shows.
     */
    MinutesPlayed,
}

/**
 * A player's stats, folded from their [RunOutcome] history in order.
 *
 * The fold is the single definition of "how a finished run moves the numbers".
 * Counters come in three shapes and only one of them is a sum, which is why the
 * log is replayed rather than aggregated:
 *
 * - **accumulators** ([Stat.TotalMerges], [Stat.BoardsCleared]) — added per run;
 * - **high-water marks** ([Stat.BestScore], [Stat.LongestCascade]) — running max;
 * - **bookkeeping** ([playedMillis]) — carried at full precision because
 *   [Stat.MinutesPlayed] is truncated for display and a hundred short runs would
 *   each round to nothing.
 *
 * Every one of these is monotonic over a history, which is what lets
 * [AchievementEngine] treat an unlock as permanent. A badge hung off a value
 * that could go down would unlock and then disagree with its own counter on the
 * next run.
 */
data class AchievementCounters(
    private val values: Map<Stat, Long> = emptyMap(),
    private val playedMillis: Long = 0L,
) {

    operator fun get(stat: Stat): Long = values[stat] ?: 0L

    /** Folds one finished run in and returns the next snapshot. Pure. */
    fun fold(outcome: RunOutcome): AchievementCounters {
        val next = values.toMutableMap()
        fun add(stat: Stat, amount: Long) { next[stat] = (next[stat] ?: 0L) + amount }
        fun setMax(stat: Stat, candidate: Long) { next[stat] = maxOf(next[stat] ?: 0L, candidate) }

        val played = playedMillis + outcome.durationMs.coerceAtLeast(0L)
        next[Stat.MinutesPlayed] = played / MINUTE_MS

        add(Stat.TotalMerges, outcome.merges.toLong())
        add(Stat.TotalBursts, outcome.bursts.toLong())
        add(Stat.BoardsCleared, outcome.facts.boardsCleared.toLong())
        add(Stat.StoneBursts, outcome.facts.stoneBursts.toLong())
        add(Stat.WildcardBursts, outcome.facts.wildcardBursts.toLong())

        setMax(Stat.HighestTier, outcome.highestTier.toLong())
        setMax(Stat.MostBurstsInARun, outcome.bursts.toLong())
        setMax(Stat.LongestCascade, outcome.longestCascade.toLong())
        setMax(Stat.LongestDangerRun, outcome.facts.longestDangerRun.toLong())
        setMax(Stat.HighestLevel, outcome.level.toLong())
        setMax(Stat.MostBlocksInARun, outcome.blocksPlaced.toLong())
        setMax(Stat.BestScore, outcome.score)

        return AchievementCounters(next, played)
    }

    companion object {
        val Empty: AchievementCounters = AchievementCounters()

        private const val MINUTE_MS: Long = 60_000
    }
}
