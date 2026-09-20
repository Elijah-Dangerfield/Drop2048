package com.dangerfield.drop2048.libraries.cascade

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SPEC 5.3's two stacked constraints and SPEC 5.2's special rates.
 *
 * These are the most important numbers in the game and the ones most likely to
 * be moved remotely, so the tests assert the *shape* of the rules — a level
 * band is respected, the cap clamps down rather than rerolling, specials
 * unlock by level and never come back to back — rather than pinning the exact
 * percentages, which SPEC 5.3 explicitly expects to be wrong on launch day.
 *
 * The entry column (the owner's 2026-09-20 ruling) is tested the same way: in
 * bounds always, roughly uniform over many draws, never the same column every
 * time. A tolerance rather than an exact histogram, because the assertion worth
 * having is "this is a fair draw over the columns" and a pinned count would fail
 * the next time anything upstream of it takes one more roll.
 */
class SpawnTest {

    private val config = EngineConfig.Default

    @Test
    fun theLevelTableOnlyEverDrawsValuesThatBandAllows() {
        listOf(1 to setOf(2, 4), 5 to setOf(2, 4, 8), 20 to setOf(8, 16, 32, 64)).forEach { (level, allowed) ->
            val board = boardOf("2048 . . . .")
            val drawn = drawMany(level = level, board = board, count = 400)
                .mapNotNull { it.numberValue?.points }
                .toSet()
            assertTrue(
                allowed.containsAll(drawn),
                "level $level drew $drawn, band allows $allowed",
            )
        }
    }

    @Test
    fun everyValueInABandIsActuallyReachable() {
        val board = boardOf("2048 . . . .")
        val drawn = drawMany(level = 20, board = board, count = 2_000)
            .mapNotNull { it.numberValue?.points }
            .toSet()

        assertEquals(setOf(8, 16, 32, 64), drawn)
    }

    @Test
    fun theBoardAwareCapClampsDownRatherThanRerolling() {
        val empty = Board.empty(config.cols, config.rows)
        val onEmptyBoard = drawMany(level = 20, board = empty, count = 300)
            .mapNotNull { it.numberValue?.points }
            .toSet()

        assertEquals(setOf(4), onEmptyBoard, "max(4, 0 / 16) is 4, so everything clamps to a 4")

        val withA256 = boardOf("256 . . . .")
        val capped = drawMany(level = 20, board = withA256, count = 300)
            .mapNotNull { it.numberValue?.points }
        assertTrue(capped.all { it <= 16 }, "256 / 16 is 16: ${capped.toSet()}")
        assertTrue(capped.contains(16), "the cap must be reachable, not just an upper bound")
    }

    @Test
    fun theCapFloorIsFourSoAFreshBoardStillGetsFours() {
        val drawn = drawMany(level = 1, board = Board.empty(config.cols, config.rows), count = 200)
            .mapNotNull { it.numberValue?.points }
            .toSet()

        assertEquals(setOf(2, 4), drawn)
    }

    /**
     * The draws are counted, not the drops, and with the preview gone (decision
     * D11) the two are the same thing — so the run has to actually be played
     * three blocks deep to see the third draw.
     */
    @Test
    fun specialsAreSuppressedForTheFirstThreeDrawsOfARun() {
        (0 until 40).forEach { seed ->
            var state = Cascade.newGame(seed.toLong(), config.copy(specialRates = alwaysSpecial()))
            val opening = mutableListOf<Block?>()
            repeat(config.specialSuppressedDraws) {
                opening += state.falling?.block
                state = Cascade.apply(state, Input.Lock).state
            }
            assertTrue(
                opening.none { it is SpecialBlock },
                "seed $seed opened with a special: $opening",
            )
        }
    }

    /**
     * SPEC 5.3's cap now reads the board the block will land on rather than the
     * board of two drops ago (decision D11).
     *
     * The board only changes at a lock, so the test has to be a lock that moves
     * the ceiling: a 1024 landing on a 1024 makes a 2048, which bursts its own
     * row and leaves the board empty. The ceiling goes from `2048 / 16` down to
     * the floor of 4 in one transition. Drawn at spawn, the next block is a 4;
     * drawn two drops early against the pre-burst board, a level-20 table could
     * hand out a 64.
     */
    @Test
    fun theCapIsReadAtSpawnAgainstTheBoardTheLockJustProduced() {
        val state = stateOf(
            boardOf("1024 . . . ."),
            level = 20,
            config = config.copy(specialRates = emptyList()),
        )

        val transition = drop(state, value(1024), col = 0)

        assertTrue(transition.state.board.isClear, "the burst emptied the board")
        assertEquals(
            config.spawnCapFloor,
            transition.state.falling?.block?.numberValue?.points,
            "an empty board caps the very next spawn at the floor",
        )
    }

    @Test
    fun twoSpecialsNeverArriveBackToBack() {
        var rng = Rng(1234)
        var lastWasSpecial = false
        val board = boardOf("2048 . . . .")
        var previous: Block? = null

        repeat(500) { index ->
            val draw = Spawn.draw(rng, level = 20, board, index + 3, lastWasSpecial, config.copy(specialRates = alwaysSpecial()))
            assertTrue(
                !(draw.block is SpecialBlock && previous is SpecialBlock),
                "two specials in a row at draw $index",
            )
            rng = draw.rng
            lastWasSpecial = draw.wasSpecial
            previous = draw.block
        }
    }

    @Test
    fun aSpecialOnlyAppearsFromItsOwnLevel() {
        val board = boardOf("2048 . . . .")
        val seen = mutableMapOf<Special, Int>()
        listOf(1, 4, 5, 7, 8, 11, 12).forEach { level ->
            drawMany(level = level, board = board, count = 4_000, allowSpecials = true).forEach { block ->
                block.specialKind?.let { special ->
                    seen.getOrPut(special) { level }
                    assertTrue(
                        level >= SpecialRate.Default.single { it.special == special }.fromLevel,
                        "$special appeared at level $level",
                    )
                }
            }
        }
        assertEquals(setOf(Special.WILDCARD, Special.BOMB, Special.STONE), seen.keys)
    }

    @Test
    fun theEntryColumnIsUniformOverTheBoardsColumns() {
        val counts = IntArray(config.cols)
        drawManyColumns(count = 50_000).forEach { counts[it]++ }

        val expected = 50_000.0 / config.cols
        counts.forEachIndexed { col, count ->
            assertTrue(
                count > expected * 0.9 && count < expected * 1.1,
                "column $col drew $count of 50000, expected about ${expected.toInt()}: ${counts.toList()}",
            )
        }
    }

    /**
     * The bound is the *config's* column count, not the default's, because
     * `board.cols` travels inside the state and a run played on a remote board
     * width would otherwise spawn off the edge of it.
     */
    @Test
    fun theEntryColumnIsNeverOutOfBoundsAtAnyBoardWidth() {
        listOf(1, 2, 3, 5, 9).forEach { cols ->
            val narrow = config.copy(cols = cols)
            val board = Board.empty(cols, narrow.rows)
            var rng = Rng(cols * 104_729L)
            repeat(2_000) { index ->
                val draw = Spawn.draw(rng, level = 1, board, index + 3, false, narrow)
                assertTrue(
                    draw.column in 0 until cols,
                    "a $cols-column board spawned in column ${draw.column}",
                )
                rng = draw.rng
            }
        }
    }

    /**
     * The one thing a uniform draw and a centre spawn cannot both satisfy, and
     * the whole point of the ruling.
     */
    @Test
    fun consecutiveRunsDoNotAllOpenInTheSameColumn() {
        val opened = (0 until 200).map { seed ->
            Cascade.newGame(seed.toLong()).falling?.cell?.col
        }.toSet()

        assertEquals((0 until config.cols).toSet(), opened, "every column has to be an opening")
    }

    @Test
    fun aBlockAlwaysEntersAtRowZero() {
        var state = Cascade.newGame(seed = 77)
        repeat(60) {
            assertEquals(0, state.falling?.cell?.row, "a block entered below the top row")
            state = Cascade.apply(state, Input.Lock).state
            if (state.isOver) return
        }
    }

    private fun drawManyColumns(count: Int): List<Int> {
        val board = boardOf("2048 . . . .")
        var rng = Rng(982_451_653L)
        return (0 until count).map { index ->
            val draw = Spawn.draw(
                rng = rng,
                level = 1,
                board = board,
                drawIndex = index + config.specialSuppressedDraws,
                lastWasSpecial = false,
                config = config.copy(specialRates = emptyList()),
            )
            rng = draw.rng
            draw.column
        }
    }

    private fun alwaysSpecial() = SpecialRate.Default.map { it.copy(perMille = 1000 / 3) }

    private fun drawMany(
        level: Int,
        board: Board,
        count: Int,
        allowSpecials: Boolean = false,
    ): List<Block> {
        val rates = if (allowSpecials) config.specialRates else emptyList()
        var rng = Rng(level * 7919L + count)
        var lastSpecial = false
        return (0 until count).map { index ->
            val draw = Spawn.draw(
                rng = rng,
                level = level,
                board = board,
                drawIndex = index + config.specialSuppressedDraws,
                lastWasSpecial = lastSpecial,
                config = config.copy(specialRates = rates),
            )
            rng = draw.rng
            lastSpecial = draw.wasSpecial
            draw.block
        }
    }
}
