package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.RewardOutcome
import com.dangerfield.drop2048.libraries.billing.PaywallTrigger
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SPEC 12 at the one place it touches a live board.
 *
 * The gates themselves are tested in `:libraries:ads:impl`, without a game. What
 * only this file can hold is the *call sites*: that the continue is offered on a
 * board the player can still see, that the countdown ends the run on its own,
 * that an ad failure never costs a continue, and — the one that matters —
 * **that no interstitial is ever requested while a run is alive.**
 *
 * That last one is checked against the ViewModel rather than against the policy,
 * because the policy refusing is only half of the guarantee. The other half is
 * that nothing in the game asks at the wrong moment, and a gate that answered
 * "no" to a question nobody should be asking would still be a bug waiting for
 * the day somebody makes the gate more permissive.
 */
class AdsAtTheBoardTest : CoroutineTest() {

    @Test
    fun `stacking out offers the continue over the board`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()

            assertPhase(GamePhase.ContinueOffer)
            assertEquals(8, state.continueSecondsLeft)
        }
    }

    /**
     * SPEC 12.2: the board is the argument. The state still carries the board
     * and the score the run reached, which is what the screen draws unblurred
     * behind the scrim — and `GameScreen` is the only thing that can decide not
     * to blur, so this asserts the half the ViewModel owns.
     */
    @Test
    fun `the offer keeps the board and the score on the state`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()

            assertTrue(state.board.blockCount > 0, "the board the offer is about")
            assertTrue(state.score > 0, "the score the offer preserves")
        }
    }

    @Test
    fun `the countdown declines the offer on its own`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            assertPhase(GamePhase.ContinueOffer)

            advance(8_000)

            assertPhase(GamePhase.StackedOut)
            assertTrue(ads.requests.isEmpty(), "an expired offer must not show an ad")
        }
    }

    @Test
    fun `accepting shows a rewarded ad and puts the run back`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            val scoreAtDeath = state.score

            act(GameAction.ContinueAccept)

            assertEquals(listOf(AdPlacement.ContinueRun), ads.requests)
            assertPhase(GamePhase.Playing)
            assertEquals(scoreAtDeath, state.score, "SPEC 12: the score is preserved")
        }
    }

    /**
     * The engine half of SPEC 12's continue, asserted through the ViewModel: the
     * top three rows are gone and the level has dropped by one.
     */
    @Test
    fun `the continue clears the top rows and drops a level`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0), level = 5) {
            land()
            waitOutResolution()

            act(GameAction.ContinueAccept)

            assertEquals(4, state.level)
            repeat(3) { row ->
                assertFalse(landedIn(row), "row $row should have been cleared")
            }
        }
    }

    /**
     * The rule the whole `RewardOutcome` hierarchy exists for. A no-fill is not
     * the player saying no, and a board ten minutes in the building is not
     * something an ad network's bad afternoon gets to take.
     */
    @Test
    fun `an ad that cannot be served still continues the run`() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            ads = FakeAdGate(outcome = RewardOutcome.NoFill),
        ) {
            land()
            waitOutResolution()

            act(GameAction.ContinueAccept)

            assertPhase(GamePhase.Playing)
        }
    }

    @Test
    fun `closing the ad early is the only thing that ends the run`() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            ads = FakeAdGate(outcome = RewardOutcome.Dismissed),
        ) {
            land()
            waitOutResolution()

            act(GameAction.ContinueAccept)

            assertPhase(GamePhase.StackedOut)
        }
    }

    /**
     * SPEC 12's "a 2nd at higher friction": the second continue is not offered,
     * it is reached for. The sheet carries it, and taking it goes straight to the
     * ad with no countdown, because a sheet is not something that expires.
     */
    @Test
    fun `the second continue is on the sheet rather than offered`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            act(GameAction.ContinueAccept)
            assertPhase(GamePhase.Playing)

            landAgainOnto()

            assertPhase(GamePhase.StackedOut)
            assertTrue(state.continueAvailable, "the sheet should carry the second continue")

            act(GameAction.ContinueAgain)

            assertPhase(GamePhase.Playing)
            assertEquals(2, ads.requests.size)
        }
    }

    /** SPEC 12's hard cap of two. The third is not on the sheet and is refused. */
    @Test
    fun `a third continue is neither offered nor accepted`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            act(GameAction.ContinueAccept)
            landAgainOnto()
            act(GameAction.ContinueAgain)
            landAgainOnto()

            assertFalse(state.continueAvailable, "the cap is two")

            act(GameAction.ContinueAgain)

            assertPhase(GamePhase.StackedOut)
            assertEquals(2, ads.requests.size)
        }
    }

    /**
     * **The governing principle.** Nothing asks the interstitial gate anything
     * while a run is alive — not during play, not during a cascade, not while
     * the pause overlay is up, and not while the continue offer is on screen.
     */
    @Test
    fun `no interstitial is requested while a run is alive`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            tick(3)
            assertEquals(0, interstitials.shows)

            act(GameAction.Pause)
            assertEquals(0, interstitials.shows)
            act(GameAction.Resume)

            land()
            assertEquals(0, interstitials.shows)

            waitOutResolution()
            assertPhase(GamePhase.ContinueOffer)
            assertEquals(0, interstitials.shows, "the offer is not a dismissal")

            act(GameAction.ContinueDecline)
            assertEquals(0, interstitials.shows, "the results are still up")
        }
    }

    /**
     * The one moment SPEC 12 allows. "Drop again" *is* the dismissal, and the
     * next run starts on the other side of the ad rather than behind it.
     */
    @Test
    fun `dismissing the results is the only thing that asks for an interstitial`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            act(GameAction.ContinueDecline)
            assertEquals(0, interstitials.shows)

            act(GameAction.Restart)

            assertEquals(1, interstitials.shows)
            assertPhase(GamePhase.Playing)
        }
    }

    @Test
    fun `quitting the results also dismisses them`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            act(GameAction.ContinueDecline)

            act(GameAction.Quit, GameAction.ConfirmQuit)

            assertEquals(1, interstitials.shows)
        }
    }

    /** Quitting mid-run is not a dismissal: there are no results to dismiss. */
    @Test
    fun `quitting a live run asks for nothing`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            act(GameAction.Pause, GameAction.Quit, GameAction.ConfirmQuit)

            assertEquals(0, interstitials.shows)
        }
    }

    /**
     * Feeds SPEC 12's 45-second rule in the direction that is easy to forget: no
     * interstitial right after a rewarded ad. The gate needs telling, and this is
     * the call site that tells it — including on a dismissal, because the player
     * still sat in front of an advert.
     */
    @Test
    fun `a rewarded continue tells the interstitial gate about itself`() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            ads = FakeAdGate(outcome = RewardOutcome.Dismissed),
        ) {
            land()
            waitOutResolution()
            act(GameAction.ContinueAccept)

            assertEquals(1, interstitials.rewardedNotices)
        }
    }

    @Test
    fun `every finished run is counted towards the session gate`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            act(GameAction.ContinueDecline)

            assertEquals(1, interstitials.runsFinished)
        }
    }

    /**
     * SPEC 12's one non-modal card. The claim happens when the run ends, once,
     * so a rotation cannot spend a second session's worth of card on the same
     * sheet.
     */
    @Test
    fun `the stacked-out sheet claims the session card once`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            act(GameAction.ContinueDecline)

            assertTrue(state.showUpsell)
            assertEquals(1, paywall.cardClaims)
        }
    }

    @Test
    fun `a session that has spent its card draws none`() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            paywall = FakePaywallCoordinator(cardAvailable = false),
        ) {
            land()
            waitOutResolution()
            act(GameAction.ContinueDecline)

            assertFalse(state.showUpsell)
        }
    }

    @Test
    fun `tapping the card opens the paywall and buys nothing`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()
            act(GameAction.ContinueDecline)

            act(GameAction.OpenPro)

            assertEquals(listOf(PaywallTrigger.StackedOut), paywall.offers)
        }
    }

    /**
     * SPEC 12's third paywall surface, wired on 2026-09-20 (D28).
     *
     * `PaywallTrigger.Continue` was declared in C10 with a written rationale and
     * **zero call sites anywhere, including tests**, which is a documented
     * surface that did not exist. This is the test that stops it going back to
     * that: the offer draws the line, tapping it reaches the coordinator, and it
     * arrives under its own trigger rather than borrowing the card's.
     */
    @Test
    fun `the continue offer carries a Pro entry point`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            land()
            waitOutResolution()

            assertPhase(GamePhase.ContinueOffer)
            assertTrue(state.proOnContinue, "the offer draws a Pro line")

            act(GameAction.OpenProFromContinue)

            assertEquals(listOf(PaywallTrigger.Continue), paywall.offers)
        }
    }

    /**
     * A dead control is worse than no control. The coordinator refuses every
     * trigger but `Direct` when `pro.upsell.enabled` is off, and it refuses all
     * of them for a player who already owns Pro, so the offer asks first.
     */
    @Test
    fun `the continue offer draws no Pro line when the paywall would refuse it`() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            paywall = FakePaywallCoordinator(offerable = false),
        ) {
            land()
            waitOutResolution()

            assertPhase(GamePhase.ContinueOffer)
            assertFalse(state.proOnContinue)
        }
    }

    /**
     * The banner's policy half (D28), which is the only half a ViewModel test
     * can see. Whether an ad actually arrives is the surface's, and the layout
     * consequence of it not arriving is `BoardTakesTheStripTest`'s.
     */
    @Test
    fun `the banner is allowed while ads are on and refused when they are not`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            assertTrue(state.bannerAllowed)
        }

        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            banners = FakeBannerAds(allowed = false),
        ) {
            assertFalse(state.bannerAllowed)
        }
    }

    /**
     * A banner on screen is an ad this player has seen, which is what the Pro
     * card is gated on. A banner that never filled is not, and reporting one
     * would put the "Tired of the ads?" lie back with a different source.
     */
    @Test
    fun `only a filled banner counts as an ad the player has seen`() = runUnitTest {
        playing(picture = StackedOutBoard, fallingAt = Cell(2, 0)) {
            act(GameAction.BannerFilled(filled = false))

            assertEquals(0, banners.fills)

            act(GameAction.BannerFilled(filled = true))

            assertEquals(1, banners.fills)
        }
    }

    /**
     * L63 makes the *session* the taint and gates the three writes that claim a
     * player did something: `run_record`, leaderboard submission and the
     * achievement fact log. A continue is none of those, and `endRun` refuses
     * all three for a debug run whether it was continued or not — so a
     * continued debug run still reaches the economy exactly as much as it did
     * before, which is not at all.
     *
     * It is allowed for the reason the Pro grant is: a tester who cannot reach
     * the screen cannot test it, and this screen is the one SPEC 19's menu is
     * most needed for — reaching a stacked-out board on purpose is what the
     * preset boards and the forced-block queue exist to do.
     */
    @Test
    fun `a debug run may still continue and still records nothing`() = runUnitTest {
        playing(
            picture = StackedOutBoard,
            fallingAt = Cell(2, 0),
            debug = FakeDebugController(debugSession = true),
        ) {
            land()
            waitOutResolution()

            assertPhase(GamePhase.ContinueOffer)

            act(GameAction.ContinueAccept)

            assertPhase(GamePhase.Playing)
            assertTrue(progress.recorded.isEmpty(), "a debug run writes no run_record")
        }
    }

    /**
     * Drops blocks until the board stacks out again, which is what a second
     * continue needs and what no single action can produce: the continue cleared
     * the top three rows, so the run has to be killed a second time.
     */
    /**
     * Drops block after block, steering none of them, until the run stacks out
     * again — which is what a player who took a continue and then gave up looks
     * like, and all these tests need from the board between two continues.
     *
     * The budget used to be 60 and used to be reached in a handful of drops,
     * because every block entered the column the fixture had filled to the top.
     * Since the 2026-09-20 ruling a block enters anywhere, so the stack builds
     * across all five columns and level-1 twos merge with each other on the way.
     * It still terminates — the board is finite and nothing but a merge or a
     * burst removes a block — it now takes between 80 and 100 drops, measured by
     * walking the budget down until these tests failed. 250 is the headroom on
     * that, not a number anything is expected to approach.
     *
     * Running out of budget now fails here and says so. It used to fall through
     * and leave the caller asserting `StackedOut` against a run that was still
     * happily playing, which reads as the continue cap being broken.
     */
    private fun GameScenario.landAgainOnto() {
        var dropped = 0
        while (state.phase == GamePhase.Playing) {
            assertTrue(dropped < RefillBudget, "the board did not refill inside $RefillBudget drops")
            land()
            waitOutResolution()
            dropped++
        }
    }

    private companion object {
        /**
         * One column filled to the top, so the next block into it ends the run.
         * The same picture `RunEndReportingTest` uses, for the same reason.
         */
        val StackedOutBoard = """
            .  .  4  .  .
            .  .  8  .  .
            .  .  16 .  .
            .  .  32 .  .
            .  .  64 .  .
            .  .  128 . .
            .  .  256 . .
        """

        const val RefillBudget = 250
    }
}
