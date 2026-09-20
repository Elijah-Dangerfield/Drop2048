package com.dangerfield.drop2048.libraries.billing

import kotlinx.coroutines.flow.Flow

/**
 * The moments that may open the paywall.
 *
 * [id] is the `iap.paywall_shown` attribute, so "which trigger converts" is one
 * query rather than a join. SPEC 12 names exactly two surfaces and this enum is
 * deliberately not longer than they are: a small persistent entry in Settings,
 * and one non-modal card on the stacked-out screen at most once per session.
 */
enum class PaywallTrigger(val id: String) {
    /**
     * The player opened it themselves from Settings. Never gated and never
     * capped — refusing to sell to someone who walked into the shop would be
     * absurd, and it is not an interruption to cap.
     */
    Direct("direct"),

    /**
     * SPEC 12's once-per-session card on the stacked-out screen.
     *
     * A card, not a sheet. It is drawn in place by `:features:game:impl` and
     * only opens the paywall when it is tapped, which is why this trigger fires
     * on the tap rather than on the card appearing.
     */
    StackedOut("stacked_out"),

    /**
     * Next to the rewarded continue, where the player is already weighing
     * "watch an ad or not", so Pro reads as the other answer to a question
     * they are already being asked.
     *
     * Declared in C10 and wired on 2026-09-20 (D28). It sat with zero call
     * sites for three chunks, which is the same failure `feature.leaderboards`
     * was: a documented surface that did not exist. It is now a quiet option on
     * the continue overlay, under "No thanks", drawn only when [mayOffer] says
     * the tap would go somewhere.
     */
    Continue("continue"),
}

/** What the app should put on screen. */
sealed interface PaywallRequest {
    /** The Pro sheet, as an offer. Always dismissible: SPEC 12 has no blocking paywall. */
    data class Offer(val trigger: PaywallTrigger) : PaywallRequest
}

/**
 * The one place that decides whether a paywall may be shown, and the bus the
 * navigator listens on.
 *
 * A bus rather than a direct `Router.navigate` because the callers are a library
 * and a repository — neither should know a route exists, and a navigation call
 * from an ad gate is how a library ends up depending on a feature. The feature
 * owns its own destinations; this owns the policy.
 *
 * **Refusals are silent and return `false`**, so a caller can fall back to
 * whatever it was going to do anyway. Nothing here can block a player: the worst
 * it does is decline to offer them something.
 */
interface PaywallCoordinator {

    /** What to show. Hot; the navigator is the only subscriber. */
    val requests: Flow<PaywallRequest>

    /**
     * Ask for the paywall at [trigger]. Returns whether it was accepted.
     *
     * Refused for a player who already owns Pro, and for every trigger except
     * [PaywallTrigger.Direct] when `pro.upsell.enabled` is off. **Not capped**:
     * every caller is a control the player touched, and the once-per-session
     * limit SPEC 12 asks for belongs on [claimStackedOutCard], which is the only
     * thing here that appears without being asked for.
     */
    fun requestOffer(trigger: PaywallTrigger): Boolean

    /**
     * Whether a [requestOffer] at [trigger] would be accepted, asked without
     * spending it.
     *
     * For drawing the control rather than for deciding anything: a Pro entry
     * point that opens nothing is worse than no entry point, and the caller
     * cannot know the two rules [requestOffer] applies without copying them.
     * Pure: it moves nothing and logs nothing, so asking twice is free.
     */
    fun mayOffer(trigger: PaywallTrigger): Boolean

    /**
     * Take the session's one stacked-out card, if it is still there. True at
     * most once per session, and never for a Pro player, with
     * `pro.upsell.enabled` off, or before this player has actually been shown an
     * ad.
     *
     * Separate from [requestOffer] because SPEC 12's card is not a sheet: it is
     * drawn in place, it costs the player nothing until they touch it, and it is
     * the *card* that is capped at once per session rather than the paywall
     * behind it. A player who taps it, backs out and taps it again is asking, and
     * asking is never refused.
     *
     * Suspending since D28. The card's copy is "Tired of the ads?", and whether
     * this player has ever seen one is a fact on disk, behind `AdImpressions`
     * in `:libraries:ads`, rather than something the process knows already. It
     * is called from the run-end path, which is
     * already suspending, so the cost is a read the sheet was waiting for
     * anyway.
     */
    suspend fun claimStackedOutCard(): Boolean
}
