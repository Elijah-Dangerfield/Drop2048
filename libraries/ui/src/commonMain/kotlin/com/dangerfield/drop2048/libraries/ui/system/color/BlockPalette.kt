package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * How one tier of block is painted.
 *
 * [ink] and [edge] are derived from [face] by [blockStyle] rather than authored,
 * so adding a tier or retuning a palette is one `L C H` triple. Hand-picking ink
 * is the mistake this exists to prevent: Sodogku's KDoc records that assigning
 * its region inks by eye put light ink on three fills dark enough that the mark
 * nearly vanished, an error invisible in code review and obvious the moment the
 * palette was rendered as a strip.
 */
@Immutable
data class BlockStyle(
    /** The block's fill. */
    val face: Color,

    /** The numeral printed on the face. Always printed — see SPEC 5.1. */
    val ink: Color,

    /** The darker lip under the face, which is what gives the block thickness. */
    val edge: Color,
)

/**
 * The eleven block tiers, 2 through 2048, as one swappable set.
 *
 * An interface rather than a flat object because the accessibility settings pick
 * between five of these at runtime (SPEC 16). Sodogku's `RegionPalette` is an
 * `object` with a `colorblind: Boolean` drilled through every call site, which
 * cannot express five selectable palettes and puts the setting in the signature
 * of everything that draws a cell. Here the palette is chosen once and read from
 * [LocalBlockPalette].
 *
 * Three constraints shaped every ramp in here, and all three are asserted by
 * `BlockPaletteTest` rather than believed:
 *
 * 1. **Adjacent tiers have to be obviously different.** A 4 landing next to an 8
 *    is the pair a player actually has to separate; a 4 next to a 512 is not.
 *    A ramp holds a floor of ΔE 24 between neighbouring tiers, and 17 between
 *    *any* two.
 * 2. **The numeral has to be readable on every face.** Every ink clears 4.5:1
 *    against the face it sits on, which is WCAG AA for body text and generous
 *    for a numeral drawn at block size.
 * 3. **Lightness has to carry what hue cannot.** Roughly 8% of men cannot
 *    separate red from green, so a ramp separated only by hue collapses. A
 *    palette spans at least 0.45 of relative luminance.
 *
 * **[BlockPalettes.Default] no longer holds 1 or 3, because the design handoff
 * overrode it.** That is a live finding rather than a regression to fix quietly:
 * the exact numbers, and which tier pairs they land on, are pinned in
 * `BlockPaletteTest` so nobody can move them without saying so. The four
 * accessibility ramps below still hold all three, and they are the answer for
 * the player the floors were written for.
 *
 * Colour is never the only signal regardless: the number is on the face, always.
 */
@Immutable
interface BlockPalette {

    /** Indexed by tier, `styles[0]` being the 2 and `styles[10]` the 2048. */
    val styles: List<BlockStyle>

    /**
     * The style for a block worth [value].
     *
     * Clamps rather than throwing. A value off the end of the ramp is an engine
     * bug, and a wrongly-coloured block is a far better failure than a crash on
     * the board screen — the numeral on the face still tells the truth.
     */
    operator fun get(value: Int): BlockStyle =
        styles[TIER_VALUES.indexOf(value).takeIf { it >= 0 } ?: styles.lastIndex]

    /**
     * The three specials (SPEC 5.2), which are the same three colours in every
     * palette.
     *
     * Shared rather than authored five times, and that is a design decision
     * rather than a shortcut. A special is not a tier: it has no value, it is
     * not part of the ramp, and it is identified by its mark rather than by
     * where it sits between two other colours. Giving each palette its own
     * Stone would mean fifteen more hexes to hold against fifteen more floors,
     * to express a difference no player can act on.
     *
     * What they still have to clear is asserted by `BlockPaletteTest`: each
     * reads its own mark, and none of the three collides with any of the
     * fifty-five tier faces. [BlockSpecial.Stone] carries the strictest version
     * of that, because "not a number" is the entire content of a Stone.
     */
    val specials: Map<BlockSpecial, BlockStyle> get() = SPECIAL_STYLES

    /** The style for a [special]. */
    operator fun get(special: BlockSpecial): BlockStyle = specials.getValue(special)
}

