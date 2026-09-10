package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The QA menu (SPEC 19), reached from Settings after seven taps on the version
 * number.
 *
 * A `class` with no arguments at all, for the reason `SettingsRoute` has none: a
 * screen this full of enums is exactly where a forgotten typeMap entry would bite,
 * and the crash is at graph-build time on Native naming a *different* argument
 * than the one that was missed. Every choice on this screen is held in
 * [DebugController] rather than routed, so an argument would buy nothing and
 * could only cost.
 */
@Serializable
class DebugRoute : Route()

/**
 * The words on the Settings row that opens the menu.
 *
 * Plain constants rather than `composeResources`, and in the api module rather
 * than beside the rest of the menu's copy because Settings draws the row and a
 * feature impl may not read another feature's impl.
 *
 * The rest of the menu's copy is constants too, for the reason in `DebugCopy`:
 * this screen is unreachable without seven taps and, on a release build, a
 * passphrase, so there is nothing here for a translator to translate. The repo's
 * string baseline stays at nine.
 */
object DebugMenuLabels {
    const val SettingsRow = "Debug menu"
    const val SettingsRowHint = "QA tools. Runs started from here are not recorded."
}
