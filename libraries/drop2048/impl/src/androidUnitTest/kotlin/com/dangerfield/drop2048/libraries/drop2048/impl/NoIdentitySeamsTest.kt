package com.dangerfield.drop2048.libraries.drop2048.impl

import com.dangerfield.drop2048.libraries.drop2048.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Nothing that could carry a person's identity to Sentry exists on the
 * [Telemetry] seam.
 *
 * §1 of `docs/store/data-safety.md` opens by saying nothing the app collects can
 * be linked by us to a name, an email or an account, because none exists. Before
 * C13a that was true of the tree and enforced by nothing: `Telemetry.setUser`
 * was declared and implemented against `Sentry.setUser` with no caller, and
 * `captureUserFeedback` carried dead `email` and `screenshots` parameters.
 * (`screenshots` has a caller again since C16 — see below.)
 *
 * The hazard is not the dead code. It is that a one-line call is the natural
 * thing to write the day somebody adds a contact field to the feedback form, and
 * **nothing would fail** — the declaration would already be waiting. The seams
 * are gone; this is what stops them coming back quietly.
 *
 * JVM reflection rather than a compile-time shape, because a re-added parameter
 * with a default value would not break any caller, and a test that only fails on
 * a breaking change is not watching the case that actually happens.
 */
class NoIdentitySeamsTest {

    private val methods = Telemetry::class.java.methods

    @Test
    fun `the telemetry seam declares no way to set a user`() {
        val identitySetters = methods.filter { it.name.startsWith("setUser") }
        assertTrue(
            identitySetters.isEmpty(),
            "Telemetry declares ${identitySetters.map { it.name }}. " +
                "An identity reaching Sentry is what data-safety.md §1 says cannot happen.",
        )
    }

    /**
     * `message`, `kind`, `eventId`, `errorCode`, `attachSessionLog`,
     * `screenshots`.
     *
     * Counting rather than naming, because JVM method reflection does not carry
     * Kotlin parameter names. An added `email` moves the count, and so does
     * anything else nobody thought about — which is the right failure: this test
     * should be re-read, not silently widened.
     *
     * **`screenshots` came back in C16 and is not the parameter this test was
     * written about.** The one it objected to was dead: declared, unreachable,
     * and waiting for the day somebody wired a picker into the player's feedback
     * form. This one has exactly one caller, in `:libraries:devfeedback:tester`,
     * a module held off the release classpath by Gradle
     * (`verifyNoDevFeedbackInRelease`). The player's own seam,
     * `FeedbackRepository`, still cannot express an image at all, and
     * `PlayerFeedbackSeamTest` in `:features:home:impl` is what holds that.
     */
    @Test
    fun `feedback carries six things and none of them is a contact detail`() {
        val feedback = methods.single { it.name == "captureUserFeedback" }
        assertEquals(
            FeedbackParameterCount,
            feedback.parameterCount,
            "captureUserFeedback's shape changed. If a parameter was added, check it cannot " +
                "carry a person's identity or anything settings_diagnostics_hint promises is " +
                "never sent, then update this count.",
        )
        assertTrue(
            feedback.parameterTypes.count { it == List::class.java } == 1,
            "captureUserFeedback's list parameters changed. There is exactly one, it is the " +
                "tester panel's screenshots, and a second list is a new way for something to " +
                "leave that nobody has read the promise for.",
        )
    }

    private companion object {
        const val FeedbackParameterCount = 6
    }
}
