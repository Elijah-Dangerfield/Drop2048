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

    @Test
    fun specialsAreSuppressedForTheFirstThreeDrawsOfARun() {
        (0 until 40).forEach { seed ->
            val state = Cascade.newGame(seed.toLong(), config.copy(specialRates = alwaysSpecial()))
            val firstThree = listOf(state.falling?.block) + state.preview
            assertTrue(
                firstThree.none { it is SpecialBlock },
                "seed $seed opened with a special: $firstThree",
            )
        }
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
