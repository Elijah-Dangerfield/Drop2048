package com.dangerfield.drop2048.features.gate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mostly a list of ways an outage must **not** be able to reach a wall.
 *
 * A force-update gate is the only config value in this project that can brick
 * every install at once, so the interesting cases here are the ones where the
 * config is missing, zero, half-written or nonsense, and the answer has to be
 * "the player plays".
 */
class LaunchGatesTest {

    @Test
    fun `no config at all blocks nobody`() {
        val gates = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 12,
                minSupportedVersionCode = 0,
                softUpdateVersionCode = 0,
                softUpdateDismissedFor = 0,
            ),
            maintenance = MaintenanceInputs(mode = "", message = ""),
            legal = legal(),
        )
        assertNull(gates.blocking)
        assertNull(gates.notice)
    }

    @Test
    fun `a build that cannot say what version it is is never force updated`() {
        val gates = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 0,
                minSupportedVersionCode = 99,
                softUpdateVersionCode = 99,
                softUpdateDismissedFor = 0,
            ),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(),
        )
        assertNull(gates.blocking)
        assertNull(gates.notice)
    }

    @Test
    fun `below the minimum supported build is a wall`() {
        val gates = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 11,
                minSupportedVersionCode = 12,
                softUpdateVersionCode = 0,
                softUpdateDismissedFor = 0,
            ),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(),
        )
        assertEquals(BlockingGate.ForceUpdate, gates.blocking)
    }

    /**
     * The positive control for the test above it (L35): the only thing that
     * changes between the two is the installed build, so a pass here proves the
     * comparison is what raised the wall rather than something else in the inputs.
     */
    @Test
    fun `at the minimum supported build is not a wall`() {
        val gates = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 12,
                minSupportedVersionCode = 12,
                softUpdateVersionCode = 0,
                softUpdateDismissedFor = 0,
            ),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(),
        )
        assertNull(gates.blocking)
    }

    @Test
    fun `blocking maintenance with no message raises no gate at all`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(mode = MaintenanceBlocking, message = "   "),
            legal = legal(),
        )
        assertNull(gates.blocking)
        assertNull(gates.notice)
    }

    @Test
    fun `blocking maintenance with a message is a wall carrying that message`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(mode = "Blocking", message = "  Back at noon UTC.  "),
            legal = legal(),
        )
        assertEquals(BlockingGate.Maintenance("Back at noon UTC."), gates.blocking)
    }

    @Test
    fun `a mode nobody declared resolves to off`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(mode = "blokcing", message = "Back at noon."),
            legal = legal(),
        )
        assertNull(gates.blocking)
        assertNull(gates.notice)
    }

    @Test
    fun `force update outranks maintenance`() {
        val gates = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 11,
                minSupportedVersionCode = 12,
                softUpdateVersionCode = 0,
                softUpdateDismissedFor = 0,
            ),
            maintenance = MaintenanceInputs(mode = MaintenanceBlocking, message = "Back at noon."),
            legal = legal(acceptedTerms = 1, terms = 5, forceBelow = 5),
        )
        assertEquals(BlockingGate.ForceUpdate, gates.blocking)
    }

    @Test
    fun `a banner the player closed does not come back this session`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(
                mode = MaintenanceBanner,
                message = "Scores are slow to post.",
                dismissedBanner = "Scores are slow to post.",
            ),
            legal = legal(),
        )
        assertNull(gates.notice)
    }

    @Test
    fun `rewording a dismissed banner says it again`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(
                mode = MaintenanceBanner,
                message = "Scores are slow to post. Sorry.",
                dismissedBanner = "Scores are slow to post.",
            ),
            legal = legal(),
        )
        assertEquals(NoticeGate.Maintenance("Scores are slow to post. Sorry."), gates.notice)
    }

    @Test
    fun `a dismissed banner cannot wave away a wall`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(
                mode = MaintenanceBlocking,
                message = "Down for an hour.",
                dismissedBanner = "Down for an hour.",
            ),
            legal = legal(),
        )
        assertEquals(BlockingGate.Maintenance("Down for an hour."), gates.blocking)
    }

    @Test
    fun `a first launch is never gated on legal`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(terms = 9, privacy = 9, forceBelow = 9, everAccepted = false),
        )
        assertNull(gates.blocking)
        assertNull(gates.notice)
    }

    @Test
    fun `a terms bump alone is a notice rather than a wall`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(terms = 2, acceptedTerms = 1, forceBelow = 0),
        )
        assertNull(gates.blocking)
        assertEquals(NoticeGate.LegalUpdated(termsVersion = 2, privacyVersion = 1), gates.notice)
    }

    @Test
    fun `force reaccept below the offered version is a wall`() {
        val gates = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(terms = 3, acceptedTerms = 1, forceBelow = 3),
        )
        assertEquals(BlockingGate.ReacceptLegal(termsVersion = 3, privacyVersion = 1), gates.blocking)
    }

    /**
     * The unsatisfiable case, and the one this whole cap exists for. A
     * `forceReacceptBelow` of 5 against a `termsVersion` of 3 would wall the
     * player out permanently: accepting records 3, and 3 is still below 5.
     */
    @Test
    fun `a reaccept floor above the offered version cannot brick anyone`() {
        val accepted = resolveLaunchGates(
            upgrade = upgradeOk(),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(terms = 3, privacy = 3, acceptedTerms = 3, acceptedPrivacy = 3, forceBelow = 5),
        )
        assertNull(accepted.blocking)
    }

    @Test
    fun `a soft update stays dismissed until the target moves`() {
        val dismissed = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 10,
                minSupportedVersionCode = 0,
                softUpdateVersionCode = 12,
                softUpdateDismissedFor = 12,
            ),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(),
        )
        assertNull(dismissed.notice)

        val raised = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 10,
                minSupportedVersionCode = 0,
                softUpdateVersionCode = 13,
                softUpdateDismissedFor = 12,
            ),
            maintenance = MaintenanceInputs(mode = MaintenanceOff, message = ""),
            legal = legal(),
        )
        assertEquals(NoticeGate.SoftUpdate(13), raised.notice)
    }

    @Test
    fun `a notice never rides along with a wall`() {
        val gates = resolveLaunchGates(
            upgrade = UpgradeInputs(
                installedVersionCode = 10,
                minSupportedVersionCode = 12,
                softUpdateVersionCode = 13,
                softUpdateDismissedFor = 0,
            ),
            maintenance = MaintenanceInputs(mode = MaintenanceBanner, message = "Slow scores."),
            legal = legal(terms = 2, acceptedTerms = 1),
        )
        assertTrue(gates.blocking != null)
        assertNull(gates.notice)
    }
}

private fun upgradeOk() = UpgradeInputs(
    installedVersionCode = 12,
    minSupportedVersionCode = 0,
    softUpdateVersionCode = 0,
    softUpdateDismissedFor = 0,
)

private fun legal(
    terms: Int = 1,
    privacy: Int = 1,
    acceptedTerms: Int = 1,
    acceptedPrivacy: Int = 1,
    forceBelow: Int = 0,
    everAccepted: Boolean = true,
) = LegalInputs(
    termsVersion = terms,
    privacyVersion = privacy,
    acceptedTermsVersion = acceptedTerms,
    acceptedPrivacyVersion = acceptedPrivacy,
    forceReacceptBelow = forceBelow,
    hasEverAccepted = everAccepted,
)