/**
 * The blocks that arrive in place of a value block (SPEC 5.2).
 *
 * [mark] is what the face draws instead of a numeral, and it is on the enum
 * rather than on the drawing code because it is the same class of decision as
 * the colour: what a Bomb looks like is a design-system answer, and a feature
 * that could pick its own would eventually pick two.
 */
enum class BlockSpecial(val mark: BlockMark) {

    /** Takes a neighbour's value doubled. Marked with a star: it can become anything. */
    Wildcard(BlockMark.Star),

    /** Destroys itself and its four orthogonal neighbours. Marked with a fused charge. */
    Bomb(BlockMark.Fuse),

    /**
     * An obstacle with no value at all.
     *
     * The only block on the board that draws **nothing** on its face, which is
     * the point: a Stone is defined by the absence of a number, and giving it a
     * mark would make it look like a special that does something.
     */
    Stone(BlockMark.None),
}

/** What a special draws on its face. Geometry, not a glyph — see `BlockFace`. */
enum class BlockMark { Star, Fuse, None }

/** The values a block can hold, low to high. Powers of two, 2 through 2048 (SPEC 5.1). */
val TIER_VALUES: List<Int> = listOf(2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)

/** Which of the five palettes is in play. Persisted by settings, defaulted by [BlockPalettes]. */
enum class BlockPaletteChoice {
    Default,
    Deuteranopia,
    Protanopia,
    Tritanopia,
    HighContrast,
}

object BlockPalettes {

    /**
     * The shipped ramp, and it is now the **design's** ramp rather than a derived
     * one.
     *
     * C2 hill-climbed eleven faces against the constraint set in
     * [BlockPalette]'s KDoc, which was the right answer while nobody had drawn
     * the game. The design handoff draws it, fixes one hue per tier, and holds
     * lightness and chroma constant across the whole ramp so the board reads as
     * one set of objects lit the same way rather than as a ladder that gets
     * brighter. `L/C` is `0.78 / 0.15` below 2048 and `0.85 / 0.17` at 2048, which
     * is the only tier allowed to glow.
     *
     * **That is a design decision that overrides a measured one, and it costs
     * three of the floors this file's tests hold.** The numbers are recorded in
     * `BlockPaletteTest.theDesignRampIsWhereTheHandoffPutIt` rather than argued
     * about here, because the point of writing them down is that they move
     * loudly. The short version: a constant lightness cannot span lightness, the
     * 16 and the 32 sit 30° apart rather than the 45–60° the rest of the ramp
     * uses, and the 2048 is the brand yellow, which is five degrees of hue from
     * the 2.
     *
     * The mitigation the design leans on is real and is the reason this is
     * arguable rather than wrong: every tile carries its own numeral, and the
     * four palettes below exist for the player who cannot use the hue at all.
     */
    val Default: BlockPalette = paletteOf(
        0.78f, 0.15f, 85f, 0.78f, 0.15f, 55f,
        0.78f, 0.15f, 30f, 0.78f, 0.15f, 5f,
        0.78f, 0.15f, 340f, 0.78f, 0.15f, 300f,
        0.78f, 0.15f, 265f, 0.78f, 0.15f, 230f,
        0.78f, 0.15f, 200f, 0.78f, 0.15f, 165f,
        0.85f, 0.17f, 90f,
    )

    /**
     * Deuteranopia: green-blind, the most common form.
     *
     * Not the default ramp run through a filter. Every tier alternates across
     * the blue/gold axis, which is the one axis a deuteranope still has, and
     * lightness climbs monotonically with tier so the ramp reads as a ladder
     * even to somebody who sees two hues in it. Authored against a Viénot 1999
     * dichromat simulation and asserted against one in the test: a palette that
     * only separates in normal vision is exactly the failure this is for.
     *
     * The colours are unchanged from C2 — they were hill-climbed and they hold
     * every floor. What changed is that they are now written in the same L/C/H
     * terms the design ramp is, and get their ink and their hard shadow from the
     * same derivation, so all five palettes are one system rather than one system
     * plus four exceptions.
     */
    val Deuteranopia: BlockPalette = paletteOf(
        0.4472f, 0.1176f, 256.4f, 0.6364f, 0.1209f, 84.5f,
        0.4527f, 0.1762f, 266.8f, 0.8255f, 0.1616f, 93.0f,
        0.6685f, 0.1300f, 240.0f, 0.7723f, 0.1119f, 72.2f,
        0.6128f, 0.1627f, 274.3f, 0.9076f, 0.1567f, 112.7f,
        0.8202f, 0.0879f, 234.5f, 0.8826f, 0.0711f, 83.5f,
        0.9039f, 0.0173f, 256.3f,
    )

