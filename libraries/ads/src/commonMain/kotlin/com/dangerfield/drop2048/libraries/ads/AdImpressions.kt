package com.dangerfield.drop2048.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Whether this player has ever actually been shown an advertisement.
 *
 * ## The card that asked "Tired of the ads?" before there had been any
 *
 * SPEC 12's Pro upsell card is claimed once per session on the stacked-out
 * screen, and its copy is `Tired of the ads?`. Every gate in SPEC 12.3 pushes
 * the first interstitial a long way out (suppressed entirely for three days
 * after install, never before the fourth run of a session), so the overwhelmingly
 * likely first sighting of that card was on a player who had not yet seen a
 * single ad. The card was asking them to pay to remove something they had never
 * experienced, which is a worse sales pitch than saying nothing and is, plainly,
 * untrue.
 *
 * So the card is gated on this (owner ruling, 2026-09-20, D28). It is its own
 * type rather than a field on [InterstitialGate] because the consumer is
 * `:libraries:billing:impl`, which may not depend on `:libraries:ads:impl`
 * because only `:apps:*` may depend on an impl, and because the question
 * outgrew interstitials the moment the banner landed.
 *
 * ## Suspend, because the answer is on disk
 *
 * It survives a restart deliberately: a player who saw an interstitial yesterday
 * is entitled to be tired of the ads today. The record is the same `AdState`
 * SPEC 12.3's frequency gates are read from, so there is one file that knows
 * what this player has been shown and no second bookkeeping to drift from it.
 */
interface AdImpressions {

    /**
     * True once an ad the app served has actually reached the screen: an
     * interstitial that was shown, or a banner that reported a fill.
     *
     * A rewarded ad deliberately does **not** count. The player asked for it,
     * watched it on purpose and was paid for it, so it is the one format that
     * cannot make anybody tired of the ads, and it is the one Pro does not
     * remove (SPEC 12: rewarded video stays available to Pro holders). Counting
     * it would let the card promise to remove an ad the purchase keeps.
     */
    suspend fun anyAdShown(): Boolean
}

/**
 * The answer on a build with no ad network: nothing has been shown, because
 * nothing can be.
 *
 * Replaced by `RealAdImpressions`. On iOS this is the binding that survives, and
 * the consequence is deliberate and worth stating: **iOS draws no upsell card**,
 * because iOS serves no ads and the card is about ads. Pro is still sold there
 * from the Settings entry and from the continue offer, both of which are
 * controls the player reached for.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoAdImpressions : AdImpressions {
    override suspend fun anyAdShown(): Boolean = false
}
