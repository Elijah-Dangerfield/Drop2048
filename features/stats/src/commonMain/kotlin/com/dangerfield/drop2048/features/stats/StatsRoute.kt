package com.dangerfield.drop2048.features.stats

import com.dangerfield.drop2048.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * SPEC 15's stats page. Reached from the stacked-out sheet, which is where a
 * player is when the numbers on it have just changed.
 *
 * A `class` with defaults rather than a `data object`, which SIGSEGVs the iOS
 * navigator at navigate time.
 */
@Serializable
class StatsRoute : Route()
