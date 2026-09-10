package com.dangerfield.drop2048.features.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether this device owns Pro, and the control Apple requires for restoring it.
 *
 * **A seam, not an implementation.** Billing is C10. What this chunk owes C10 is
 * a settings screen with the row already on it and a type to bind against, so
 * C10 replaces one binding rather than editing a screen. The default binding
 * reports "not Pro" and "we could not ask", which is the honest answer while
 * there is no store connection at all.
 *
 * [restore] returns an outcome rather than a `Boolean` because every branch has
 * to say something. A restore that silently does nothing is the most common
 * reason this control gets reported as broken: the player cannot tell "you never
 * bought it" from "we could not ask the store".
 */
interface ProEntitlement {
    val isPro: StateFlow<Boolean>

    suspend fun restore(): RestoreOutcome
}

enum class RestoreOutcome {
    Restored,
    NothingToRestore,

    /** The store could not be reached, or there is no store wired up yet. */
    Unavailable,
}

/**
 * The GDPR / CCPA / ATT consent surface.
 *
 * Also a seam: the consent SDK arrives with the ad SDK in C10, and a settings
 * row that opens nothing is worse than one that says why. [isAvailable] is what
 * decides whether the row is drawn at all.
 */
interface AdConsent {
    val isAvailable: Boolean

    suspend fun present()
}
