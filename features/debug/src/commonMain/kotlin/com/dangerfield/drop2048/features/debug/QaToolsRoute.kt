package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The tester's own small menu: the switch that hides the floating directive
 * button, and nothing that cannot be undone.
 *
 * ### Why this is not a section of [DebugRoute]
 *
 * It was going to be, and there are two reasons it is not.
 *
 * **Opening the debug menu latches the debug-session taint.** `DebugViewModel`'s
 * `init` calls `markDebugSession()`, deliberately and for a good reason (L63):
 * a tester who has had their hands on a seed switch is not a source of real data
 * afterwards either, so the *session* is the unit and relaunching is the reset.
 * Hiding a button is housekeeping that takes two seconds, and charging it a
 * whole process of silenced telemetry would make the switch cost more than the
 * button it hides. This screen touches `DebugController` not at all, which is
 * the property, not an omission.
 *
 * **And the debug menu clears the back stack on the way in**, again for a good
 * reason: leaving it underneath a live board means a back press lands a tester
 * on a screen full of switches with a run still going. But it means that
 * reaching the FAB switch would cost the tester the screen they were on — which
 * is often the screen the button is in the way of.
 *
 * The property this has to preserve is that the switch is reachable **without
 * the button it switches off**, and it is: the Settings row that opens this is
 * shown on any tester build, with no seven taps and no passphrase, because a
 * TestFlight tester needs to be able to get the button out of the way and must
 * not be handed anything irreversible to do it. That is the same split Sodogku's
 * QA screen makes, expressed as two routes instead of one `isDebug` branch.
 *
 * A `class` and not a `data object`, for the reason [DebugRoute] gives: an
 * arg-less `data object` route SIGSEGVs the iOS navigator at navigate time.
 */
@Serializable
class QaToolsRoute : Route()

/**
 * The words on the Settings row that opens it.
 *
 * Beside [DebugMenuLabels] and constants for the same reason: Settings draws the
 * row, a feature impl may not read another feature's impl, and nothing on this
 * screen is ever read by a player. The `VerifyStrings` baseline stays at 8.
 */
object QaToolsLabels {
    const val SettingsRow = "QA tools"
    const val SettingsRowHint = "The floating directive button."
}
