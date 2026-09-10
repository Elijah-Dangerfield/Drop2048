package com.dangerfield.drop2048.features.home

import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

@Serializable
class HomeRoute : Route()

@Serializable
class FeedbackRoute : TrackableRoute("feedbackScreenOpens")
