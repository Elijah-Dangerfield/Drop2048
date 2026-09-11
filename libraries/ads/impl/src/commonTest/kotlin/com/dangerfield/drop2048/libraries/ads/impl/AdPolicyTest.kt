package com.dangerfield.drop2048.libraries.ads.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SPEC 12.3, one rule at a time and then all of them together.
 *
 * ### The baseline is the test
 *
 * [allowed] is a set of conditions under which an interstitial **does** show,
 * asserted first and separately. Every other test takes that baseline and moves
 * exactly one field. Without the positive control a rule test proves nothing: a
 * policy that refused everything would pass all eight of the negative cases and
 * the suite would look complete (L35 is the same shape).
 *
 * ### One reason, not one boolean
 *
 * Each test asserts *which* block fired, not just that one did. A test that
 * accepted "blocked for some reason" would still pass if the cooldown rule were
 * deleted, as long as the install-suppression rule happened to cover the same
 * case — which it would, since both are true of a new player.
 */
class AdPolicyTest {

    @Test
    fun `the baseline shows an interstitial`() {
        assertNull(InterstitialPolicy.decide(allowed))
    }

    @Test
    fun `no interstitial while a run is alive`() {
        assertEquals(
            InterstitialBlock.RunAlive,
            InterstitialPolicy.decide(allowed.copy(runAlive = true)),
        )
    }

    /**
     * The governing principle, stated as its own test rather than inferred from
     * the one above it: *the player never sees an ad they did not choose while a
     * run is alive.*
     *
     * It is checked against a set of conditions where **every other gate is
     * wide open** — the cooldown spent, four runs in, a fortnight since install,
     * an ad loaded and waiting. If a live run could ever be overruled by the
     * frequency rules, this is the arrangement that would find it.
     */
    @Test
    fun `a live run outranks every other gate`() {
        val everythingElsePermits = allowed.copy(
            runAlive = true,
            runsThisSession = 99,
            millisSinceLastInterstitial = null,
            millisSinceRewarded = null,
            daysSinceInstall = 400,
            preloaded = true,
        )

        assertEquals(InterstitialBlock.RunAlive, InterstitialPolicy.decide(everythingElsePermits))
    }

    @Test
    fun `no interstitial until the results are dismissed`() {
        assertEquals(
            InterstitialBlock.ResultsNotDismissed,
            InterstitialPolicy.decide(allowed.copy(resultsDismissed = false)),
        )
    }

    @Test
    fun `the kill switch stops interstitials`() {
        assertEquals(
            InterstitialBlock.AdsDisabled,
            InterstitialPolicy.decide(allowed.copy(adsEnabled = false)),
        )
    }

    @Test
    fun `pro removes interstitials`() {
        assertEquals(
            InterstitialBlock.Pro,
            InterstitialPolicy.decide(allowed.copy(isPro = true)),
        )
    }

    @Test
    fun `no interstitial before the fourth run of a session`() {
        val third = allowed.copy(runsThisSession = 3, minSessionRuns = 4)
        assertEquals(InterstitialBlock.TooFewRunsThisSession, InterstitialPolicy.decide(third))
    }

    /**
     * The boundary, because "not before the 4th run" is an off-by-one waiting to
     * happen and the two readings differ by exactly one advert per session.
     */
    @Test
    fun `the fourth run of a session may carry one`() {
        val fourth = allowed.copy(runsThisSession = 4, minSessionRuns = 4)
        assertNull(InterstitialPolicy.decide(fourth))
    }

    @Test
    fun `no interstitial within the cooldown of the last one`() {
        val recent = allowed.copy(
            millisSinceLastInterstitial = 179_000,
            cooldownMillis = 180_000,
        )
        assertEquals(InterstitialBlock.Cooldown, InterstitialPolicy.decide(recent))
    }

    @Test
    fun `the cooldown expires exactly at its own length`() {
        val expired = allowed.copy(
            millisSinceLastInterstitial = 180_000,
            cooldownMillis = 180_000,
        )
        assertNull(InterstitialPolicy.decide(expired))
    }

    /** Never having seen one is not a cooldown, and null is how that is said. */
    @Test
    fun `a player who has never seen an interstitial is not in a cooldown`() {
        assertNull(InterstitialPolicy.decide(allowed.copy(millisSinceLastInterstitial = null)))
    }

    @Test
    fun `no interstitial within the gap after a rewarded ad`() {
        val justWatched = allowed.copy(millisSinceRewarded = 44_000, rewardedGapMillis = 45_000)
        assertEquals(InterstitialBlock.NearRewarded, InterstitialPolicy.decide(justWatched))
    }

