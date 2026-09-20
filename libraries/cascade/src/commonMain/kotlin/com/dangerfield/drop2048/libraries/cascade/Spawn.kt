package com.dangerfield.drop2048.libraries.cascade

internal data class Draw(val rng: Rng, val block: Block, val column: Int, val wasSpecial: Boolean)

/**
 * SPEC 5.3's two stacked constraints, plus SPEC 5.2's special rates.
 *
 * The draw happens when the block **spawns**, against the board it will land on.
 *
 * It used to happen two drops earlier, when the block entered the next-block
 * preview: a preview that can still change is a lie, so the value had to be
 * fixed the moment it was shown, and the board-aware cap below therefore read a
 * board two drops stale. Decision D11 cut the preview, and nothing else ever
 * wanted the early draw. The cap now reads the real board.
 *
 * The board does not change while a block is in flight — only a lock changes it
 * — so "at spawn" and "at landing" are the same board, and the cap is now
 * exactly the rule SPEC 5.3 describes rather than an approximation of it. L20
 * measured the approximation at 0.02-0.04% of drops, so expect no visible
 * change; what this buys is one fewer caveat, not one fewer bug.
 *
 * When a special is suppressed — the first three draws of a run, or the draw
 * straight after a special — the special roll is not taken at all rather than
 * taken and discarded. Either is deterministic; not taking it keeps the RNG
 * stream shorter and makes a stream diff readable.
 *
 * ### The column is drawn here too, and it is uniform
 *
 * Blocks used to enter at `cols / 2` on every drop. The owner's 2026-09-20
 * ruling made the entry column random, and the draw belongs in this function
 * rather than at either call site in [Cascade] because it has to come off
 * [GameState]'s own RNG — a column decided anywhere else would break the one
 * property the whole engine is built on, that a seed plus a list of inputs
 * reproduces a run byte for byte (SPEC 4.1).
 *
 * The column roll is taken **after** the value roll rather than before it, so
 * every roll that existed before this change stays in the order it was in and a
 * diff of two RNG streams reads as one appended roll per draw instead of a
 * re-alignment. It is taken unconditionally, including when a special was drawn,
 * because a roll that is sometimes skipped is a second alignment rule to keep
 * straight for no benefit — the suppression above is skipped-not-discarded only
 * because it fires on a run's first three draws and would otherwise dominate the
 * opening of every stream diff.
 *
 * Nothing constrains the column against the board. It does not need to: a run
 * ends when row 0 is occupied after a resolution completes, so by the time
 * [Cascade] spawns anything, row 0 is empty in every column. [Cascade]'s
 * `SPAWN_BLOCKED` fault still guards that reasoning rather than trusting it.
 */
internal object Spawn {

    fun draw(
        rng: Rng,
        level: Int,
        board: Board,
        drawIndex: Int,
        lastWasSpecial: Boolean,
        config: EngineConfig,
    ): Draw {
        val eligible = config.specialRates.filter { it.fromLevel <= level && it.perMille > 0 }
        val suppressed = drawIndex < config.specialSuppressedDraws || lastWasSpecial

        var current = rng
        if (!suppressed && eligible.isNotEmpty()) {
            current = current.next()
            val roll = current.valueIn(SpecialRate.SCALE)
            var threshold = 0
            eligible.forEach { rate ->
                threshold += rate.perMille
                if (roll < threshold) return withColumn(current, SpecialBlock(rate.special), wasSpecial = true, config)
            }
        }

        current = current.next()
        val roll = current.valueIn(SpawnTable.FULL)
        val weights = config.spawnTable.weightsFor(level)
        var threshold = 0
        var drawn = weights.last().value
        for (weight in weights) {
            threshold += weight.percent
            if (roll < threshold) {
                drawn = weight.value
                break
            }
        }
        return withColumn(current, NumberBlock(capped(drawn, board, config)), wasSpecial = false, config)
    }

    private fun withColumn(rng: Rng, block: Block, wasSpecial: Boolean, config: EngineConfig): Draw {
        val advanced = rng.next()
        return Draw(advanced, block, advanced.valueIn(config.cols), wasSpecial)
    }

    /**
     * SPEC 5.3 constraint B: the spawned value may not exceed
     * `max(4, highestOnBoard / 16)`. This is the safety valve that stops a 64
     * landing on a board of 2s; the level table is what makes 2048 reachable
     * inside one run.
     */
    private fun capped(drawn: BlockValue, board: Board, config: EngineConfig): BlockValue {
        val highest = board.highestValue()?.points ?: 0
        val ceiling = maxOf(config.spawnCapFloor, highest / config.spawnCapDivisor)
        val allowed = BlockValue.atMost(ceiling) ?: BlockValue.V2
        return if (drawn.points > allowed.points) allowed else drawn
    }
}
