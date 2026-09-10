@file:Suppress("MagicNumber")

package com.dangerfield.drop2048.libraries.ui.system.color

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The game screen's colour table, exactly as the design handoff specifies it.
 *
 * Separate from [com.dangerfield.drop2048.system.color.Colors] on purpose, and
 * the split is worth stating because it is the kind of thing that gets
 * "tidied up" into one object by the next person. [Colors] is the *template's*
 * role-based light-theme ramp — `surfacePrimary`, `onBackground`, a status
 * triple — and it dresses the screens that are made of lists and forms: settings,
 * stats, the bug reporter. The game screen is not made of those. It is one dark
 * surface with a well cut into it, and every colour on it was chosen against that
 * surface rather than against a role.
 *
 * Trying to express `boardWell` as a `surfaceTertiary` would produce a token that
 * is wrong everywhere except one screen, which is the failure mode of a single
 * flat palette. So these are named for what they are.
 *
 * Everything here is final per the handoff's fidelity note. Do not approximate a
 * value in here to make a component easier to write.
 */
object GameColors {

    /**
     * The screen behind everything, a radial gradient falling away from a point
     * just above the top edge.
     *
     * A [Brush] rather than a [Color] because it genuinely is one: the handoff's
     * `radial-gradient(120% 70% at 50% -10%, …)`. Sized per-draw, so it is a
     * function rather than a value — a brush baked at one size stretches wrongly
     * on the next phone.
     */
    fun backdrop(width: Float, height: Float): Brush = Brush.radialGradient(
        colorStops = arrayOf(0f to BackdropNear, 0.55f to BackdropMid, 1f to BackdropFar),
        center = Offset(x = width * 0.5f, y = height * -0.10f),
        radius = maxOf(width * 1.20f, height * 0.70f),
    )

    val BackdropNear = Color(0xFF2A1F45)
    val BackdropMid = Color(0xFF141021)
    val BackdropFar = Color(0xFF0E0B17)

    /** The well the board is cut into. Darker than anything else on the screen. */
    val BoardWell = Color(0xFF0D0A16)

    /** The 4px ring around the well. */
    val BoardRing = Color(0xFF221B36)

    /** The ring and the glow when the stack reaches the top two rows. */
    val DangerRing = oklch(0.65f, 0.20f, 25f)
    val DangerGlow = oklch(0.60f, 0.20f, 25f).copy(alpha = 0.5f)

    /** An empty cell, and the same cell in the column the falling tile is in. */
    val Slot = Color.White.copy(alpha = 0.035f)
    val SlotActive = Color.White.copy(alpha = 0.075f)

    val Ink = Color(0xFFF3EEF7)
    val InkMuted = Color(0xFF8F86A6)
    val InkFaint = Color(0xFFB8AFCC)

    val Surface = Color.White.copy(alpha = 0.06f)

    /** The two arrow buttons and the pause button. */
    val Control = Color(0xFF2A2340)
    val ControlShadow = Color(0xFF17122A)

    /** The nudge button, one step quieter than [Control] on purpose. */
    val ControlQuiet = Color(0xFF221C34)
    val ControlQuietShadow = Color(0xFF150F26)

    /**
     * [Control] lifted one step, for the things that have to read *above* a card
     * rather than below it.
     *
     * Derived rather than drawn, and the only value in this object that is. The
     * handoff's control system runs downward — control, quiet control, shadow —
     * because on the board everything chunky is pressed into a dark surface.
     * A role ramp needs the other direction too: a disabled button and an
     * unselected chart bar both sit *on* a `Control` card, and painting them in
     * anything darker made them vanish into it. That is not a hypothetical —
     * `stats-populated` was recorded with the bar chart drawn in the well colour
     * and the bars were invisible.
     *
     * It is [Control] composited with 6% white, which is [Surface]'s own lift, so
     * it is the handoff's step size rather than a new one.
     */
    val ControlRaised = Color(0xFF3B3450)

    /** The brand yellow: the wordmark chip, the primary button, "new best!". */
    val AccentYellow = oklch(0.85f, 0.17f, 90f)
    val AccentYellowShadow = oklch(0.62f, 0.15f, 85f)
    val OnAccentYellow = oklch(0.28f, 0.08f, 80f)

    /** The level progress fill. */
    val AccentViolet = oklch(0.78f, 0.16f, 300f)
    val AccentVioletShadow = oklch(0.55f, 0.15f, 300f)

    /** The track the level fill runs in. */
    val ProgressTrack = Color.Black.copy(alpha = 0.45f)

    /** The three overlay scrims. All sit over a 6dp blur. */
    val ScrimStart = Color(0xFF0E0B17).copy(alpha = 0.86f)
    val ScrimPaused = Color(0xFF0E0B17).copy(alpha = 0.84f)
    val ScrimGameOver = Color(0xFF0E0B17).copy(alpha = 0.90f)

    /** The hard shadow under the wordmark, which is not the accent shadow. */
    val WordmarkShadow = Color(0xFF0A0812)

    /** The toast's own hard drop shadow, and the halo behind it. */
    val ToastShadow = Color(0xFF3A2A5C)
    val ToastGlow = Color.White.copy(alpha = 0.35f)

    /** The landing ghost, in the two states it has. */
    val GhostPlain = Color.White.copy(alpha = 0.28f)
    val GhostMerge = Color.White.copy(alpha = 0.95f)
    val GhostMergeFill = Color.White.copy(alpha = 0.08f)

    /** The inset top highlight every chunky surface carries. */
    val TopHighlight = Color.White.copy(alpha = 0.38f)

    /** The stronger highlight the yellow accent surfaces carry. */
    val AccentHighlight = Color.White.copy(alpha = 0.45f)

    /** The soft lift under the falling tile, which is the one soft shadow on the board. */
    val FallingLift = Color.Black.copy(alpha = 0.45f)

    /** The inner shadow that makes the well look cut rather than painted. */
    val WellInnerShadow = Color.Black.copy(alpha = 0.6f)
}
