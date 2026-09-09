package com.dangerfield.drop2048.features.profile

import com.dangerfield.drop2048.libraries.navigation.NavigableWhileBlocked
import com.dangerfield.drop2048.libraries.navigation.TrackableRoute
import kotlinx.serialization.Serializable

@Serializable
data class BugReportRoute(
	val logId: String? = null,
	val errorCode: Int? = null,
	val contextMessage: String? = null,
) : TrackableRoute("bugReportScreenOpens"), NavigableWhileBlocked
