package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.Serializable

/**
 * Every tunable number the engine reads, in one value.
 *
 * These are exactly the keys SPEC 10 marks remote-configurable — the spawn
 * table, the cap divisor, the speed curve, the special rates, `board.rows` and
 * `level.blocksPerLevel`. They are carried **inside** [GameState] rather than
 * passed alongside it, because a replayed seed only reproduces a run if the
 * numbers it was played under travel with it. A run recorded under one spawn
 * table and replayed under another is a different run, and a bug report that
 * silently changes shape when a remote value moves is worse than no bug report.
 *
 * The merge priority order, the resolution algorithm, the burst rule and the
 * scoring formulas are deliberately *not* here. SPEC 10 forbids changing them
 * mid-flight because doing so invalidates every high score on the board.
 */
@Serializable
data class EngineConfig(
    val cols: Int = DEFAULT_COLS,
    val rows: Int = DEFAULT_ROWS,
    /**
     * How many ticks one press of ▼ is worth (decision D11).
     *
     * Two, and the handoff says two. It is a config value rather than a constant
     * because the nudge is now the *only* acceleration a player has, C1e has to
     * re-measure the opening without hard drop, and the number is the obvious
     * dial to sweep while doing it. It travels inside [GameState] like every
     * other tunable here, so a swept value cannot silently reshape a replay.
     */
    val nudgeRows: Int = DEFAULT_NUDGE_ROWS,
    val blocksPerLevel: Int = DEFAULT_BLOCKS_PER_LEVEL,
    val spawnCapDivisor: Int = DEFAULT_CAP_DIVISOR,
    val spawnCapFloor: Int = DEFAULT_CAP_FLOOR,
    val specialSuppressedDraws: Int = DEFAULT_SUPPRESSED_DRAWS,
    val continueRowsCleared: Int = DEFAULT_CONTINUE_ROWS,
    val cascadeStepCap: Int = DEFAULT_STEP_CAP,
    val cascadeMultiplierCap: Int = DEFAULT_MULTIPLIER_CAP,
    val spawnTable: SpawnTable = SpawnTable.Default,
    val specialRates: List<SpecialRate> = SpecialRate.Default,
    val speed: SpeedCurve = SpeedCurve.Default,
    val scoring: Scoring = Scoring(),
) {
    init {
        require(nudgeRows > 0) { "nudgeRows must be positive" }
        require(blocksPerLevel > 0) { "blocksPerLevel must be positive" }
        require(spawnCapDivisor > 0) { "spawnCapDivisor must be positive" }
        require(cascadeStepCap > 0) { "cascadeStepCap must be positive" }
    }

    /** The column a new falling block enters at. */
    val spawnColumn: Int get() = cols / 2

    companion object {
        const val DEFAULT_COLS = 5
        const val DEFAULT_ROWS = 8
        const val DEFAULT_NUDGE_ROWS = 2
        const val DEFAULT_BLOCKS_PER_LEVEL = 20
        const val DEFAULT_CAP_DIVISOR = 16
        const val DEFAULT_CAP_FLOOR = 4
        const val DEFAULT_SUPPRESSED_DRAWS = 3
        const val DEFAULT_STEP_CAP = 100
        const val DEFAULT_MULTIPLIER_CAP = 10
        const val DEFAULT_CONTINUE_ROWS = 3

        val Default = EngineConfig()
    }
}

/** One row of SPEC 5.3's level table: a level band and the weights inside it. */
@Serializable
data class SpawnBand(
    val fromLevel: Int,
    val weights: List<SpawnWeight>,
)

@Serializable
data class SpawnWeight(val value: BlockValue, val percent: Int)

/**
 * SPEC 5.3 constraint A. Bands are matched by the highest [SpawnBand.fromLevel]
 * that does not exceed the level, so the table is open-ended at the top.
 */
@Serializable
data class SpawnTable(val bands: List<SpawnBand>) {
    init {
        require(bands.isNotEmpty()) { "spawn table needs at least one band" }
        bands.forEach { band ->
            require(band.weights.sumOf { it.percent } == FULL) {
                "band from level ${band.fromLevel} sums to ${band.weights.sumOf { it.percent }}, not $FULL"
            }
        }
    }

    fun weightsFor(level: Int): List<SpawnWeight> =
        bands.lastOrNull { it.fromLevel <= level }?.weights ?: bands.first().weights

    companion object {
        const val FULL = 100

        val Default = SpawnTable(
            listOf(
                band(1, BlockValue.V2 to 65, BlockValue.V4 to 35),
                band(4, BlockValue.V2 to 35, BlockValue.V4 to 45, BlockValue.V8 to 20),
                band(7, BlockValue.V2 to 10, BlockValue.V4 to 45, BlockValue.V8 to 35, BlockValue.V16 to 10),
                band(10, BlockValue.V4 to 30, BlockValue.V8 to 40, BlockValue.V16 to 25, BlockValue.V32 to 5),
                band(13, BlockValue.V4 to 15, BlockValue.V8 to 35, BlockValue.V16 to 35, BlockValue.V32 to 15),
                band(16, BlockValue.V8 to 25, BlockValue.V16 to 35, BlockValue.V32 to 30, BlockValue.V64 to 10),
                band(19, BlockValue.V8 to 15, BlockValue.V16 to 30, BlockValue.V32 to 35, BlockValue.V64 to 20),
            )
        )

        private fun band(fromLevel: Int, vararg weights: Pair<BlockValue, Int>) =
            SpawnBand(fromLevel, weights.map { SpawnWeight(it.first, it.second) })
    }
}

