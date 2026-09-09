package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Eleven tiers across five palettes is fifty-five chances to ship two colours
 * nobody can tell apart, and every one of them is invisible in a code review and
 * obvious the moment the ramp is rendered as a strip.
 *
 * So this asserts properties rather than hexes. Nothing here pins a colour;
 * retuning a palette is meant to be cheap. What it pins is that the numeral can
 * be read, that neighbouring tiers are different enough to act on at a glance,
 * that lightness carries what hue cannot, and — for the three colour-vision
 * ramps — that all of that survives the deficiency the ramp exists for.
 */
class BlockPaletteTest {

    @Test
    fun everyPaletteHasOneStylePerTier() {
        BlockPalettes.all.forEach { (choice, palette) ->
            assertEquals(TIER_VALUES.size, palette.styles.size, "$choice is not eleven tiers")
        }
    }

    @Test
    fun aTierLooksUpByItsValue() {
        val palette = BlockPalettes.Default
        TIER_VALUES.forEachIndexed { tier, value ->
            assertEquals(palette.styles[tier], palette[value], "the $value did not find tier $tier")
        }
    }

    /** A value off the end of the ramp is an engine bug, not a reason to crash the board. */
    @Test
    fun anUnknownValueClampsToTheTopTier() {
        assertEquals(BlockPalettes.Default.styles.last(), BlockPalettes.Default[4096])
        assertEquals(BlockPalettes.Default.styles.last(), BlockPalettes.Default[3])
    }

    @Test
    fun everyInkIsTheHigherContrastOfTheTwoCandidates() {
        forEachTier { choice, value, style ->
            val chosen = contrastRatio(style.ink, style.face)
            val rejected = contrastRatio(style.ink.other(), style.face)
            assertTrue(
                chosen >= rejected,
                "$choice's $value took the worse ink: ${style.ink} scores $chosen against " +
                    "${style.face}, the other candidate scores $rejected",
            )
        }
    }

    /**
     * What separates a derivation from a hardcoded constant.
     *
     * The assertion above is true of a palette that hardcodes one ink as well,
     * as long as every face happens to want that one. This feeds [inkFor] a face
     * dark enough that only the light ink can win and one light enough that only
     * the dark ink can, and checks a shipped tier against the derived value
     * rather than against a literal.
     */
    @Test
    fun theInkDerivationFlipsWithTheFace() {
        assertEquals(LIGHT_INK, inkFor(Color(0xFF15122B)))
        assertEquals(DARK_INK, inkFor(Color(0xFFF6C445)))

        val crimson = BlockPalettes.Default[32]
        assertEquals(inkFor(crimson.face), crimson.ink)
        assertEquals(LIGHT_INK, crimson.ink, "the 32 is dark enough to want the light numeral")
    }

    /** Colour is never the only signal, so the numeral has to be readable on every face. */
    @Test
    fun everyNumeralClearsTheReadabilityFloor() {
        forEachTier { choice, value, style ->
            val ratio = contrastRatio(style.ink, style.face)
            assertTrue(
                ratio >= InkContrastFloor,
                "$choice's $value prints its numeral at $ratio:1, under $InkContrastFloor",
            )
        }
    }

    /**
     * The pair a player actually has to separate is a tier and the tier above
     * it — a 4 landing on an 8. Two tiers six apart are never the confusion.
     */
    @Test
    fun neighbouringTiersClearTheSeparationFloor() {
        BlockPalettes.all.forEach { (choice, palette) ->
            val worst = palette.closestNeighbours()
            assertTrue(
                worst.distance >= NeighbourSeparationFloor,
                "$choice's ${worst.description} are only ${worst.distance} apart in CIELAB, " +
                    "under the floor of $NeighbourSeparationFloor",
            )
        }
    }

    /** Looser than the neighbour floor, because the numerals do the rest of the work. */
    @Test
    fun noTwoTiersAnywhereInARampCollide() {
        BlockPalettes.all.forEach { (choice, palette) ->
            val worst = palette.closestPair()
            assertTrue(
                worst.distance >= CollisionFloor,
                "$choice's ${worst.description} are only ${worst.distance} apart in CIELAB, " +
                    "under the floor of $CollisionFloor",
            )
        }
    }