    /**
     * Protanopia: red-blind, and dimmer at the long-wavelength end.
     *
     * The same blue/gold strategy as [Deuteranopia] but not the same colours,
     * because the two deficiencies are not the same shape: a protanope sees deep
     * reds and oranges as much darker than a deuteranope does, so the warm half
     * sits further toward yellow-green and never reaches orange.
     */
    val Protanopia: BlockPalette = paletteOf(
        0.4640f, 0.1175f, 251.2f, 0.6257f, 0.1228f, 104.4f,
        0.5184f, 0.1616f, 258.0f, 0.8966f, 0.2007f, 118.8f,
        0.7034f, 0.1219f, 229.5f, 0.8199f, 0.1540f, 102.2f,
        0.6465f, 0.1346f, 258.6f, 0.8861f, 0.1299f, 113.5f,
        0.8144f, 0.0631f, 233.2f, 0.8856f, 0.0866f, 115.9f,
        0.9139f, 0.0162f, 227.0f,
    )

    /**
     * Tritanopia: blue-blind, and rare enough that it is usually the one that
     * gets skipped.
     *
     * Blue and gold is exactly the axis a tritanope has lost, so this ramp is
     * built on the other one: crimson and pink against teal and cyan, again with
     * lightness climbing by tier.
     */
    val Tritanopia: BlockPalette = paletteOf(
        0.5329f, 0.0840f, 200.3f, 0.4676f, 0.1746f, 26.0f,
        0.6847f, 0.1230f, 224.4f, 0.5719f, 0.2182f, 15.5f,
        0.8335f, 0.1403f, 195.9f, 0.6475f, 0.1909f, 34.5f,
        0.7667f, 0.0647f, 222.2f, 0.7093f, 0.1555f, 5.1f,
        0.8958f, 0.1011f, 179.9f, 0.7670f, 0.0935f, 25.2f,
        0.9260f, 0.0201f, 196.8f,
    )

