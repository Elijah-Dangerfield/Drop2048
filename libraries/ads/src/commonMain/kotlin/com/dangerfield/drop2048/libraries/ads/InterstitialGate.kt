package com.dangerfield.drop2048.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The one ad in the app the player did not ask for, and every rule that keeps it
 * that way.
 *
 * It is a separate type from [AdGate] rather than a third [AdPlacement], and the
 * separation is the point. SPEC 12's governing principle is that **the player
 * never sees an ad they did not choose while a run is alive**, and the only
 * defence that survives a year of edits is one where the unchosen format has no
 * expressible call site inside a live run: this interface has exactly one method
 * that can show anything, one caller, and that caller is the code that has just
 * watched the player dismiss a results sheet.
 *
 * Every gate in SPEC 12.3 is evaluated in
 * [InterstitialPolicy][com.dangerfield.drop2048.libraries.ads.impl.InterstitialPolicy],
 * which is a pure function so each rule has a test that cannot be satisfied by
 * accident.
 */
interface InterstitialGate {

    /**
     * The player finished a run. Counts towards SPEC 12's "not before the 4th run
     * of a session" and nothing else — it shows nothing and must not.
     *
     * Separate from [showIfReady] because the two happen at different moments: a
     * run ends when the board stacks out, and the sheet is dismissed some
     * seconds later, or never.
     */
    fun noteRunFinished()

    /**
     * The player dismissed the stacked-out results. Show an interstitial if every
     * gate allows it, and return whether one was shown.
     *
     * **Skipped silently when nothing is preloaded** (SPEC 12.3). No spinner, no
     * "loading ad", no delay: the alternative is making the player wait for an
     * advert they did not ask for, which is worse than not serving it.
     */
    suspend fun showIfReady(): Boolean

    /**
     * A rewarded ad just finished, either way.
     *
     * Feeds SPEC 12's 45-second rule in the direction that is easy to forget: not
     * only "no rewarded ad right after an interstitial", but no interstitial
     * right after a rewarded one. A player who has just watched thirty seconds to
     * continue their run and then loses it again has already paid.
     */
    fun noteRewardedShown()

    /** Warms the format. Fire and forget; the gate's whole point is that it can skip. */
    fun preload()
}

/**
 * The gate on a build with no ad network: nothing is ever shown.
 *
 * Replaced by `RealInterstitialGate`. Unlike [UnservedAdGate] there is no
 * temptation to make the default do anything else — the safe answer and the
 * lazy answer are the same one here, which is a property worth noticing: the
 * format the player did not ask for is the one where doing nothing is correct.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoInterstitials : InterstitialGate {
    override fun noteRunFinished() = Unit

    override suspend fun showIfReady(): Boolean = false

    override fun noteRewardedShown() = Unit

    override fun preload() = Unit
}
