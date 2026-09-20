package com.dangerfield.drop2048.features.game

import com.dangerfield.drop2048.libraries.navigation.AnimationType
import com.dangerfield.drop2048.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The playable board (SPEC 3, 6, 8).
 *
 * A `class` with defaults rather than a `data object`, which SIGSEGVs the iOS
 * navigator at navigate time — an arg-less route still gets a constructor.
 *
 * Fades rather than slides. Entering a run is a mode switch, and a board that
 * slides in from the right reads as a page in a stack the player can swipe back
 * out of, which is exactly what a live run must not look like.
 */
@Serializable
class GameRoute(
    /**
     * Run the guided tutorial again (SPEC 13's "replayable from Settings").
     *
     * A route argument rather than a second screen, because the tutorial *is*
     * this screen with the drop clock switched off. Settings is C11; until it
     * lands this argument is the whole of the entry point, and nothing in the app
     * currently passes true.
     *
     * First launch does **not** use it: that is decided from the persisted flag
     * inside `GameViewModel`, so the app has exactly one start destination.
     */
    val replayTutorial: Boolean = false,
) : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
)