    /**
     * High contrast: separate on **lightness** first and hue second, so the ramp
     * holds up for a player who cannot use the hue.
     *
     * ### It used to be eleven pastels, and that was the bug
     *
     * The original ramp put every face in a narrow light band and let hue do all
     * the work between tiers. It measured beautifully in normal vision — ΔE 29
     * between the closest neighbours — and it fell apart the moment a cone was
     * missing, because hue is exactly what a dichromat loses. Its 512 and its
     * 1024 sat **ΔE 1.6 apart under protanopia** and 8.7 under deuteranopia; its
     * 2 and its 4 sat 6.7 apart under tritanopia. Those are neighbouring tiers,
     * which is the one pair a player genuinely has to separate, and 1.6 is the
     * same colour.
     *
     * Nothing caught it because `BlockPaletteTest` paired each ramp with the one
     * deficiency it was authored for, and this ramp is authored for none — so it
     * was the only accessibility palette never put through a simulation at all.
     * `theHighContrastRampSurvivesAllThreeDeficiencies` is the test that now does.
     *
     * ### What changed, and what it cost
     *
     * The eleven hues are **untouched**, and the 2048 is still the near-white
     * capstone every palette in here ends on. The full turn is what makes the
     * ramp read as a set rather than as a gradient, and every 15° rotation of it
     * was measured: all twenty-three cost more separation under the three
     * deficiencies than they buy anywhere else.
     *
     * What moved is lightness and chroma. The ramp now alternates between a pale
     * band around `L 0.80–0.95` and a deep one around `L 0.48–0.56`, so every
     * adjacent pair differs by a lightness step no deficiency can take away. The
     * worst neighbouring pair is now ΔE 30.2 under protanopia, against 1.6.
     *
     * **The cost is the pastel character, and it is worth naming rather than
     * glossing.** Eleven light faces separated by hue and a ramp that survives
     * dichromacy are contradictory requirements, not a tuning problem: one asks
     * hue to carry eleven steps, the other says hue carries nothing. Half the
     * board is now dark tiles with light numerals. Every one of them still clears
     * 3:1 against the well behind it, which is the constraint that kept the deep
     * band from going darker still.
     *
     * "High contrast" is now true of the blocks against each other — ΔE 38.2
     * between the closest neighbours, against 29.0 — rather than only of the
     * numerals against their blocks.
     *
     * ### What is still not true of it
     *
     * It does **not** have the widest ink-to-face margin of the five. The design
     * ramp does, at 7.2 against this one's 5.1. The old KDoc claimed it did and
     * was already wrong before this retune.
     *
     * It is also still the accessibility ramp nearest the shipped one (mean
     * ΔE 40.9 against 69–74 for the other three), because it is built on the same
     * hue wheel. `theAccessibilityRampsSitWhereTheyDoFromTheShippedOne` records
     * that rather than hiding it.
     */
    val HighContrast: BlockPalette = paletteOf(
        0.8948f, 0.1220f, 103.9f, 0.4924f, 0.0739f, 55.5f,
        0.7271f, 0.1208f, 13.8f, 0.5555f, 0.2410f, 340.3f,
        0.7956f, 0.1634f, 318.4f, 0.5087f, 0.1749f, 290.2f,
        0.8130f, 0.0533f, 257.7f, 0.4777f, 0.0803f, 199.5f,
        0.8842f, 0.1951f, 161.5f, 0.6882f, 0.1956f, 139.8f,
        0.9500f, 0.0180f, 197.1f,
    )

    /** Every palette, in the order the settings screen offers them. */
    val all: Map<BlockPaletteChoice, BlockPalette> = mapOf(
        BlockPaletteChoice.Default to Default,
        BlockPaletteChoice.Deuteranopia to Deuteranopia,
        BlockPaletteChoice.Protanopia to Protanopia,
        BlockPaletteChoice.Tritanopia to Tritanopia,
        BlockPaletteChoice.HighContrast to HighContrast,
    )

    operator fun get(choice: BlockPaletteChoice): BlockPalette = all.getValue(choice)
}

/**
 * Defaults to the shipped ramp so previews, screenshot tests and any composable
 * that draws a block need provide nothing.
 */
val LocalBlockPalette = staticCompositionLocalOf { BlockPalettes.Default }

/**
 * A tier's face, and the ink and the hard shadow the design derives from it.
 *
 * Everything below the face is a derivation, and all three derivations come
 * from the handoff rather than from taste:
 *
 * - the shadow is the same colour at `L - 0.22`, which is what makes a tile look
 *   like a solid object with a side rather than a rectangle with a drop shadow;
 * - the ink is `oklch(0.26 0.07 H)` — the tier's own hue, taken almost to black,
 *   so a numeral belongs to its tile instead of being one grey printed eleven
 *   times.
 *
 * The one place this departs from the handoff is [inkFor]'s fallback, and it has
 * to: the handoff only ever draws light tiles, so its ink rule has never met a
 * face dark enough to swallow it. Three of the four accessibility ramps have
 * exactly that. See [inkFor].
 */
fun blockStyle(face: Oklch): BlockStyle {
    val color = face.toColor()
    return BlockStyle(face = color, ink = inkFor(color, face.hue), edge = face.darker(ShadowDrop).toColor())
}

/** The same, for a colour that was authored as sRGB — the three specials. */
fun blockStyle(face: Color): BlockStyle = blockStyle(face.toOklch())

/**
 * The design's hue-matched ink, unless that ink cannot be read on this face.
 *
 * Prefer-and-fall-back rather than best-of-three, and the order is the whole
 * point. Best-of-three would silently reject the handoff's ink on every one of
 * the eleven default tiers, because the flat near-black beats it on contrast
 * everywhere — the design would be overruled by a tie-break it never entered.
 * Preferring it means the shipped ramp gets exactly the ink that was drawn, and
 * the fallback only ever fires where the design has nothing to say.
 *
 * It fires on twelve tiers in total, every one of them on one of the three
 * colour-vision ramps, and never on the design ramp or on high contrast.
 * `BlockPaletteTest` measures which.
 */
