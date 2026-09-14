package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * SPEC 10's config keys, editable on the device (SPEC 19).
 *
 * `ConfigOverrideRepository` has been in the app since the template and nothing
 * has ever written to it, which is why every gate SPEC 12.3 puts in front of an
 * interstitial has been unreachable by hand: the three-day install suppression,
 * the fourth-run minimum and the 180-second cooldown compound, and there was no
 * way to relax one without a build. The same absence blocks the launch gates and
 * both kill switches, which can brick every install at once and had therefore
 * never been exercised anywhere but a unit test.
 *
 * A separate destination from [DebugRoute] rather than another section on it.
 * The menu is already longer than any phone, and these rows are the only ones
 * that **outlive the process**: an override is written to disk and shadows the
 * console until it is removed, so a tester who forgets one is debugging a
 * different app from everybody else. A screen with its own title and its own
 * "clear all" is where that belongs.
 *
 * A `class` with no arguments, for the reason [DebugRoute] has none.
 */
@Serializable
class ConfigOverridesRoute : Route()
