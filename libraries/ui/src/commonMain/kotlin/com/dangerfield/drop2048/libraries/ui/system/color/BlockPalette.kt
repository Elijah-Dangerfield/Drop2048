package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * How one tier of block is painted.
 *
 * [ink] and [edge] are derived from [face] by [blockStyle] rather than authored,
 * so adding a tier or retuning a palette is one hex per tier. Hand-picking ink
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
 *    Every ramp holds a floor of ΔE 24 between neighbouring tiers, and 17
 *    between *any* two.
 * 2. **The numeral has to be readable on every face.** Every ink clears 4.5:1
 *    against the face it sits on, which is WCAG AA for body text and generous
 *    for a numeral drawn at block size.
 * 3. **Lightness has to carry what hue cannot.** Roughly 8% of men cannot
 *    separate red from green, so a ramp separated only by hue collapses. Every
 *    palette spans at least 0.45 of relative luminance.
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
     * The shipped ramp: sand through amber and orange into crimson, across the
     * pinks and violets, out to blue and teal, with the 2048 a pale gold that
     * exists nowhere else on the board.
     *
     * Warm at the bottom because that is where a run spends its time and warm
     * blocks on the deep indigo board read as sweets rather than as data. The
     * turn into violet at 128 is deliberate: it is the point where a player
     * starts building rather than clearing, and the board changing temperature
     * says so without a HUD element.
     *
     * The ink derivation genuinely branches on this ramp — four of the eleven
     * take the light ink — which is what keeps the assertion in
     * `everyInkIsTheHigherContrastCandidate` from being vacuously true of a
     * constant.
     */
    val Default: BlockPalette = paletteOf(
        0xFFF6E7C8, 0xFFF6C445, 0xFFF08A2C, 0xFFE24B2B, 0xFFA81E48, 0xFFE1559B,
        0xFF7B34C4, 0xFF3358D8, 0xFF21A9C4, 0xFF2E7D4F, 0xFFFFF089,
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
     */
    val Deuteranopia: BlockPalette = paletteOf(
        0xFF235494, 0xFFAD841F, 0xFF2A49B6, 0xFFEAC224, 0xFF389EDC, 0xFFE1A960,
        0xFF6878E4, 0xFFE2EB65, 0xFF8ACEF5, 0xFFEFD5A3, 0xFFD8E0EB,
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
        0xFF1B5B98, 0xFF948B1E, 0xFF2265C3, 0xFFD0EE2B, 0xFF38ADDC, 0xFFD8C73A,
        0xFF598EDF, 0xFFD9E37A, 0xFF9ACAE5, 0xFFD6E19F, 0xFFD8E5EB,
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
        0xFF1A7B80, 0xFFA6191E, 0xFF1FA9D2, 0xFFDA194B, 0xFF13E4E6, 0xFFEB5633,
        0xFF85BDD1, 0xFFEF7394, 0xFF8CF3DF, 0xFFE99C95, 0xFFD8EBEB,
    )

    /**
     * High contrast: the numeral is the thing being protected here, not the hue.
     *
     * Every face is light and every ink is the dark one, which is what buys the
     * 6.3:1 floor between numeral and face — more than a full step above the
     * other four ramps. That means the set reads as pale rather than as loud,
     * which is the opposite of what "high contrast" sounds like and the right
     * answer anyway: contrast is between the numeral and its face, and between
     * the face and the deep board behind it, not between the blocks and the idea
     * of a bright colour.
     *
     * Hue still rotates a full turn across the eleven so the ramp is not eleven
     * shades of one thing.
     */
    val HighContrast: BlockPalette = paletteOf(
        0xFFEDE26E, 0xFFF4CDB3, 0xFFED6E7F, 0xFFF4B3DD, 0xFFD16EED, 0xFFC4BDF6,
        0xFF5D9BF5, 0xFFAAEDF0, 0xFF64FEBB, 0xFFC6F6BB, 0xFFE4E7E7,
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
 * Whichever of the two inks has more contrast against [face], and the lip that
 * goes under it.
 *
 * Worth computing rather than declaring. The derivation is also the thing that
 * notices: it currently answers "light" for four of the eleven default tiers and
 * "dark" for all eleven high-contrast ones, and it will notice again the next
 * time somebody darkens a face by eye.
 */
fun blockStyle(face: Color): BlockStyle = BlockStyle(
    face = face,
    ink = inkFor(face),
    edge = face.deepen(),
)

internal fun inkFor(face: Color): Color =
    if (contrastRatio(face, DARK_INK) >= contrastRatio(face, LIGHT_INK)) DARK_INK else LIGHT_INK

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

private fun paletteOf(vararg faces: Long): BlockPalette {
    require(faces.size == TIER_VALUES.size) {
        "a palette needs one face per tier: ${TIER_VALUES.size}, got ${faces.size}"
    }
    val styles = faces.map { blockStyle(Color(it)) }
    return object : BlockPalette {
        override val styles: List<BlockStyle> = styles
    }
}
