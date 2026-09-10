package com.dangerfield.drop2048.features.achievements

import com.dangerfield.drop2048.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

/**
 * SPEC 15's badge grid. Reached from Settings.
 *
 * A `class`, never a `data object`: an arg-less object route SIGSEGVs the iOS
 * navigator at navigate time.
 */
@Serializable
class AchievementsRoute : TrackableRoute("achievementsVisits")
