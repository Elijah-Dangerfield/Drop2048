package com.dangerfield.drop2048.features.game

import com.dangerfield.drop2048.libraries.navigation.AnimationType
import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.serializableType
import com.dangerfield.drop2048.libraries.progress.GameMode
import androidx.navigation.NavType
import kotlin.jvm.JvmSuppressWildcards
import kotlin.reflect.KType
import kotlin.reflect.typeOf
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
    /**
     * Which rules the run is played under (SPEC 14).
     *
     * One screen, not two. The Daily is the same board, the same controls and the
     * same engine on a seed nobody chose, so a second copy of `GameScreen` would
     * be a second place for every later fix to have to land.
     *
     * An enum route argument, which needs two things on Native: the enum must be
     * `@Serializable` and it must appear in the destination's typeMap. See
     * [GameRouteTypeMap].
     */
    val mode: GameMode = GameMode.ENDLESS,
) : Route(
    enter = AnimationType.FadeIn,
    exit = AnimationType.FadeOut,
    popExit = AnimationType.FadeOut,
)

/**
 * [GameMode]'s NavType, which every destination and deep link for [GameRoute]
 * has to be given.
 *
 * Native has no built-in enum NavType — androidx's `parseEnum` returns UNKNOWN —
 * so an enum argument is resolved through the typeMap or not at all, and the
 * failure is a graph-build crash whose message names whichever argument happened
 * to be resolving at the time. Declared beside the route so the two cannot drift.
 */
val GameRouteTypeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = mapOf(
    typeOf<GameMode>() to serializableType<GameMode>(),
)
