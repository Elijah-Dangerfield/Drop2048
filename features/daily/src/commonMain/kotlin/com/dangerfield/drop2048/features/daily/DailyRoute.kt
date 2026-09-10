package com.dangerfield.drop2048.features.daily

import com.dangerfield.drop2048.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * SPEC 14's Daily Challenge screen: today's state, the streak, attempts
 * remaining, and where C9's leaderboard will go.
 *
 * A `class` with no arguments rather than a `data object`, which SIGSEGVs the
 * iOS navigator at navigate time — an arg-less route still gets a constructor.
 *
 * Slides, unlike `GameRoute`. This one *is* a page in a stack: the player came
 * from the board, they are going back to the board, and the animation should say
 * so.
 */
@Serializable
class DailyRoute : Route()
