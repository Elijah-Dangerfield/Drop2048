package com.dangerfield.drop2048.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The GDPR / CCPA / ATT consent surface, as Settings sees it.
 *
 * It was a seam in `:features:settings` until C10 and it is here now for the
 * same reason [RunActivity] is: the thing that can answer it is the ad SDK, and
 * a library cannot implement a type a feature owns. Settings depends on this
 * module; the binding comes from `:libraries:ads:impl`, where UMP already is.
 *
 * [isAvailable] is what decides whether the row is drawn at all. A settings row
 * that opens nothing is worse than no row — it reads as broken rather than as
 * absent, and it is the first thing a store reviewer taps.
 */
interface AdConsent {
    val isAvailable: Boolean

    suspend fun present()
}

/**
 * No consent form, on a build with no consent SDK. The row is not drawn.
 *
 * Replaced on Android by the UMP-backed binding. It is *not* the same thing as
 * "the player declined": nothing here records a preference, and nothing reads
 * one. Consent lives in the SDK's own storage, which is the only place a store
 * audit will look for it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoAdConsent : AdConsent {
    override val isAvailable: Boolean = false

    override suspend fun present() = Unit
}
