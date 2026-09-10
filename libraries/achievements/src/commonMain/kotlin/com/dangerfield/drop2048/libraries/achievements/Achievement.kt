package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.cascade.EngineConfig

/**
 * Stable identity for an achievement. Persisted by name, so renaming one throws
 * away everybody who earned it — add a new entry instead.
 *
 * **There is no display copy here on purpose.** A name and a description are
 * player-facing strings, they have to be translated, and this module cannot see
 * `:libraries:resources`. The UI maps an id to copy with an exhaustive `when`,
 * which means adding an entry here fails to compile until somebody writes the
 * words for it. A `nameKey: String` on the definition would have looked tidier
 * and would have failed at runtime, in the release build, as a badge captioned
 * `achievement_clean_sweep_name`.
 *
 * Declared in [Achievements]' own order — the three firsts, then chains, then
 * endurance, the three rare moments, score, the Daily, and time on the board —
 * so the enum and the grid read the same way down the file.
 */
enum class AchievementId {
    FirstMerge,
    SixtyFour,
    FirstBurst,

    ChainOfFive,
    ChainOfTen,
    DoubleBurst,

    LevelTwenty,
    FiveHundredBlocks,
    OnTheBrink,

    CleanSweep,
    StoneCold,
    WildFinish,

    FirstFigures,
    SolidRun,
    SharpRun,
    BigRun,
    MonsterRun,

    SevenDays,
    ThirtyDays,

    OneHour,
    FiveHours,
    TenHours,
    TwentyFiveHours,
    FiftyHours,
}

/**
 * Which shelf of the grid a badge sits on.
 *
 * Twenty-four tiles is past the point where list order carries a grouping on
 * its own, and the reason to open the screen is to find the ladder you are
 * part-way up rather than to read a wall.
 *
 * There is no hidden or secret group. Sodogku has one and it earns its keep
 * there because nine of its badges are surprises ("play at 3am"); every badge
 * here is a thing to go and do, and a mystery tile over a goal is a goal nobody
 * can aim at.
 */
enum class AchievementGroup {
    Firsts,
    Chains,
    Endurance,
    Rare,
    Score,
    Daily,
    Dedication,
}

/**
 * One achievement: a counter, and the value of it that earns the badge.
 *
 * Every criterion in the catalog is that same shape. Anything that cannot be
 * phrased as "this stat reached this number" becomes a new [Stat] in the fold
 * rather than a new kind of criterion — the reasoning about what counts then
 * lives in exactly one place, and progress-toward-unlock is a division rather
 * than a special case.
 */
data class Achievement(
    val id: AchievementId,
    val stat: Stat,
    val target: Long,
) {
    init {
        require(target > 0) { "$id needs a positive target, was $target" }
    }

    fun isMet(counters: AchievementCounters): Boolean = counters[stat] >= target

    /** 0.0 to 1.0, for a progress bar. */
    fun progress(counters: AchievementCounters): Float =
        (counters[stat].toFloat() / target).coerceIn(0f, 1f)

    /**
     * Where the player is on this badge's counter, clamped to the target.
     *
     * Clamped because the raw counter keeps climbing after a badge is earned,
     * and "1,284 / 1" under the first-merge badge reads as a bug.
     */
    fun currentFor(counters: AchievementCounters): Long = counters[stat].coerceAtMost(target)
}

/** One shelf: a group and the badges on it, easiest first. */
data class AchievementSection(
    val group: AchievementGroup,
    val achievements: List<Achievement>,
)

/**
 * SPEC 15's twenty-four, client-side and shipped in the binary rather than
 * served: every badge needs copy, so a new one costs a release regardless.
 *
 * [sections] is the source of truth and [catalog] is its flattening, so display
 * order and grant order cannot disagree and no badge can be left off the grid by
 * being forgotten in a second list.
 *
 * **They pay nothing.** SPEC 2 cut coins with the powerups they existed to buy,
 * so a badge unlocks, posts to the platform and stops there. There is no
 * currency field here and adding one is a scope decision, not a plumbing one.
 *
 * Every target that describes *the game* rather than the player is checked
 * against the shipped engine by `AchievementReachabilityTest`. A badge nobody
 * can ever earn is worse than no badge, and it looks exactly like a working one.
 */
object Achievements {

