package com.dangerfield.drop2048.libraries.drop2048

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The line C13a drew, held from the side C16 came at it from.
 *
 * C13a found that a feedback report was carrying `extra.score`, `extra.level`
 * and `extra.highest_tier` whatever the diagnostics switch said, and gated the
 * session log on the opt-in while clearing the carrier's breadcrumbs (L74). C16
 * then added a channel that wants both unconditionally. That is safe only for as
 * long as exactly one kind is the owner's, and this is the test that says so out
 * loud rather than leaving it to be read off an `==` in `AppTelemetry`.
 *
 * The three tag values are asserted literally because they are the coupling
 * between this enum and the `feedback-triage` skill, and a rename here is silent
 * at both ends — Sentry keeps accepting the report and the skill's query starts
 * matching nothing. `FeedbackTriageQueryContractTest` holds the other half of
 * that; this holds the floor.
 */
class FeedbackKindPolicyTest {

    @Test
    fun exactlyOneKindIsTheOwnersOwnChannel() {
        val owner = FeedbackKind.entries.filter { it.isOwnerChannel }

        assertEquals(
            listOf(FeedbackKind.OwnerDirective),
            owner,
            "the owner channel attaches the session log and keeps its breadcrumbs without " +
                "asking. A second kind on this list is a player's report doing the same.",
        )
    }

    @Test
    fun neitherPlayerFacingKindIsTheOwnersChannel() {
        assertFalse(FeedbackKind.Feedback.isOwnerChannel)
        assertFalse(
            FeedbackKind.BugReport.isOwnerChannel,
            "a bug report is the one most tempting to give the log to, and it is still a " +
                "player's. settings_diagnostics_hint is what they read before deciding.",
        )
    }

    @Test
    fun theThreeChannelsAreTheOnesTriageSearchesFor() {
        // A floor with a name: the split between an owner's instruction and a
        // player's report is the whole reason the tag exists, and collapsing
        // them back into one value would pass the tests above.
        assertEquals(
            setOf("owner_directive", "bug_report", "feedback"),
            FeedbackKind.entries.map { it.tag }.toSet(),
        )
    }

    @Test
    fun everyKindHasACarrierMessageOfItsOwn() {
        // The carrier message is the Sentry issue title. Two kinds sharing one
        // would still be told apart by the tag, but the issue list would read as
        // if a player had filed the owner's directives.
        val messages = FeedbackKind.entries.map { it.carrierMessage }

        assertEquals(messages.size, messages.toSet().size)
        assertTrue(messages.none { it.isBlank() })
    }
}
