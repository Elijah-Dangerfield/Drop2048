package com.dangerfield.drop2048.features.settings

import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

/**
 * The settings screen (SPEC 11 and 16).
 *
 * A `class`, never a `data object` — an arg-less `data object` route SIGSEGVs
 * the iOS navigator at navigate time, and an arg-less route still gets a
 * constructor.
 *
 * Deliberately carries **no arguments at all**, enum or otherwise. Type-safe nav
 * turns every serialized constructor `val` into a nav argument, and on
 * Kotlin/Native an enum argument needs an explicit `NavType` in the
 * destination's typeMap or graph-build throws — naming a *different* argument
 * than the one that was forgotten. Every choice on this screen is persisted
 * rather than routed, so there is nothing an argument would buy.
 */
@Serializable
class SettingsRoute : TrackableRoute("settingsVisits")

/** The bundled third-party licence text. Its own screen because it is long. */
@Serializable
class LicensesRoute : Route()