    val sections: List<AchievementSection> = listOf(
        AchievementSection(
            AchievementGroup.Firsts,
            listOf(
                Achievement(AchievementId.FirstMerge, Stat.TotalMerges, target = 1),
                Achievement(AchievementId.SixtyFour, Stat.HighestTier, target = 64),
                Achievement(AchievementId.FirstBurst, Stat.TotalBursts, target = 1),
            ),
        ),
        AchievementSection(
            AchievementGroup.Chains,
            listOf(
                Achievement(AchievementId.ChainOfFive, Stat.LongestCascade, target = 5),
                Achievement(AchievementId.ChainOfTen, Stat.LongestCascade, target = 10),
                Achievement(AchievementId.DoubleBurst, Stat.MostBurstsInARun, target = 2),
            ),
        ),
        AchievementSection(
            AchievementGroup.Endurance,
            listOf(
                Achievement(AchievementId.LevelTwenty, Stat.HighestLevel, target = 20),
                Achievement(AchievementId.FiveHundredBlocks, Stat.MostBlocksInARun, target = 500),
                Achievement(AchievementId.OnTheBrink, Stat.LongestDangerRun, target = 10),
            ),
        ),
        AchievementSection(
            AchievementGroup.Rare,
            listOf(
                Achievement(AchievementId.CleanSweep, Stat.BoardsCleared, target = 1),
                Achievement(AchievementId.StoneCold, Stat.StoneBursts, target = 1),
                Achievement(AchievementId.WildFinish, Stat.WildcardBursts, target = 1),
            ),
        ),
        AchievementSection(
            AchievementGroup.Score,
            listOf(
                Achievement(AchievementId.FirstFigures, Stat.BestScore, ScoreLadder.rungs[0]),
                Achievement(AchievementId.SolidRun, Stat.BestScore, ScoreLadder.rungs[1]),
                Achievement(AchievementId.SharpRun, Stat.BestScore, ScoreLadder.rungs[2]),
                Achievement(AchievementId.BigRun, Stat.BestScore, ScoreLadder.rungs[3]),
                Achievement(AchievementId.MonsterRun, Stat.BestScore, ScoreLadder.rungs[4]),
            ),
        ),
        AchievementSection(
            AchievementGroup.Daily,
            listOf(
                Achievement(AchievementId.SevenDays, Stat.BestDailyStreak, target = 7),
                Achievement(AchievementId.ThirtyDays, Stat.BestDailyStreak, target = 30),
            ),
        ),
        AchievementSection(
            AchievementGroup.Dedication,
            listOf(
                Achievement(AchievementId.OneHour, Stat.MinutesPlayed, target = 60),
                Achievement(AchievementId.FiveHours, Stat.MinutesPlayed, target = 300),
                Achievement(AchievementId.TenHours, Stat.MinutesPlayed, target = 600),
                Achievement(AchievementId.TwentyFiveHours, Stat.MinutesPlayed, target = 1_500),
                Achievement(AchievementId.FiftyHours, Stat.MinutesPlayed, target = 3_000),
            ),
        ),
    )

    /** Every badge, in display order. */
    val catalog: List<Achievement> = sections.flatMap { it.achievements }

    private val byId: Map<AchievementId, Achievement> = catalog.associateBy { it.id }

    private val groupById: Map<AchievementId, AchievementGroup> =
        sections.flatMap { section -> section.achievements.map { it.id to section.group } }.toMap()

    operator fun get(id: AchievementId): Achievement = byId.getValue(id)

    fun groupOf(id: AchievementId): AchievementGroup = groupById.getValue(id)
}

/**
 * The five score rungs, derived from the scoring formula instead of typed.
 *
 * These are the only targets in the catalog whose unit is *points*, and points
 * are the one quantity in the game with no natural scale: SPEC 7's table is six
 * coefficients, all of them `Scoring` fields, and halving `survivalPerLevel`
 * would halve most of every score there will ever be. Sodogku shipped this bug
 * twice — a rescale left three badges permanently unearnable while still
 * rendering as ordinary tiles with a progress bar stuck near zero — and the fix
 * that stuck was to stop writing numbers down.
 *
 * So each rung is [floorScore] of a level: the score a run is **guaranteed** to
 * have banked by the time it reaches that level, from survival and level-up
 * bonuses alone. Merges, bursts and detonations are all additive on top, so a
 * player who gets to level 20 has at least the level-20 rung whatever else they
 * did. That makes each badge say something a player can aim at ("get to level
 * twenty") rather than name a number, and it moves with the coefficients on its
 * own.
 *
 * The chosen levels are 5, 10, 15, 20 and 25, against a measured greedy median
 * of level 19 and a p90 of 24 (`tools/balance`, 2,000 clocked runs). The top
 * rung is therefore a good run rather than a freak one, and
 * `AchievementReachabilityTest` plays the shipped engine to prove it.
 *
 * The one thing derivation cannot cover: the catalog is compiled into the binary
 * and reads the *shipped* coefficients, while an Endless run scores on whatever
 * remote config says. SPEC 10 marks the scoring formulas never-remote precisely
 * because moving them invalidates high scores, so that gap is closed by a rule
 * rather than by code.
 */
internal object ScoreLadder {

    /** The levels each rung is priced at, ascending. */
    val levels: List<Int> = listOf(5, 10, 15, 20, 25)

    val rungs: List<Long> = levels.map { legible(floorScore(it)) }

    /**
     * The least a run that reaches [level] can possibly have scored.
     *
     * Survival pays `survivalPerLevel x level` for every block dropped, and
     * reaching level *n* takes `blocksPerLevel` drops at each of levels 1 to
     * n-1 — the run is *at* level n having dropped `blocksPerLevel x (n - 1)`
     * blocks. The level-up bonus pays `levelUpPerLevel x newLevel` once for each
     * of levels 2 to n. Nothing else in SPEC 7's table is guaranteed, so nothing
     * else is counted.
     */
    fun floorScore(level: Int, config: EngineConfig = EngineConfig.Default): Long {
        val scoring = config.scoring
        val perLevel = config.blocksPerLevel.toLong()
        val survival = (1 until level).sumOf { perLevel * scoring.survivalPerLevel * it }
        val levelUps = (2..level).sumOf { scoring.levelUpPerLevel.toLong() * it }
        return survival + levelUps
    }

    /**
     * [points] rounded down to two significant figures, because a badge whose
     * description reads "score 62,900 points" is a badge that looks like a bug.
     *
     * Down rather than to nearest, so rounding can only ever make a rung easier
     * than the run it was derived from — the direction that cannot strand it.
     * Significant figures rather than a fixed step because the step has to
     * survive the next rescale too: at a tenth of these coefficients this still
     * returns two digits, where flooring to the nearest thousand would return
     * zero and [Achievement]'s own `require(target > 0)` would take the app down
     * at class-init time.
     */
    private fun legible(points: Long): Long {
        var step = 1L
        while (points / step >= SIGNIFICANT) step *= DECIMAL
        return (points / step * step).coerceAtLeast(1L)
    }

    private const val SIGNIFICANT = 100L
    private const val DECIMAL = 10L
}
