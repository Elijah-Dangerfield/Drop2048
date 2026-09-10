package com.dangerfield.drop2048.features.gate

import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.Platform

/**
 * Where "Update" sends the player.
 *
 * **Not remote config**, unlike almost everything else the gates read. Changing
 * where the app is listed is a store operation, not a live-ops one, and a
 * force-update wall whose only button is driven by the same config that raised
 * the wall has one failure mode too many.
 *
 * Android derives the URL from the application id, so it is correct for every
 * flavour without anyone maintaining a table. iOS cannot: the App Store
 * addresses apps by a numeric id that only exists once the listing does, so
 * [AppStoreId] is blank until then and the fallback is a search that lands on
 * the app rather than a link that 404s. Fill it in with App Store Connect's
 * "Apple ID" when the listing is created (C13).
 */
object StoreListing {

    /** App Store Connect's numeric "Apple ID". Blank until the listing exists. */
    const val AppStoreId: String = ""

    private const val AppName = "Drop 2048"

    fun url(): String = when (BuildInfo.platform) {
        Platform.Android -> "https://play.google.com/store/apps/details?id=${BuildInfo.applicationId}"
        Platform.iOS ->
            if (AppStoreId.isBlank()) "https://apps.apple.com/search?term=$AppName"
            else "https://apps.apple.com/app/id$AppStoreId"
    }
}
