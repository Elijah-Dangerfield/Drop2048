package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.features.debug.DebugEntitlements
import com.dangerfield.drop2048.features.settings.AdConsent
import com.dangerfield.drop2048.features.settings.ProEntitlement
import com.dangerfield.drop2048.features.settings.RestoreOutcome
import kotlinx.coroutines.flow.StateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * What Pro and ad consent do until C10 wires a store to them.
 *
 * Bound rather than absent so the settings screen compiles, renders and is
 * screenshot-tested with the rows in place, and so C10 replaces a binding rather
 * than editing a screen. Both answer honestly: nobody owns Pro, a restore could
 * not be attempted, and there is no consent form to present.
 *
 * `isAvailable` being false is what takes the consent row off the screen
 * entirely. A row that opens nothing is worse than no row — it reads as broken
 * rather than as absent, and it is the first thing a store reviewer taps.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ProEntitlement::class)
@Inject
class UnwiredProEntitlement(
    debugEntitlements: DebugEntitlements,
) : ProEntitlement {

    /**
     * Nobody owns Pro, unless the debug menu says otherwise (SPEC 19).
     *
     * The grant is folded in here rather than bound as a competing
     * [ProEntitlement] so there is one answer to "does this device own Pro" and
     * one place C10 replaces. **C10 has to keep this fold**, or the menu's Pro
     * switch quietly stops working on the release where it matters most.
     */
    override val isPro: StateFlow<Boolean> = debugEntitlements.proGranted

    override suspend fun restore(): RestoreOutcome = RestoreOutcome.Unavailable
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AdConsent::class)
@Inject
class UnwiredAdConsent : AdConsent {
    override val isAvailable: Boolean = false

    override suspend fun present() = Unit
}
