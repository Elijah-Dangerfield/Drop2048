package com.dangerfield.drop2048.features.home.impl.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The player's route into Sentry cannot send an image, and cannot dress a
 * player's report up as the owner's.
 *
 * C13a deleted `captureUserFeedback`'s dead `screenshots` parameter and
 * `NoIdentitySeamsTest` pinned its absence, on the argument that a one-line call
 * is the natural thing to write the day somebody adds a picker to the feedback
 * form and **nothing would fail**, because the declaration would already be
 * waiting. C16 put that parameter back — with one caller, in a module a release
 * build does not contain.
 *
 * So the pin moved here, to the seam the player's screens actually talk to. This
 * is the interface a picker on the feedback form would have to go through, and
 * there is no argument on it that could carry one.
 *
 * The second test is the C16-shaped version of the same worry. The kind is what
 * decides whether a report takes the session log and its breadcrumbs without
 * asking, and this interface deliberately cannot express the owner's kind: it
 * takes `isBugReport: Boolean`, so the only two reports it can produce are the
 * two that honour `diagnosticsOptIn`. A `FeedbackKind` parameter appearing here
 * would be the moment that stopped being true.
 *
 * JVM reflection rather than a compile-time shape, for `NoIdentitySeamsTest`'s
 * reason: a re-added parameter *with a default value* breaks no caller, and that
 * is exactly how this comes back.
 */
class PlayerFeedbackSeamTest {

    /**
     * Matched by prefix, not by name. `Catching` is an inline value class over
     * `Result`, so returning one gets the method a mangled JVM name
     * (`submitFeedback-hUnOzRk`) that changes with the signature — an exact name
     * here would be a test that goes red for the wrong reason on any edit and
     * green on none. `$default` is the synthetic bridge the default arguments
     * generate.
     */
    private val submit = FeedbackRepository::class.java.methods
        .single { it.name.startsWith("submitFeedback") && !it.name.contains("\$default") }

    @Test
    fun `the player's seam takes no list, so it can send no image`() {
        assertTrue(
            submit.parameterTypes.none { it == List::class.java },
            "FeedbackRepository takes a list. settings_diagnostics_hint tells the player what " +
                "a report contains, and a screenshot is not on that list.",
        )
    }

    @Test
    fun `the player's seam cannot name a feedback kind`() {
        val kinds = submit.parameterTypes.filter { it.isEnum }

        assertEquals(
            emptyList(),
            kinds,
            "FeedbackRepository takes an enum: ${kinds.map { it.simpleName }}. If that is " +
                "FeedbackKind, a player's report can now be filed as an owner directive, which " +
                "attaches their session log and their breadcrumbs without asking. See " +
                "FeedbackKind.isOwnerChannel.",
        )
    }
}
