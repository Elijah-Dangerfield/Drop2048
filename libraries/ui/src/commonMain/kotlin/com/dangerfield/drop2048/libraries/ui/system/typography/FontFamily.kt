package com.dangerfield.drop2048.system.typography

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import drop2048.libraries.ui.generated.resources.DMSerifText_Italic
import drop2048.libraries.ui.generated.resources.fredoka_bold
import drop2048.libraries.ui.generated.resources.fredoka_medium
import drop2048.libraries.ui.generated.resources.fredoka_semibold
import drop2048.libraries.ui.generated.resources.nunito_bold
import drop2048.libraries.ui.generated.resources.nunito_extrabold
import drop2048.libraries.ui.generated.resources.nunito_semibold
import drop2048.libraries.ui.generated.resources.DMSerifText_Regular
import drop2048.libraries.ui.generated.resources.Res
import drop2048.libraries.ui.generated.resources.Roboto_Bold
import drop2048.libraries.ui.generated.resources.Roboto_Light
import drop2048.libraries.ui.generated.resources.Roboto_Medium
import drop2048.libraries.ui.generated.resources.Roboto_Regular
import drop2048.libraries.ui.generated.resources.Roboto_SemiBold
import drop2048.libraries.ui.generated.resources.lust_script_regular
import drop2048.libraries.ui.generated.resources.poppins_bold
import drop2048.libraries.ui.generated.resources.poppins_light
import drop2048.libraries.ui.generated.resources.poppins_medium
import drop2048.libraries.ui.generated.resources.poppins_regular
import drop2048.libraries.ui.generated.resources.poppins_semibold


val BrandFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.lust_script_regular, weight = FontWeight.Normal
        ),
    )

val SansSerifFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.Roboto_Light, weight = FontWeight.Light
        ), Font(
            resource = Res.font.Roboto_Regular, weight = FontWeight.Normal
        ), Font(
            resource = Res.font.Roboto_Medium, weight = FontWeight.Medium
        ), Font(
            resource = Res.font.Roboto_Bold, weight = FontWeight.Bold
        ), Font(
            resource = Res.font.Roboto_SemiBold, weight = FontWeight.SemiBold
        )
    )

/**
 * Fredoka, 500/600/700. Numerals, the wordmark and every button label.
 *
 * Bundled rather than fetched from Google Fonts at runtime, as the handoff asks.
 * A game whose first frame is its score cannot afford a face that arrives on the
 * second, and a network font on a game with no other network call is a privacy
 * surface for nothing.
 *
 * These are static instances cut from the variable original at `wdth = 100`,
 * because the design uses one width and three fixed weights. Three 50KB files
 * beat one 160KB file the platform then has to interpolate on every frame.
 */
val FredokaFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(resource = Res.font.fredoka_medium, weight = FontWeight.Medium),
        Font(resource = Res.font.fredoka_semibold, weight = FontWeight.SemiBold),
        Font(resource = Res.font.fredoka_bold, weight = FontWeight.Bold),
    )

/**
 * Nunito, 600/700/800. Labels and body copy.
 *
 * The quieter of the two faces, and the one that carries the small uppercase
 * letter-spaced labels the design leans on: `SCORE`, `LEVEL`, `BIGGEST`.
 */
val NunitoFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(resource = Res.font.nunito_semibold, weight = FontWeight.SemiBold),
        Font(resource = Res.font.nunito_bold, weight = FontWeight.Bold),
        Font(resource = Res.font.nunito_extrabold, weight = FontWeight.ExtraBold),
    )

/**
 * The face every number is drawn in: tile values, the score, the level, the
 * chain counter.
 *
 * **Fredoka's figures are proportional, and that is measured rather than
 * assumed.** The shipped file carries no `tnum` feature at all, and its ten
 * digits have eight distinct advance widths at every weight the design uses. At
 * 700 the `1` is 379 units against the `2`'s 566, a 49% spread. A score ticking
 * from 1111 to 2222 visibly changes width, and during a cascade it does it
 * several times a second.
 *
 * The token still points at Fredoka, because the handoff is explicit that
 * numerals are Fredoka, and because on a **tile** the wobble does not exist: a
 * tile draws one value, centred, and never animates between two.
 *
 * Where it does exist is the score counter, and there are three fixes, in
 * increasing order of what they cost the design:
 *
 * 1. **Lay the score out digit by digit in fixed-width slots.** Keeps Fredoka
 *    everywhere and costs one composable. This is the recommendation.
 * 2. **Draw the score in [NunitoFontFamily] at ExtraBold.** Nunito's ten digits
 *    are all exactly 600 units — tabular in effect, without needing the feature —
 *    so this is a one-line fix with no layout work. It costs the most prominent
 *    number on the screen its Fredoka look.
 * 3. **Ship a `tnum`-patched cut of Fredoka.** Correct, and a build-time font
 *    pipeline nobody wants to own for one number.
 *
 * This is an owner decision and it has not been made. Do not quietly pick one.
 */
val DigitFontFamily: FontFamily
    @Composable get() = FredokaFontFamily

/**
 * The same size, weight and rhythm as [this], drawn in [DigitFontFamily].
 *
 * What lets the score and the level pick a typography token like every other
 * piece of text while still being drawn in the face numbers are drawn in. The
 * alternative is a hand-built `TextStyle` at each call site, which is how one of
 * them ends up in the wrong face and nobody notices until a screenshot.
 */
val TypographyResource.digits: TypographyResource
    @Composable get() = TypographyResource(
        fontFamily = DigitFontFamily,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = lineHeight,
        lineBreak = lineBreak,
        fontStyle = fontStyle,
        identifier = "$identifier-digits",
    )

val SerifFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.DMSerifText_Regular, weight = FontWeight.Normal
        ),

        Font(
            resource = Res.font.DMSerifText_Italic, style = FontStyle.Italic
        ),
    )