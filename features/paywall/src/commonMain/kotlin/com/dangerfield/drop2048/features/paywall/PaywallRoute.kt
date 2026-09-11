package com.dangerfield.drop2048.features.paywall

import com.dangerfield.drop2048.libraries.navigation.AnimationType
import com.dangerfield.drop2048.libraries.navigation.Route
import kotlinx.serialization.Serializable

/**
 * SPEC 12's Pro sheet.
 *
 * A `class` with no arguments rather than a `data object`, which SIGSEGVs the
 * iOS navigator at navigate time — an arg-less route still gets a constructor.
 *
 * **The trigger is not a route argument**, and that is deliberate. Which moment
 * opened the paywall is analytics, not navigation: it decides nothing about what
 * the screen draws, it would put a monetization enum in the back stack where a
 * process death has to restore it, and it would give a deep link a way to claim
 * a conversion the app never made. The coordinator that decided to open the sheet
 * already knows the trigger and is the thing that reports it.
 */
@Serializable
class PaywallRoute : Route(
    // It rises rather than slides in, because it is a sheet over whatever the
    // player was doing rather than a page they navigated to — and because the
    // two places that open it (Settings, and the stacked-out sheet) are both
    // somewhere the player expects to come straight back to.
    enter = AnimationType.SlideUp,
    exit = AnimationType.SlideDown,
    popExit = AnimationType.SlideDown,
)
