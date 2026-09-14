package com.dangerfield.drop2048.features.paywall

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
 * the sheet draws, it would put a monetization enum in the back stack where a
 * process death has to restore it, and it would give a deep link a way to claim
 * a conversion the app never made. The coordinator that decided to open the sheet
 * already knows the trigger and is the thing that reports it.
 *
 * It carries no enter/exit animation overrides any more. It is registered with
 * `bottomSheet<>` (C14), and a floating-window destination is animated by the
 * sheet rather than by the nav host — the base class defaults are inert here, and
 * a `SlideUp` written on the route would read as a promise the transition does
 * not keep.
 */
@Serializable
class PaywallRoute : Route()
