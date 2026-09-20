package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
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

    /**
     * The ink rule, both halves of it.
     *
     * A tier either takes the handoff's hue-matched ink — in which case the only
     * thing to check is that it is readable, which
     * [everyNumeralClearsTheReadabilityFloor] does — or it falls back, in which
     * case the fallback has to have picked the better of the two flat inks. The
     * second half is the assertion that used to cover the whole file, and it
     * still catches the same bug: an ink assigned by eye onto a face too close
     * to it.
     */
    @Test
    fun everyFallbackInkIsTheHigherContrastOfTheTwoCandidates() {
        forEachTier { choice, value, style ->
            if (style.ink != DARK_INK && style.ink != LIGHT_INK) return@forEachTier
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
     * The design's ink is the *preferred* one, not the winner of a tie-break.
     *
     * Worth its own test because the difference is invisible in the rendered
     * result and total in the code: a best-of-three would reject the hue-matched
     * ink on all eleven default tiers, since the flat near-black beats it on
     * contrast every time. If somebody "simplifies" [inkFor] into a max, this is
     * the only thing that notices.
     */
    @Test
    fun theShippedRampTakesTheDesignsHueMatchedInkOnEveryTier() {
        BlockPalettes.Default.styles.forEachIndexed { tier, style ->
            assertTrue(
                style.ink != DARK_INK && style.ink != LIGHT_INK,
                "the ${TIER_VALUES[tier]} fell back to a flat ink; the design specifies " +
                    "oklch(0.26 0.07 H) and its faces are light enough to take it",
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
        assertEquals(LIGHT_INK, inkFor(Color(0xFF15122B), hue = 260f))
        assertEquals(
            Oklch(0.26f, 0.07f, 90f).toColor(),
            inkFor(Color(0xFFF6C445), hue = 90f),
            "a face this light takes the design's own ink, not a flat one",
        )

        val deepBlue = BlockPalettes.Deuteranopia[2]
        assertEquals(LIGHT_INK, deepBlue.ink, "the deuteranopia 2 is too dark for a dark numeral")
        assertEquals(inkFor(deepBlue.face, deepBlue.face.toOklch().hue), deepBlue.ink)

        val periwinkle = BlockPalettes.Deuteranopia[128]
        assertEquals(DARK_INK, periwinkle.ink, "the deuteranopia 128 wants the flat dark numeral")
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
        accessibilityPalettes().forEach { (choice, palette) ->
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
        accessibilityPalettes().forEach { (choice, palette) ->
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
        accessibilityPalettes().forEach { (choice, palette) ->
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

    /**
     * The same claim for [BlockPalettes.HighContrast], which is the one palette
     * that has to hold it against **all three** deficiencies rather than one.
     *
     * **It used not to hold it against any of them, and nothing was watching.**
     * [eachColourVisionPaletteSurvivesItsOwnDeficiency] pairs each ramp with its
     * own deficiency, and high contrast has no own deficiency, so it was the only
     * accessibility palette never put through the simulation at all. Measured, its
     * 512 and its 1024 — *neighbouring tiers* — collapsed to ΔE 1.6 under
     * protanopia, 8.7 under deuteranopia; its 2 and its 4 to 6.7 under tritanopia.
     * Adjacent tiers at ΔE 1.4 are the same colour, on a palette a player reaches
     * for because the default is not working for them.
     *
     * **The any-pair floor here is lower than [SimulatedCollisionFloor] on
     * purpose.** The three targeted ramps each spend their whole budget on one
     * deficiency and can hold 16 under it. This one is asked to hold all three at
     * once, which is a strictly harder problem — a dichromat loses a different
     * axis in each — and the ramp that clears the *neighbour* floor under every
     * one of them lands at ΔE 14.2 for its worst non-adjacent pair, under
     * deuteranopia. Neighbours are the pair a player has to separate; a 4 against
     * a 512 is not.
     */
    @Test
    fun theHighContrastRampSurvivesAllThreeDeficiencies() {
        ColorVision.entries.forEach { vision ->
            val seen = BlockPalettes.HighContrast.seenBy(vision)

            val neighbours = seen.closestNeighbours()
            assertTrue(
                neighbours.distance >= NeighbourSeparationFloor,
                "under $vision, high contrast's ${neighbours.description} collapse to " +
                    "${neighbours.distance}, under $NeighbourSeparationFloor",
            )

            val pair = seen.closestPair()
            assertTrue(
                pair.distance >= HighContrastSimulatedFloor,
                "under $vision, high contrast's ${pair.description} collapse to " +
                    "${pair.distance}, under $HighContrastSimulatedFloor",
            )
        }
    }

    /**
     * How far each accessibility ramp sits from the shipped one, pinned rather
     * than floored.
     *
     * The owner's report was that the options "use colors that are all very
     * similar", and the settings screen is where that is seen: each row carries a
     * five-tier swatch of its ramp, and two rows that look alike make the choice
     * meaningless. High contrast is the one that reads as a tint of the default
     * rather than as another board, and this is why: it is built on the **same
     * hue wheel**, one hue per tier in the same order, so tier for tier the two
     * are in the same colour family and only lightness and chroma separate them.
     * The other three swap the wheel for a two-axis ramp and land three times
     * further away.
     *
     * Retuning high contrast for colour vision moved it from 34.0 to 40.9 and it
     * is still the closest of the four. Pulling it past the others means changing
     * its hues, and every rotation of that wheel was measured: all 23 of them cost
     * more separation under the three deficiencies than they buy here. So the
     * number is recorded instead of floored — there is no floor to set that this
     * ramp could pass and that would still mean anything.
     *
     * **Deuteranopia and protanopia sit ΔE 16 from each other and that is not a
     * defect.** Both deficiencies leave the same blue/gold axis intact, so both
     * ramps are built on it. A player picks the one for their own eyes and never
     * sees the other.
     */
    @Test
    fun theAccessibilityRampsSitWhereTheyDoFromTheShippedOne() {
        val default = BlockPalettes.Default.styles.map { it.face }
        mapOf(
            BlockPaletteChoice.Deuteranopia to DeuteranopiaFromDefault,
            BlockPaletteChoice.Protanopia to ProtanopiaFromDefault,
            BlockPaletteChoice.Tritanopia to TritanopiaFromDefault,
            BlockPaletteChoice.HighContrast to HighContrastFromDefault,
        ).forEach { (choice, expected) ->
            val mean = BlockPalettes[choice].styles
                .mapIndexed { tier, style -> perceptualDistance(style.face, default[tier]) }
                .average()
                .toFloat()
            assertNear(expected, mean, "$choice's mean distance from the shipped ramp", MeanPinTolerance)
        }
    }

    /**
     * **The design ramp, measured, including where it misses.**
     *
     * The handoff replaced C2's hill-climbed default ramp with an authored one,
     * and three of the floors the other four palettes hold do not survive that.
     * Loosening the floors to make the suite green would delete the finding, and
     * deleting the finding is the expensive move: it means the next person reads
     * a passing suite and believes the shipped ramp holds a separation it does
     * not.
     *
     * So the numbers are pinned instead. Every one of these is a *measurement*
     * of the shipped ramp, and the assertions are two-sided — a retune that
     * improves a number fails this test as loudly as one that worsens it, and it
     * should, because either way the ramp is no longer the one that was drawn.
     *
     * What each miss actually is:
     *
     * - **Lightness span 0.19 against a floor of 0.45.** Not a mistake, a
     *   deliberate property: `L` is 0.78 on every tier below 2048, so the board
     *   reads as one set of objects lit the same way. It also means lightness
     *   carries nothing, and lightness is the axis that survives every colour
     *   vision deficiency.
     * - **16 / 32 at ΔE 22.6 against a floor of 24.** The closest neighbouring
     *   pair. Thirty degrees of hue where the rest of the ramp uses forty-five to
     *   sixty.
     * - **2 / 2048 at ΔE 14.6 against a floor of 17.** The closest pair anywhere,
     *   and the interesting one: the 2048 is the brand yellow, five degrees of
     *   hue from the 2. It is mitigated by the fact that a 2048 bursts its row
     *   immediately, so the two are rarely on the board together — but a 2 and a
     *   2048 side by side is a pair the ramp cannot separate.
     *
     * The mitigation across all three is the numeral, which every tile carries
     * and which no palette setting can turn off, plus the four ramps below for
     * the player who cannot use hue at all. Whether that is enough is an owner
     * decision, not this test's.
     */
    @Test
    fun theDesignRampIsWhereTheHandoffPutIt() {
        val palette = BlockPalettes.Default
        val faces = palette.styles.map { it.face }

        val neighbours = faces.closestNeighbours()
        assertEquals("16 and 32", neighbours.description)
        assertNear(DesignRampClosestNeighbours, neighbours.distance, "closest neighbouring tiers")

        val pair = faces.closestPair()
        assertEquals("2 and 2048", pair.description)
        assertNear(DesignRampClosestPair, pair.distance, "closest pair anywhere")

        val luminances = faces.map { it.luminance() }
        assertNear(DesignRampLuminanceSpan, luminances.max() - luminances.min(), "luminance span", LuminancePinTolerance)

        val worstInk = palette.styles.minOf { contrastRatio(it.ink, it.face) }
        assertNear(DesignRampWorstInk, worstInk, "worst numeral contrast")
    }

    /**
     * The half of the design ramp's cost that no floor in this file was watching,
     * and the reason the four accessibility palettes are not optional.
     *
     * A ramp of eleven hues at one constant lightness has, by construction,
     * nothing left once a cone is missing. Under deuteranopia the 128 and the 256
     * land ΔE 0.67 apart, which is the same colour.
     *
     * C2's authored ramp was not much better here — it collapsed to 6.15 under
     * protanopia — so this is not a regression the handoff introduced so much as
     * one it deepened. Neither ramp was ever the answer for a colour-blind
     * player; the palettes are.
     */
    @Test
    fun theDesignRampCollapsesUnderEachDeficiency() {
        mapOf(
            ColorVision.Deuteranopia to (DesignRampUnderDeuteranopia to "128 and 256"),
            ColorVision.Protanopia to (DesignRampUnderProtanopia to "64 and 128"),
            ColorVision.Tritanopia to (DesignRampUnderTritanopia to "256 and 512"),
        ).forEach { (vision, expected) ->
            val (distance, description) = expected
            val worst = BlockPalettes.Default.seenBy(vision).closestPair()
            assertEquals(description, worst.description, "the worst pair under $vision moved")
            assertNear(distance, worst.distance, "the worst pair under $vision")
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

    @Test
    fun everySpecialHasAStyle() {
        BlockPalettes.all.forEach { (choice, palette) ->
            BlockSpecial.entries.forEach { special ->
                assertEquals(
                    SPECIAL_STYLES.getValue(special),
                    palette[special],
                    "$choice does not paint $special the shared way",
                )
            }
        }
    }

    /**
     * A special carries a mark rather than a numeral, and a mark that cannot be
     * read against its own face is a special the player has to guess at.
     */
    @Test
    fun everySpecialMarkClearsTheReadabilityFloor() {
        BlockSpecial.entries.forEach { special ->
            val style = SPECIAL_STYLES.getValue(special)
            val ratio = contrastRatio(style.ink, style.face)
            assertTrue(
                ratio >= InkContrastFloor,
                "$special draws its mark at $ratio:1, under $InkContrastFloor",
            )
        }
    }

    /**
     * The one that matters, and the reason the specials are one shared set rather
     * than five.
     *
     * A special is never a tier. Reading one as a numeric block is a worse mistake
     * than confusing two tiers, because the player does not merely misjudge a
     * merge — they plan a merge that cannot happen. So all three are held to the
     * *neighbour* floor against **every** tier face in **all five** palettes, not
     * the looser any-pair floor the ramps use among themselves.
     *
     * [BlockSpecial.Stone] is the tight one, and deliberately so: it is the only
     * block on the board that draws nothing at all, so its face is the entire
     * signal. It is achromatic for exactly that reason.
     */
    @Test
    fun noSpecialCollidesWithAnyTierInAnyPalette() {
        BlockSpecial.entries.forEach { special ->
            val face = SPECIAL_STYLES.getValue(special).face
            BlockPalettes.all.forEach { (choice, palette) ->
                palette.styles.forEachIndexed { tier, style ->
                    val distance = perceptualDistance(face, style.face)
                    assertTrue(
                        distance >= NeighbourSeparationFloor,
                        "$special sits $distance from $choice's ${TIER_VALUES[tier]}, under the " +
                            "floor of $NeighbourSeparationFloor",
                    )
                }
            }
        }
    }

    @Test
    fun theThreeSpecialsAreObviouslyDifferentFromEachOther() {
        val faces = BlockSpecial.entries.map { it to SPECIAL_STYLES.getValue(it).face }
        faces.forEachIndexed { i, (a, faceA) ->
            faces.drop(i + 1).forEach { (b, faceB) ->
                val distance = perceptualDistance(faceA, faceB)
                assertTrue(
                    distance >= NeighbourSeparationFloor,
                    "$a and $b are only $distance apart, under $NeighbourSeparationFloor",
                )
            }
        }
    }

    /**
     * The specials shared across all five palettes have to survive the three
     * deficiencies too, but against a lower floor than [SimulatedCollisionFloor]
     * would suggest and lower than the ramps are held to.
     *
     * That is argued rather than conceded. A ramp asks the player to *order*
     * eleven colours, which is a job colour alone has to do. A special asks only
     * "is this one of them", and the mark on the face — or, for a Stone, the
     * conspicuous absence of one — answers that before the colour is consulted.
     * The floor here is what stops a special becoming genuinely indistinguishable
     * from a tier, not what makes it the primary signal.
     */
    @Test
    fun theSpecialsSurviveEachDeficiency() {
        mapOf(
            BlockPaletteChoice.Deuteranopia to ColorVision.Deuteranopia,
            BlockPaletteChoice.Protanopia to ColorVision.Protanopia,
            BlockPaletteChoice.Tritanopia to ColorVision.Tritanopia,
        ).forEach { (choice, vision) ->
            val tiers = BlockPalettes[choice].styles.map { it.face.asSeenBy(vision) }
            val specials = BlockSpecial.entries.map { it to SPECIAL_STYLES.getValue(it).face.asSeenBy(vision) }

            specials.forEach { (special, face) ->
                tiers.forEachIndexed { tier, seen ->
                    val distance = perceptualDistance(face, seen)
                    assertTrue(
                        distance >= SpecialSimulatedFloor,
                        "under $vision, $special and $choice's ${TIER_VALUES[tier]} collapse to " +
                            "$distance, under $SpecialSimulatedFloor",
                    )
                }
            }

            specials.forEachIndexed { i, (a, faceA) ->
                specials.drop(i + 1).forEach { (b, faceB) ->
                    val distance = perceptualDistance(faceA, faceB)
                    assertTrue(
                        distance >= SpecialSimulatedFloor,
                        "under $vision, $a and $b collapse to $distance, under $SpecialSimulatedFloor",
                    )
                }
            }
        }
    }

    private fun accessibilityPalettes(): Map<BlockPaletteChoice, BlockPalette> =
        BlockPalettes.all.filterKeys { it != BlockPaletteChoice.Default }

    private fun assertNear(
        expected: Float,
        actual: Float,
        what: String,
        tolerance: Float = PinTolerance,
    ) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "$what measured $actual, pinned at $expected. If the ramp was retuned on purpose, " +
                "re-derive this number by running it rather than pasting the value out of this " +
                "message, and say in the commit why the design moved.",
        )
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

        /**
         * Below [SimulatedCollisionFloor], and argued in
         * [theHighContrastRampSurvivesAllThreeDeficiencies] rather than conceded:
         * holding one ramp against all three deficiencies at once costs more than
         * holding three ramps against one each.
         */
        const val HighContrastSimulatedFloor = 14f

        const val LuminanceSpanFloor = 0.45f

        /**
         * Below [NeighbourSeparationFloor] on purpose — see
         * [theSpecialsSurviveEachDeficiency] for why a special is allowed to be
         * closer than a tier is once the mark is doing the work.
         */
        const val SpecialSimulatedFloor = 20f

        /**
         * Wide enough to absorb `Float` rounding through two colour-space
         * conversions, narrow enough that any real retune of a tier trips it.
         */
        const val PinTolerance = 0.05f

        /** Luminance runs 0..1, so it needs a tolerance two orders tighter. */
        const val LuminancePinTolerance = 0.005f

        /** A mean over eleven ΔE values moves less than any one of them, but not by much. */
        const val MeanPinTolerance = 0.5f

        const val DeuteranopiaFromDefault = 71.5f
        const val ProtanopiaFromDefault = 73.6f
        const val TritanopiaFromDefault = 68.6f
        const val HighContrastFromDefault = 40.9f

        const val DesignRampClosestNeighbours = 22.62f
        const val DesignRampClosestPair = 14.64f
        const val DesignRampLuminanceSpan = 0.1867f
        const val DesignRampWorstInk = 7.16f

        const val DesignRampUnderDeuteranopia = 0.67f
        const val DesignRampUnderProtanopia = 4.01f
        const val DesignRampUnderTritanopia = 1.46f
    }
}