/**
 * SPEC 5.2's rate table, in per-mille so the remote key can express a rate
 * finer than a whole percent without a schema change.
 *
 * Order is load-bearing: the single spawn roll is compared against the
 * cumulative rate of the *eligible* specials in this order, so the stream stays
 * stable as levels unlock more of them.
 */
@Serializable
data class SpecialRate(
    val special: Special,
    val fromLevel: Int,
    val perMille: Int,
) {
    companion object {
        val Default = listOf(
            SpecialRate(Special.WILDCARD, fromLevel = 5, perMille = 30),
            SpecialRate(Special.BOMB, fromLevel = 8, perMille = 30),
            SpecialRate(Special.STONE, fromLevel = 12, perMille = 50),
        )

        const val SCALE = 1000
    }
}

/**
 * SPEC 5.5. The engine stores the level and never sleeps; this is a lookup the
 * ViewModel reads to decide how long a row takes.
 *
 * The [floorMs] is load-bearing: past roughly level 20 speed stops being the
 * pressure and the rising spawn floor carries the difficulty. Without the floor
 * the game becomes a reflex test, which is a different and worse game.
 *
 * Levels 1-8 were re-cut in C1c, from `700, 620, 550, 490, 430, 380, 340, 300`.
 * The curve is a pacing dial, not a difficulty one: with the drop clock in the
 * balance harness the opening band never once placed a block the player had not
 * chosen, and every outcome distribution is identical across the old curve and
 * this one. What moved is the dead time — a player who does not use the drop
 * control waited 4.8s a drop at level 1 and now waits 3.5s. Everything from
 * level 9 on is untouched, because the complaint was about the opening.
 */
@Serializable
data class SpeedCurve(
    val msPerRow: List<Int> = DEFAULT_MS_PER_ROW,
    val tailStepMs: Int = DEFAULT_TAIL_STEP,
    val floorMs: Int = DEFAULT_FLOOR,
    val softDropMsPerRow: Int = DEFAULT_SOFT_DROP,
) {
    init {
        require(msPerRow.isNotEmpty()) { "speed curve needs at least one entry" }
        require(floorMs > 0) { "floorMs must be positive" }
    }

    fun msPerRow(level: Int): Int {
        val clamped = if (level < 1) 1 else level
        if (clamped <= msPerRow.size) return msPerRow[clamped - 1]
        val tail = msPerRow.last() - tailStepMs * (clamped - msPerRow.size)
        return if (tail < floorMs) floorMs else tail
    }

    companion object {
        val DEFAULT_MS_PER_ROW = listOf(
            500,
            470, 440, 410,
            380, 350, 325, 300,
            270, 245, 220, 200,
            185, 170, 158, 148,
            140, 133, 127, 122,
            118,
        )
        const val DEFAULT_TAIL_STEP = 2
        const val DEFAULT_FLOOR = 90
        const val DEFAULT_SOFT_DROP = 40

        val Default = SpeedCurve()
    }
}

/**
 * SPEC 7. Never remote — changing these silently invalidates every high score.
 *
 * ### The nudge pays nothing, and that is a decision
 *
 * SPEC 7 used to award `2 x rowsSkipped` for a hard drop. Decision D11 removed
 * hard drop, so that line had nothing left to fire on, and the open question was
 * whether the ▼ nudge inherits it. It does not, for three reasons.
 *
 * The bonus was paying for **commitment**: hard drop gave up the rest of the
 * fall, and there was no way to take it back. A nudge gives up two rows and can
 * be pressed again a moment later, so paying per row would not reward a decision,
 * it would reward the tap. Two rows at two points is four points against merges
 * worth hundreds, so the payout could never be large enough to matter to a good
 * player and would still be large enough to make mashing ▼ strictly better than
 * not, on every block, forever. A control with a free reward and no cost is one
 * the player is obliged to hold down, which is the opposite of the recessive
 * accelerator the handoff drew.
 *
 * Second, the nudge is now the *only* acceleration in the game, so a per-row
 * bonus would land on nearly every drop of nearly every run. A term that almost
 * always fires is a constant multiplier on the drop count, and the score already
 * has one of those in `survivalPerLevel`.
 *
 * Third, it keeps the table honest. Every remaining row of SPEC 7 pays for
 * something that happened *on the board* — a merge, a burst, a detonation, a
 * clear, a level. Nothing pays for an input any more.
 *
 * `ResolutionStep.HardDropBonus` went with the constant, so the transcript now
 * only ever carries board events plus survival and level-up. The invariant that
 * matters is untouched: `next.score == previous.score + transcript.points`.
 */
@Serializable
data class Scoring(
    val burstBase: Int = BURST_BASE,
    val burstPerBlock: Int = BURST_PER_BLOCK,
    val bombPerBlock: Int = BOMB_PER_BLOCK,
    val levelUpPerLevel: Int = LEVEL_UP_PER_LEVEL,
    val survivalPerLevel: Int = SURVIVAL_PER_LEVEL,
    val boardCleared: Int = BOARD_CLEARED,
) {
    companion object {
        const val BURST_BASE = 5000
        const val BURST_PER_BLOCK = 250
        const val BOMB_PER_BLOCK = 50
        const val LEVEL_UP_PER_LEVEL = 100
        const val SURVIVAL_PER_LEVEL = 10
        const val BOARD_CLEARED = 1000
    }
}
