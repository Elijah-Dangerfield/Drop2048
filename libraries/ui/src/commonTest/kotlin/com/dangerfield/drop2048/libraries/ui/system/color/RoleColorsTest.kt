package com.dangerfield.drop2048.libraries.ui.system.color

import com.dangerfield.drop2048.system.color.defaultColors
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The role ramp, measured rather than eyeballed.
 *
 * **Why this file exists.** A dialog's Cancel button shipped painted in
 * `surfacePrimary` while the dialog card it sits on was also painted in
 * `surfacePrimary`. Two roles, one value, ΔE 0 — the button was a rectangle of
 * card with a word on it, and the owner reported it as "barely stands out". The
 * bug was invisible in review because neither the button nor the dialog was
 * wrong on its own; only the *pairing* was, and nothing in the codebase knew the
 * two would ever meet.
 *
 * So this asserts pairings rather than values. Nothing here pins a hex —
 * repointing a token is meant to be cheap. What it pins is that a surface which
 * is drawn on top of another surface can be told apart from it, that ink clears
 * WCAG AA on the surface it is declared for, and that the disabled fill and the
 * enabled secondary fill are not the same colour.
 *
 * Not covered here, and covered elsewhere on purpose: block tier separation
 * (`BlockPaletteTest`), and how the dialog actually renders
 * (`SettingsScreenshotTest`'s goldens).
 */
class RoleColorsTest {

    /**
     * The regression this file was written for.
     *
     * A filled secondary button — every Cancel in the app — paints in
     * `surfaceElevated`, and every dialog, bottom sheet and modal card paints in
     * `surfacePrimary`. If those two ever resolve to the same value again, the
     * Cancel button disappears.
     */
    @Test
    fun aSurfaceThatSitsOnTheCardIsNotTheCard() {
        val card = defaultColors.surfacePrimary.color
        val elevated = defaultColors.surfaceElevated.color

        val distance = perceptualDistance(card, elevated)
        assertTrue(
            distance >= LayerSeparationFloor,
            "surfaceElevated sits $distance from surfacePrimary, under the floor of " +
                "$LayerSeparationFloor. A filled secondary button on a dialog card is the " +
                "pairing this protects.",
        )
    }

    /**
     * An enabled Cancel and a disabled Erase share a dialog, so they cannot share
     * a fill.
     *
     * `surfaceDisabled` is [GameColors.ControlRaised] and `surfaceElevated` is a
     * step above it for exactly this reason — see `ControlElevated`'s KDoc. Told
     * apart only by their ink, the two would read as two live buttons.
     */
    @Test
    fun theDisabledFillIsNotTheEnabledSecondaryFill() {
        val distance = perceptualDistance(
            defaultColors.surfaceDisabled.color,
            defaultColors.surfaceElevated.color,
        )
        assertTrue(
            distance >= LayerSeparationFloor,
            "surfaceDisabled sits $distance from surfaceElevated, under the floor of " +
                "$LayerSeparationFloor",
        )
    }

    /** WCAG AA for the 14sp label a medium button draws. */
    @Test
    fun everySurfaceCarriesItsOwnInkAtBodyContrast() {
        listOf(
            "surfacePrimary" to (defaultColors.surfacePrimary to defaultColors.onSurfacePrimary),
            "surfaceSecondary" to (defaultColors.surfaceSecondary to defaultColors.onSurfaceSecondary),
            "surfaceTertiary" to (defaultColors.surfaceTertiary to defaultColors.onSurfaceTertiary),
            "surfaceElevated" to (defaultColors.surfaceElevated to defaultColors.onSurfaceElevated),
            "accentPrimary" to (defaultColors.accentPrimary to defaultColors.onAccentPrimary),
            "accentSecondary" to (defaultColors.accentSecondary to defaultColors.onAccentSecondary),
        ).forEach { (name, pair) ->
            val (surface, ink) = pair
            val ratio = contrastRatio(ink.color, surface.color)
            assertTrue(
                ratio >= TextContrastFloor,
                "$name prints its ink at $ratio:1, under $TextContrastFloor",
            )
        }
    }

    /**
     * Cancel is the safe option on a destructive dialog, so it is not allowed to
     * become the loudest thing on it.
     *
     * Asserted as an ordering rather than a value: whatever the two tokens are
     * repointed to, the accent has to carry more weight against the card than the
     * quiet fill does.
     */
    @Test
    fun theSecondaryFillStaysQuieterThanTheAccentItSitsBeside() {
        val card = defaultColors.surfacePrimary.color
        val quiet = contrastRatio(defaultColors.surfaceElevated.color, card)
        val loud = contrastRatio(defaultColors.accentPrimary.color, card)
        assertTrue(
            loud > quiet,
            "the secondary fill reads at $quiet:1 against the dialog card and the accent at " +
                "$loud:1 — Cancel has become at least as loud as the destructive confirm",
        )
    }

    private companion object {
        /**
         * Two large flat areas meeting at an edge need far less than the ΔE 24
         * two block faces do, because the edge itself is the signal. This is
         * roughly three times the just-noticeable difference, which is where a
         * boundary stops being something you have to look for.
         */
        const val LayerSeparationFloor = 7f

        /** WCAG AA for body text, which is what a 14sp semibold button label is. */
        const val TextContrastFloor = 4.5f
    }
}
