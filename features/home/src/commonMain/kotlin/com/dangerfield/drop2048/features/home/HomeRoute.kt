package com.dangerfield.drop2048.features.home

import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

@Serializable
class HomeRoute : Route()

@Serializable
data class SettingsRoute(
    val visitCount: Int = 1,
) : TrackableRoute("settingsVisits")

@Serializable
class FeedbackRoute : TrackableRoute("feedbackScreenOpens")
