package com.dangerfield.drop2048.system.typography

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import drop2048.libraries.ui.generated.resources.DMSerifText_Italic
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
 * The face every number that changes is drawn in: block values, the score, the
 * level, the chain counter.
 *
 * **This is a known gap, deliberately reduced to one line.** None of these should
 * be set in a proportional face, because proportional digits are different widths
 * — a score ticking from 1111 to 2222 visibly jitters, and a block face redraws
 * its numeral every merge. The fix is a font with tabular (monospaced) figures,
 * or one with `font-feature-settings: "tnum"`. No font in this repo or in either
 * sibling has them, and the choice is an owner decision.
 *
 * Until then this aliases the sans family, so every digit in the game is already
 * reading from one token and swapping in the real face is this declaration and
 * nothing else. Do not reach for [SansSerifFontFamily] to draw a number.
 */
val DigitFontFamily: FontFamily
    @Composable get() = SansSerifFontFamily

val SerifFontFamily: FontFamily
    @Composable get() = FontFamily(
        Font(
            resource = Res.font.DMSerifText_Regular, weight = FontWeight.Normal
        ),

        Font(
            resource = Res.font.DMSerifText_Italic, style = FontStyle.Italic
        ),
    )