internal fun inkFor(face: Color, hue: Float): Color {
    val tinted = Oklch(TintedInkLightness, TintedInkChroma, hue).toColor()
    if (contrastRatio(face, tinted) >= InkContrastFloor) return tinted
    return if (contrastRatio(face, DARK_INK) >= contrastRatio(face, LIGHT_INK)) DARK_INK else LIGHT_INK
}

/** How far below the face the hard shadow sits, in OKLCH lightness. */
const val ShadowDrop: Float = 0.22f

/**
 * WCAG AA for body text. Stricter than a numeral drawn at tile size needs, and
 * it is also the switch [inkFor] uses, so loosening it would quietly change
 * which tiers take the design's ink.
 */
const val InkContrastFloor: Float = 4.5f

private const val TintedInkLightness = 0.26f
private const val TintedInkChroma = 0.07f

/** For faces light enough that a dark numeral reads better. The board's own near-black. */
internal val DARK_INK = Color(0xFF151024)

/** For faces dark enough that a light numeral reads better. Warm rather than pure white. */
internal val LIGHT_INK = Color(0xFFFFF7EA)

/**
 * The three specials, shared by every palette. See [BlockPalette.specials].
 *
 * Each hue was picked for the region no ramp reaches rather than for a mood, and
 * every number below is measured by `BlockPaletteTest` rather than asserted here:
 *
 * - **Wildcard** is an electric violet no ramp gets near — the closest any of the
 *   fifty-five tier faces comes is ΔE 36 (the default ramp's 128).
 * - **Bomb** is a near-black with a plum cast, and it is the easiest of the three:
 *   nothing in any ramp is remotely this dark, so its worst case is ΔE 42.
 * - **Stone** is the only achromatic block in the game, which is exactly how it
 *   reads as "not a number" without drawing one. Its worst case is ΔE 26, against
 *   the tritanopia ramp's 2.
 *
 * The specials are not put through the dichromat simulation as strictly as the
 * three colour-vision ramps are, and that is argued rather than overlooked. A
 * ramp asks a player to order eleven colours; a special asks only "is this one of
 * them", and the mark on the face answers that before the colour does.
 *
 * **This has to stay below [DARK_INK] and [LIGHT_INK].** Top-level properties in
 * a file initialise in declaration order, so declared above them it runs while
 * both are still zeroed and every special ends up with a fully transparent ink —
 * silently, at class-init time, with no warning anywhere. It was written that way
 * first, and the only reason it did not ship is that
 * `everySpecialMarkClearsTheReadabilityFloor` measured the ink rather than
 * trusting the derivation.
 */
val SPECIAL_STYLES: Map<BlockSpecial, BlockStyle> = mapOf(
    BlockSpecial.Wildcard to blockStyle(Color(0xFFA800FC)),
    BlockSpecial.Bomb to blockStyle(Color(0xFF241A26)),
    BlockSpecial.Stone to blockStyle(Color(0xFF686B76)),
)

/**
 * Eleven tiers as a flat run of `lightness, chroma, hue`.
 *
 * Flat rather than a list of triples because a palette then lays out as a
 * readable block of numbers where every column means the same thing down the
 * whole ramp, which is how you see at a glance that the design ramp holds L and
 * C constant and the accessibility ramps climb.
 */
private fun paletteOf(vararg components: Float): BlockPalette {
    require(components.size == TIER_VALUES.size * ComponentsPerTier) {
        "a palette needs $ComponentsPerTier components per tier for ${TIER_VALUES.size} tiers, " +
            "got ${components.size}"
    }
    val styles = TIER_VALUES.indices.map { tier ->
        val at = tier * ComponentsPerTier
        blockStyle(Oklch(components[at], components[at + 1], components[at + 2]))
    }
    return object : BlockPalette {
        override val styles: List<BlockStyle> = styles
    }
}

private const val ComponentsPerTier = 3
