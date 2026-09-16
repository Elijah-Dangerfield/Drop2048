package com.dangerfield.drop2048.libraries.leaderboards.impl

import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.leaderboards.GameServicesStatus
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.SubmitResult
import com.dangerfield.drop2048.libraries.leaderboards.platformAchievementId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RealLeaderboards], which owns four behaviours worth separating.
 *
 * **Holding something earned before sign-in.** Authentication is slow and a run
 * is not, so the first score of a session routinely happens while Game Center is
 * still deciding who the player is, and so do the first badges.
 * `heldValueIsSentWhenAuthenticationLands`, `theBestHeldValueWins` and
 * `badgesEarnedBeforeSignInAreReportedWhenAuthenticationLands` are the ones that
 * matter; a naive "send and forget" implementation passes none of them.
 *
 * **Not sending.** A lifetime total is resubmitted after every run and only
 * moves on a personal best, so most calls should reach nothing at all. Those
 * assertions are on the *contents* of `services.submissions` rather than on a
 * count alone, because a stub that dropped everything would satisfy a count.
 * `anImprovedValueIsSent` is the companion that kills that stub.
 *
 * **Sending anyway, on a recurring board.** The exception to the rule above, and
 * the one that was wrong: `aWeakerScoreIsStillSentToARecurringBoard` submits the
 * same pair of values to both boards so the recurring one's extra call is
 * visible against the other one's silence.
 *
 * **The kill switch.** `feature.leaderboards` had no reader at all, so it is
 * asserted here over all four entry points rather than over `isOfferable` alone
 * — hiding the row while still posting scores is the failure that made the
 * switch worth wiring in the first place.
 *
 * NOT covered here: GameKit itself, which no common test can reach (see
 * `GameCenterServices` in `iosMain`, which is compiled and never run by the
 * project's verification command), the board ids, which are `LeaderboardTest`,
 * and the badge ids, which are `PlatformAchievementIdTest` in the achievements
 * impl — the only module that can see both catalogs.
 */
class RealLeaderboardsTest : CoroutineTest() {

    private val services = FakeGameServices()

    private fun leaderboards(
        services: FakeGameServices = this.services,
        enabled: Boolean = true,
    ) = RealLeaderboards(
        services = services,
        appScope = AppCoroutineScope(dispatchers),
        featureEnabled = leaderboardsEnabled(enabled),
    )


    @Test
    fun aScoreReachesThePlatformUnderTheBoardsOwnId() = runUnitTest {
        leaderboards().submit(Leaderboard.AllTimeScore, 4_200L)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.AllTimeScore.id to 4_200L, services.submissions.single())
    }

    @Test
    fun twoBoardsAreSubmittedIndependently() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.AllTimeScore, 4_200L)
        leaderboards.submit(Leaderboard.WeeklyScore, 9L)

        assertEquals(
            listOf(
                Leaderboard.AllTimeScore.id to 4_200L,
                Leaderboard.WeeklyScore.id to 9L,
            ),
            services.submissions,
        )
    }

    @Test
    fun anImprovedValueIsSent() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.AllTimeScore, 500L)
        leaderboards.submit(Leaderboard.AllTimeScore, 900L)

        assertEquals(
            listOf(
                Leaderboard.AllTimeScore.id to 500L,
                Leaderboard.AllTimeScore.id to 900L,
            ),
            services.submissions,
        )
    }


    @Test
    fun aValueAlreadyAcceptedIsNotSentAgain() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.AllTimeScore, 500L)
        leaderboards.submit(Leaderboard.AllTimeScore, 500L)
        leaderboards.submit(Leaderboard.AllTimeScore, 400L)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.AllTimeScore.id to 500L, services.submissions.single())
    }

    @Test
    fun zeroIsNeverSent() = runUnitTest {
        leaderboards().submit(Leaderboard.AllTimeScore, 0L)

        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun aNegativeValueIsNeverSent() = runUnitTest {
        leaderboards().submit(Leaderboard.AllTimeScore, -1L)

        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun aRecurringBoardStillRefusesAValueOfZero() = runUnitTest {
        leaderboards().submit(Leaderboard.WeeklyScore, 0L)

        assertTrue(services.submissions.isEmpty())
    }


    @Test
    fun nothingIsSentBeforeAuthenticationResolves() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        leaderboards(services)
            .submit(Leaderboard.AllTimeScore, 4_200L)

        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun heldValueIsSentWhenAuthenticationLands() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        leaderboards(services)
            .submit(Leaderboard.AllTimeScore, 4_200L)
        assertTrue(services.submissions.isEmpty())

        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.AllTimeScore.id to 4_200L, services.submissions.single())
    }

    @Test
    fun theBestHeldValueWins() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.SignInRequired)
        val leaderboards = leaderboards(services)
        leaderboards.submit(Leaderboard.AllTimeScore, 100L)
        leaderboards.submit(Leaderboard.AllTimeScore, 900L)
        leaderboards.submit(Leaderboard.AllTimeScore, 300L)
        assertTrue(services.submissions.isEmpty())

        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(1, services.submissions.size)
        assertEquals(Leaderboard.AllTimeScore.id to 900L, services.submissions.single())
    }

    /**
     * The same value twice, on purpose. An improved value would be sent again
     * whether or not the rejection was noticed, so it would pass against an
     * implementation that treated a refusal as an acceptance.
     */
    @Test
    fun aRejectedValueIsRetriedOnTheNextSubmit() = runUnitTest {
        val leaderboards = leaderboards()
        services.result = SubmitResult.Failed
        leaderboards.submit(Leaderboard.AllTimeScore, 500L)
        assertEquals(1, services.submissions.size)

        services.result = SubmitResult.Submitted
        leaderboards.submit(Leaderboard.AllTimeScore, 500L)

        assertEquals(
            listOf(
                Leaderboard.AllTimeScore.id to 500L,
                Leaderboard.AllTimeScore.id to 500L,
            ),
            services.submissions,
        )
    }


    @Test
    fun aPlatformThatThrowsIsSurvivedAndTheNextScoreStillLands() = runUnitTest {
        val leaderboards = leaderboards()
        services.throwOnSubmit = true
        leaderboards.submit(Leaderboard.AllTimeScore, 500L)
        assertEquals(1, services.submissions.size)

        services.throwOnSubmit = false
        leaderboards.submit(Leaderboard.AllTimeScore, 500L)

        assertEquals(
            listOf(
                Leaderboard.AllTimeScore.id to 500L,
                Leaderboard.AllTimeScore.id to 500L,
            ),
            services.submissions,
        )
    }


    @Test
    fun authenticationIsStartedOnceAtConstruction() = runUnitTest {
        leaderboards()

        assertEquals(1, services.startCalls)
    }

    @Test
    fun anEntryPointIsOfferedOnlyWhenThereIsSomewhereToGo() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        val leaderboards = leaderboards(services)
        assertFalse(leaderboards.isOfferable.value)

        services.becomes(GameServicesStatus.Unavailable)
        assertFalse(leaderboards.isOfferable.value)

        services.becomes(GameServicesStatus.SignInRequired)
        assertTrue(leaderboards.isOfferable.value)

        services.becomes(GameServicesStatus.Authenticated)
        assertTrue(leaderboards.isOfferable.value)
    }

    @Test
    fun openingTheDashboardForwardsTheBoard() = runUnitTest {
        leaderboards().openDashboard(Leaderboard.WeeklyScore)

        assertEquals(listOf<String?>(Leaderboard.WeeklyScore.id), services.dashboards)
    }

    @Test
    fun openingTheDashboardWithNoBoardAsksForNoFocus() = runUnitTest {
        leaderboards().openDashboard()

        assertEquals(listOf<String?>(null), services.dashboards)
    }

    /**
     * The platform holds a sign-in screen in this state, and presenting it is
     * the only way a signed-out player ever gets onto a board.
     */
    @Test
    fun theDashboardIsStillOpenedWhenNobodyIsSignedIn() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.SignInRequired)
        leaderboards(services).openDashboard()

        assertEquals(listOf<String?>(null), services.dashboards)
    }

    /**
     * The regression this flag exists for. `submitted` describes a board the
     * platform may have reset under us — the reset happens on Game Center's
     * clock and nothing here can see it — so an app that stays alive across the
     * weekly boundary would drop its first score of the new week for failing to
     * beat last week's, and the player would not appear on the new board at all.
     *
     * The all-time board next to it is the control: the *same* two values, on a
     * board that does not recur, and the second one is correctly dropped.
     */
    @Test
    fun aWeakerScoreIsStillSentToARecurringBoard() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.WeeklyScore, 900L)
        leaderboards.submit(Leaderboard.AllTimeScore, 900L)
        leaderboards.submit(Leaderboard.WeeklyScore, 300L)
        leaderboards.submit(Leaderboard.AllTimeScore, 300L)

        assertEquals(
            listOf(
                Leaderboard.WeeklyScore.id to 900L,
                Leaderboard.AllTimeScore.id to 900L,
                Leaderboard.WeeklyScore.id to 300L,
            ),
            services.submissions,
        )
    }

    @Test
    fun anIdenticalScoreIsStillSentToARecurringBoard() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.submit(Leaderboard.WeeklyScore, 500L)
        leaderboards.submit(Leaderboard.WeeklyScore, 500L)

        assertEquals(2, services.submissions.size)
    }


    @Test
    fun aBadgeIsReportedUnderItsPlatformId() = runUnitTest {
        leaderboards().reportUnlocked(setOf("FirstMerge"))

        assertEquals(listOf(platformAchievementId("FirstMerge")), services.reports)
    }

    /**
     * The caller declares the whole earned set every time, so the second call
     * carries the first call's badge again. Reporting it twice would be harmless
     * on the platform and is still wrong here: it is a network call per badge
     * per run, forever.
     */
    @Test
    fun aBadgeAlreadyAcceptedIsNotReportedAgain() = runUnitTest {
        val leaderboards = leaderboards()
        leaderboards.reportUnlocked(setOf("FirstMerge"))
        leaderboards.reportUnlocked(setOf("FirstMerge", "SixtyFour"))

        assertEquals(
            listOf(
                platformAchievementId("FirstMerge"),
                platformAchievementId("SixtyFour"),
            ),
            services.reports,
        )
    }

    /**
     * The case the whole listener design is for: badges earned while signed out.
     * Without the hold they would be reported once, to nobody, and never again —
     * the stored set does not change, so nothing would re-declare them.
     */
    @Test
    fun badgesEarnedBeforeSignInAreReportedWhenAuthenticationLands() = runUnitTest {
        val services = FakeGameServices(initial = GameServicesStatus.Unknown)
        leaderboards(services).reportUnlocked(setOf("FirstMerge", "SixtyFour"))
        assertTrue(services.reports.isEmpty())

        services.becomes(GameServicesStatus.Authenticated)

        assertEquals(2, services.reports.size)
        assertEquals(
            setOf(
                platformAchievementId("FirstMerge"),
                platformAchievementId("SixtyFour"),
            ),
            services.reports.toSet(),
        )
    }

    @Test
    fun aRejectedBadgeIsRetriedOnTheNextDeclaration() = runUnitTest {
        val leaderboards = leaderboards()
        services.reportResult = SubmitResult.Failed
        leaderboards.reportUnlocked(setOf("FirstMerge"))
        assertEquals(1, services.reports.size)

        services.reportResult = SubmitResult.Submitted
        leaderboards.reportUnlocked(setOf("FirstMerge"))

        assertEquals(2, services.reports.size)
    }

    @Test
    fun aPlatformThatThrowsOnAReportIsSurvived() = runUnitTest {
        val leaderboards = leaderboards()
        services.throwOnReport = true
        leaderboards.reportUnlocked(setOf("FirstMerge"))

        services.throwOnReport = false
        leaderboards.reportUnlocked(setOf("FirstMerge"))

        assertEquals(2, services.reports.size)
    }


    /**
     * SPEC 10's kill switch, which was defined, documented and tested in
     * `:libraries:gameconfig` and then read by nothing at all — so switching it
     * off switched nothing off.
     *
     * It is checked here rather than at the entry point because hiding the row
     * would leave the app still posting scores to a feature the owner has turned
     * off, and the reason to reach for this switch is a board that has gone
     * wrong.
     */
    @Test
    fun theKillSwitchStopsSubmissions() = runUnitTest {
        leaderboards(enabled = false).submit(Leaderboard.AllTimeScore, 4_200L)

        assertTrue(services.submissions.isEmpty())
    }

    @Test
    fun theKillSwitchStopsAchievementReports() = runUnitTest {
        leaderboards(enabled = false).reportUnlocked(setOf("FirstMerge"))

        assertTrue(services.reports.isEmpty())
    }

    @Test
    fun theKillSwitchStopsTheDashboard() = runUnitTest {
        leaderboards(enabled = false).openDashboard()

        assertTrue(services.dashboards.isEmpty())
    }

    @Test
    fun theKillSwitchOffersNoEntryPointEvenWhenSignedIn() = runUnitTest {
        assertFalse(leaderboards(enabled = false).isOfferable.value)
    }
}
