package com.dangerfield.drop2048.libraries.billing.impl

import com.dangerfield.drop2048.libraries.ads.AdImpressions
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.PaywallCoordinator
import com.dangerfield.drop2048.libraries.billing.PaywallRequest
import com.dangerfield.drop2048.libraries.billing.PaywallTrigger
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.drop2048.SessionTracker
import com.dangerfield.drop2048.libraries.gameconfig.ProUpsellEnabled
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Decides whether the paywall may be shown, then puts the request on a bus the
 * navigator in `:features:paywall:impl` collects.
 *
 * A bus rather than a direct `Router.navigate` because the callers are a
 * ViewModel and a library — neither should know a route exists, and a navigation
 * call from an ad gate is how a library ends up depending on a feature.
 *
 * **Refusals are silent and return `false`**, so a caller can fall back to
 * whatever it was going to do anyway. Nothing in this class can block a player:
 * the worst it does is decline to offer them something.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RealPaywallCoordinator(
    private val entitlements: Entitlements,
    private val sessionTracker: SessionTracker,
    private val upsellEnabled: ProUpsellEnabled,
    private val impressions: AdImpressions,
) : PaywallCoordinator {

    private val logger = KLog.withTag("Paywall")

    // extraBufferCapacity + DROP_OLDEST so `requestOffer` never suspends and
    // never blocks a caller that is mid-anything. A dropped offer is a
    // non-event; a suspended stacked-out sheet is a frozen screen.
    private val bus = MutableSharedFlow<PaywallRequest>(
        extraBufferCapacity = Buffer,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var observedSessionId: Long = 0L
    private var cardClaimed: Boolean = false

    override val requests: Flow<PaywallRequest> = bus.asSharedFlow()

    override fun requestOffer(trigger: PaywallTrigger): Boolean {
        if (!mayOffer(trigger)) return false

        logger.logEvent("iap.paywall_shown", "trigger" to trigger.id)
        return bus.tryEmit(PaywallRequest.Offer(trigger))
    }

    override fun mayOffer(trigger: PaywallTrigger): Boolean {
        if (entitlements.isPro.value) return false

        // The player opened it themselves. Gating the shop on a live-ops flag
        // would make "Drop 2048 Pro" in Settings do nothing, which reads as a
        // bug rather than as a decision, and `pro.upsell.enabled` is about
        // whether the app *offers*, not about whether it will take money.
        return trigger == PaywallTrigger.Direct || upsellEnabled()
    }

    /**
     * The ads-seen gate is checked **before** the session cap is spent, and the
     * order is the whole point. Claiming and then discarding would burn the
     * session's one card on a run that drew nothing, so the first stacked-out
     * screen after the first interstitial would have no card left to show.
     */
    override suspend fun claimStackedOutCard(): Boolean {
        if (entitlements.isPro.value) return false
        if (!upsellEnabled()) return false
        if (!impressions.anyAdShown()) return false
        rollIfNeeded()
        if (cardClaimed) return false
        cardClaimed = true
        return true
    }

    /**
     * The session owns the cap, and [SessionTracker] owns the session — cold
     * boot, or a foreground after fifteen minutes away. Comparing its id lazily
     * rather than collecting it, because there is no work to do at a boundary
     * except forget a boolean, and a collector would be a coroutine that exists
     * to set a field to false.
     */
    private fun rollIfNeeded() {
        val current = sessionTracker.current.id
        if (current != observedSessionId) {
            observedSessionId = current
            cardClaimed = false
        }
    }

    private companion object {
        const val Buffer = 4
    }
}
