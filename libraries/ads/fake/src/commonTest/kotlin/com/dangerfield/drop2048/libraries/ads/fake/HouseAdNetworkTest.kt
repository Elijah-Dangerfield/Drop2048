package com.dangerfield.drop2048.libraries.ads.fake

import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The house network answers everything the real one can, and suspends while it
 * does.
 *
 * The suspending half is worth a test of its own rather than being taken on
 * trust. `RewardedClock` is held across `AdNetwork.show`, and it is the forward
 * half of SPEC 12's 45-second rule — the one direction that is not arithmetic
 * and cannot be produced with a movable clock. A stand-in that returned
 * immediately would leave that window as unexercised on a device as it was
 * before this chunk, which is the specific failure the chunk exists to fix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseAdNetworkTest {

    @Test
    fun aRewardedAdWatchedToTheEndRewards() = runTest {
        val network = HouseAdNetwork()

        val showing = async { network.show(AdFormat.Rewarded) }
        runCurrent()
        network.finish(AdShowResult.Rewarded)

        assertEquals(AdShowResult.Rewarded, showing.await().result)
    }

    /**
     * The one outcome that has to be produced by hand, and the only one that
     * withholds. `RealAdGate` maps every other non-reward to a grant.
     */
    @Test
    fun aRewardedAdClosedEarlyIsDismissedAndNotRewarded() = runTest {
        val network = HouseAdNetwork()

        val showing = async { network.show(AdFormat.Rewarded) }
        runCurrent()
        network.finish(AdShowResult.Dismissed)

        val result = showing.await().result
        assertEquals(AdShowResult.Dismissed, result)
        assertFalse(result == AdShowResult.Rewarded)
    }

    @Test
    fun showSuspendsUntilTheAdIsFinished() = runTest {
        val network = HouseAdNetwork()

        val showing = async { network.show(AdFormat.Interstitial) }
        runCurrent()

        assertTrue(showing.isActive)
        assertNotNull(network.showing.value)

        network.finish(AdShowResult.Dismissed)
        showing.await()

        assertNull(network.showing.value)
    }

    /**
     * Every failure the SDK can report, without an SDK. These are the answers
     * the surrounding logic only runs on: a granted continue after a no-fill, a
     * silently skipped interstitial after a `NotShown`, a kind carried into
     * `ads.rewarded_result`.
     */
    @Test
    fun aForcedOutcomeIsAnsweredWithoutDrawingAnything() = runTest {
        val failures = listOf(
            AdShowResult.NoFill,
            AdShowResult.Offline,
            AdShowResult.Failed,
            AdShowResult.NotShown,
            AdShowResult.Dismissed,
        )

        failures.forEach { forced ->
            val network = HouseAdNetwork()
            network.forceOutcome(forced)

            val outcome = network.show(AdFormat.Rewarded)

            assertEquals(forced, outcome.result)
            assertEquals(HouseAdNetwork.ForcedErrorKind, outcome.errorKind)
            assertNull(network.showing.value)
        }
    }

    /** SPEC 12.3's "preloaded, and skipped silently if not ready", made reachable. */
    @Test
    fun readinessIsWhatQaSaysItIs() {
        val network = HouseAdNetwork()

        assertTrue(network.isReady(AdFormat.Interstitial))

        network.reportReady(false)

        assertFalse(network.isReady(AdFormat.Interstitial))
    }

    /**
     * A second ad of the same format is a different ad, so the surface's
     * countdown restarts rather than inheriting the previous one's remaining
     * seconds.
     */
    @Test
    fun eachShowGetsItsOwnId() = runTest {
        val network = HouseAdNetwork()

        val first = async { network.show(AdFormat.Interstitial) }
        runCurrent()
        val firstId = network.showing.value?.id
        network.finish(AdShowResult.Dismissed)
        first.await()

        val second = async { network.show(AdFormat.Interstitial) }
        runCurrent()
        val secondId = network.showing.value?.id
        network.finish(AdShowResult.Dismissed)
        second.await()

        assertNotNull(firstId)
        assertNotNull(secondId)
        assertTrue(secondId > firstId)
    }

    /** Nothing to resolve is not a crash. The surface can outlive its ad by a frame. */
    @Test
    fun finishingWithNothingOnScreenDoesNothing() {
        HouseAdNetwork().finish(AdShowResult.Rewarded)
    }
}