    /**
     * The other direction of "either direction", and the half that is not
     * arithmetic: the only moment the app can know a rewarded ad is *about* to
     * happen is while one is on screen.
     */
    @Test
    fun `no interstitial while a rewarded ad is in flight`() {
        val during = allowed.copy(rewardedInFlight = true, millisSinceRewarded = null)
        assertEquals(InterstitialBlock.NearRewarded, InterstitialPolicy.decide(during))
    }

    @Test
    fun `the rewarded gap expires exactly at its own length`() {
        val expired = allowed.copy(millisSinceRewarded = 45_000, rewardedGapMillis = 45_000)
        assertNull(InterstitialPolicy.decide(expired))
    }

    @Test
    fun `no interstitial in the first days after install`() {
        val fresh = allowed.copy(daysSinceInstall = 2, suppressDaysSinceInstall = 3)
        assertEquals(InterstitialBlock.NewInstall, InterstitialPolicy.decide(fresh))
    }

    @Test
    fun `the suppression ends on the third day`() {
        val ended = allowed.copy(daysSinceInstall = 3, suppressDaysSinceInstall = 3)
        assertNull(InterstitialPolicy.decide(ended))
    }

    /**
     * SPEC 12.3: preloaded, and skipped **silently** if not ready rather than
     * showing a spinner. The silence is the gate above returning without
     * touching the network; this is the decision that makes it do so.
     */
    @Test
    fun `nothing loaded means nothing shown`() {
        assertEquals(
            InterstitialBlock.NotReady,
            InterstitialPolicy.decide(allowed.copy(preloaded = false)),
        )
    }

    /**
     * Readiness is reported last so a refusal is never disguised as an
     * inventory problem. A Pro player with no ad loaded is blocked because they
     * are Pro, and a dashboard that said `not_ready` there would be counting a
     * fill-rate problem that does not exist.
     */
    @Test
    fun `a real refusal outranks an empty cache`() {
        val proAndEmpty = allowed.copy(isPro = true, preloaded = false)
        assertEquals(InterstitialBlock.Pro, InterstitialPolicy.decide(proAndEmpty))
    }

    /**
     * Every gate failing at once still names the one furthest up the order, so
     * the combination cannot be reported as whichever rule happened to be
     * evaluated last after a refactor.
     */
    @Test
    fun `all the gates closed reports the run`() {
        val everythingWrong = InterstitialConditions(
            adsEnabled = false,
            isPro = true,
            runAlive = true,
            resultsDismissed = false,
            runsThisSession = 0,
            minSessionRuns = 4,
            millisSinceLastInterstitial = 0,
            cooldownMillis = 180_000,
            millisSinceRewarded = 0,
            rewardedInFlight = true,
            rewardedGapMillis = 45_000,
            daysSinceInstall = 0,
            suppressDaysSinceInstall = 3,
            preloaded = false,
        )

        assertEquals(InterstitialBlock.RunAlive, InterstitialPolicy.decide(everythingWrong))
    }

    /**
     * The realistic combination: a returning player, four runs in, past the
     * cooldown, who took a rewarded continue on the run that just ended. Every
     * individual gate except the rewarded one is open, and the ad is still
     * refused.
     */
    @Test
    fun `a continue on the last run suppresses the interstitial after it`() {
        val afterAContinue = allowed.copy(
            runsThisSession = 6,
            millisSinceLastInterstitial = 600_000,
            millisSinceRewarded = 12_000,
        )

        assertEquals(InterstitialBlock.NearRewarded, InterstitialPolicy.decide(afterAContinue))
    }

    /**
     * A session that has already shown one, in a player old enough to see them,
     * three minutes later. This is the shape of the *second* interstitial of a
     * session and the one the cooldown exists to space out.
     */
    @Test
    fun `a second interstitial is allowed once the cooldown has run`() {
        val later = allowed.copy(
            runsThisSession = 7,
            millisSinceLastInterstitial = 200_000,
            millisSinceRewarded = 500_000,
        )

        assertNull(InterstitialPolicy.decide(later))
    }

    private companion object {
        /**
         * A player four runs into a session, a fortnight after install, past
         * every window, with an ad loaded. Every negative test below is this
         * with one field moved.
         */
        val allowed = InterstitialConditions(
            adsEnabled = true,
            isPro = false,
            runAlive = false,
            resultsDismissed = true,
            runsThisSession = 4,
            minSessionRuns = 4,
            millisSinceLastInterstitial = 600_000,
            cooldownMillis = 180_000,
            millisSinceRewarded = 600_000,
            rewardedInFlight = false,
            rewardedGapMillis = 45_000,
            daysSinceInstall = 14,
            suppressDaysSinceInstall = 3,
            preloaded = true,
        )
    }
}