    /**
     * The lightness ladder, which is what carries a player when hue does not.
     * Asserted as a span rather than per tier: any one face can be any
     * lightness, what matters is that the ramp is not all one.
     */
    @Test
    fun everyPaletteSpansLightness() {
        BlockPalettes.all.forEach { (choice, palette) ->
            val luminances = palette.styles.map { it.face.luminance() }
            val span = luminances.max() - luminances.min()
            assertTrue(span >= LuminanceSpanFloor, "$choice spans only $span of luminance")
        }
    }

    /**
     * The claim the three colour-vision palettes exist to make, checked the only
     * way it is worth checking. A ramp can hold ΔE 40 in normal vision and
     * collapse to 6 for the player it was designed for.
     */
    @Test
    fun eachColourVisionPaletteSurvivesItsOwnDeficiency() {
        mapOf(
            BlockPaletteChoice.Deuteranopia to ColorVision.Deuteranopia,
            BlockPaletteChoice.Protanopia to ColorVision.Protanopia,
            BlockPaletteChoice.Tritanopia to ColorVision.Tritanopia,
        ).forEach { (choice, vision) ->
            val seen = BlockPalettes[choice].seenBy(vision)
            val neighbours = seen.closestNeighbours()
            assertTrue(
                neighbours.distance >= NeighbourSeparationFloor,
                "under $vision, $choice's ${neighbours.description} collapse to " +
                    "${neighbours.distance}, under $NeighbourSeparationFloor",
            )
            val pair = seen.closestPair()
            assertTrue(
                pair.distance >= SimulatedCollisionFloor,
                "under $vision, $choice's ${pair.description} collapse to ${pair.distance}, " +
                    "under $SimulatedCollisionFloor",
            )
        }
    }

    /** The lip is what gives a block thickness, so it has to be darker than the face. */
    @Test
    fun everyEdgeIsDarkerThanItsFace() {
        forEachTier { choice, value, style ->
            assertTrue(
                style.edge.luminance() < style.face.luminance(),
                "$choice's $value has an edge no darker than its face",
            )
        }
    }

    private fun forEachTier(assertion: (BlockPaletteChoice, Int, BlockStyle) -> Unit) {
        BlockPalettes.all.forEach { (choice, palette) ->
            palette.styles.forEachIndexed { tier, style -> assertion(choice, TIER_VALUES[tier], style) }
        }
    }

    private fun BlockPalette.seenBy(vision: ColorVision): List<Color> =
        styles.map { it.face.asSeenBy(vision) }

    private fun BlockPalette.closestNeighbours() = styles.map { it.face }.closestNeighbours()

    private fun BlockPalette.closestPair() = styles.map { it.face }.closestPair()

    private fun List<Color>.closestNeighbours(): Closest =
        (0 until lastIndex)
            .map { Closest(it, it + 1, perceptualDistance(this[it], this[it + 1])) }
            .minBy { it.distance }

    private fun List<Color>.closestPair(): Closest =
        indices.flatMap { i ->
            (i + 1..lastIndex).map { j -> Closest(i, j, perceptualDistance(this[i], this[j])) }
        }.minBy { it.distance }

    private fun Color.other() = if (this == DARK_INK) LIGHT_INK else DARK_INK

    private data class Closest(val a: Int, val b: Int, val distance: Float) {
        val description: String get() = "${TIER_VALUES[a]} and ${TIER_VALUES[b]}"
    }

    private companion object {
        /** WCAG AA for body text, which is stricter than a numeral at block size needs. */
        const val InkContrastFloor = 4.5f

        /** Roughly where two large flat colours stop being obviously different. */
        const val NeighbourSeparationFloor = 24f

        const val CollisionFloor = 17f

        /**
         * A point below [CollisionFloor]. Two tiers far apart in a ramp, seen
         * through a deficiency, are allowed to be nearer than they are in normal
         * vision — the numerals separate them and nothing about the game asks a
         * player to compare a 4 with a 512 by colour.
         */
        const val SimulatedCollisionFloor = 16f

        const val LuminanceSpanFloor = 0.45f
    }
}
