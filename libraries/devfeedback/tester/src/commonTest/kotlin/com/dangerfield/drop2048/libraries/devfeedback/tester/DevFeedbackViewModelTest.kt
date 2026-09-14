package com.dangerfield.drop2048.libraries.devfeedback.tester

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The panel the owner files work from, and what rides along with a note.
 *
 * The attachments are the point. A directive with no screenshot is a sentence
 * somebody has to reproduce from, so the frame the panel opened over is sent by
 * default and that is asserted at the reporter rather than on state. Removing it
 * removes it from the submission too, which is the half that would otherwise
 * only clear the preview.
 *
 * Two refusals shape the form. Whitespace is not a directive and is not filed.
 * Typing past the character limit truncates rather than growing an attachment
 * nothing will read.
 *
 * A failed send still confirms, and that is a deliberate choice rather than
 * swallowed error handling: Sentry is disabled on a local build, and retyping
 * the same directive into the same disabled Sentry is not a recovery. Asking for
 * one would train the owner to ignore the panel.
 *
 * ### Not here
 *
 * That the log rides unconditionally on a directive and stays behind the opt-in
 * for a player is decided in `AppTelemetry`, not in this view model, and is
 * asserted by `FeedbackKindPolicyTest`. That the triage skill's queries match
 * the tags the app emits is `FeedbackTriageQueryContractTest`.
 */
class DevFeedbackViewModelTest : CoroutineTest() {

    @Test
    fun filingSendsTheDirectiveWithItsScreenshot() = runUnitTest {
        val reporter = RecordingReporter()
        val viewModel = DevFeedbackViewModel(reporter)

        viewModel.takeAction(DevFeedbackAction.Open(Screenshot(byteArrayOf(1, 2, 3))))
        viewModel.takeAction(DevFeedbackAction.MessageChanged("  make the fuse bigger  "))
        viewModel.takeAction(DevFeedbackAction.Submit)

        val filed = reporter.filed.single()
        assertEquals("make the fuse bigger", filed.message, "it is trimmed on the way out")
        assertContentEquals(byteArrayOf(1, 2, 3), filed.screenshots.single())
    }

    @Test
    fun aRemovedScreenshotIsNotSent() = runUnitTest {
        val reporter = RecordingReporter()
        val viewModel = DevFeedbackViewModel(reporter)

        viewModel.takeAction(DevFeedbackAction.Open(Screenshot(byteArrayOf(9))))
        viewModel.takeAction(DevFeedbackAction.MessageChanged("the pause overlay eats the settings row"))
        viewModel.takeAction(DevFeedbackAction.RemoveScreenshot)
        viewModel.takeAction(DevFeedbackAction.Submit)

        assertTrue(
            reporter.filed.single().screenshots.isEmpty(),
            "removing it has to reach the submission, not only the preview",
        )
    }

    @Test
    fun anEmptyDirectiveIsNotFiled() = runUnitTest {
        val reporter = RecordingReporter()
        val viewModel = DevFeedbackViewModel(reporter)

        viewModel.takeAction(DevFeedbackAction.Open(null))
        viewModel.takeAction(DevFeedbackAction.MessageChanged("   \n  "))
        viewModel.takeAction(DevFeedbackAction.Submit)

        assertTrue(reporter.filed.isEmpty())
        assertFalse(
            viewModel.stateFlow.value.sent,
            "and it does not claim to have been, which would lose the words",
        )
    }

    @Test
    fun typingIsCappedAtTheCharacterLimit() = runUnitTest {
        val viewModel = DevFeedbackViewModel(RecordingReporter())

        viewModel.takeAction(DevFeedbackAction.MessageChanged("x".repeat(DirectiveCharLimit * 2)))

        assertEquals(DirectiveCharLimit, viewModel.stateFlow.value.message.length)
    }

    @Test
    fun aFailedSendStillConfirms() = runUnitTest {
        val viewModel = DevFeedbackViewModel(FailingReporter)

        viewModel.takeAction(DevFeedbackAction.Open(null))
        viewModel.takeAction(DevFeedbackAction.MessageChanged("the board ring is clipped"))
        viewModel.takeAction(DevFeedbackAction.Submit)

        assertTrue(
            viewModel.stateFlow.value.sent,
            "Sentry is off on a local build; an error state here has no recovery to offer",
        )
    }

    @Test
    fun theFormIsClearedAfterFilingSoTheNextDirectiveStartsBlank() = runUnitTest {
        val viewModel = DevFeedbackViewModel(RecordingReporter())

        viewModel.takeAction(DevFeedbackAction.Open(Screenshot(byteArrayOf(4))))
        viewModel.takeAction(DevFeedbackAction.MessageChanged("one thing"))
        viewModel.takeAction(DevFeedbackAction.Submit)

        val state = viewModel.stateFlow.value
        assertEquals("", state.message)
        assertNull(state.screenshot)
        assertFalse(state.isSubmitting)
    }

    private data class Filing(val message: String, val screenshots: List<ByteArray>)

    private class RecordingReporter : OwnerDirectiveReporter {
        val filed = mutableListOf<Filing>()

        override suspend fun fileDirective(
            message: String,
            screenshots: List<ByteArray>,
        ): Catching<Unit> {
            filed += Filing(message, screenshots)
            return Catching { }
        }
    }

    private object FailingReporter : OwnerDirectiveReporter {
        override suspend fun fileDirective(
            message: String,
            screenshots: List<ByteArray>,
        ): Catching<Unit> = Catching { error("no DSN") }
    }
}
