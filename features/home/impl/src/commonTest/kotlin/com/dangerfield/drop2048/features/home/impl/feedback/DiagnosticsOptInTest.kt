package com.dangerfield.drop2048.features.home.impl.feedback

import com.dangerfield.drop2048.features.home.impl.bugreport.BugReportAction
import com.dangerfield.drop2048.features.home.impl.bugreport.BugReportViewModel
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.DeviceInfo
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.Router
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The diagnostics switch gates what its own copy says it gates.
 *
 * `settings_diagnostics_hint` reads *"Attaches your device model and build
 * number. Never your board, your scores or anything you typed elsewhere."*
 * Until C13a the switch did neither half of that: it appended a version string
 * with no device model, while **every** report — switch on or off, feedback or
 * bug report — carried a `session-log.txt` attachment of the session's own log
 * output. The promise was on screen and nothing was keeping it.
 *
 * Each test here pairs the two directions (L35): off sends nothing extra, on
 * sends the thing the copy names. A one-sided assertion would pass against a
 * parameter that is wired to a constant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsOptInTest : CoroutineTest() {

    @Test
    fun `feedback sent with the switch off attaches no session log`() = runUnitTest {
        val repository = RecordingFeedbackRepository()
        val viewModel = feedbackViewModel(repository, optedIn = false)

        viewModel.takeAction(FeedbackAction.MessageChanged("the board flickers"))
        viewModel.takeAction(FeedbackAction.Submit)
        runCurrent()

        assertEquals(1, repository.submissions.size)
        assertFalse(repository.submissions.single().attachSessionLog)
    }

    @Test
    fun `feedback sent with the switch on attaches the session log`() = runUnitTest {
        val repository = RecordingFeedbackRepository()
        val viewModel = feedbackViewModel(repository, optedIn = true)

        viewModel.takeAction(FeedbackAction.MessageChanged("the board flickers"))
        viewModel.takeAction(FeedbackAction.Submit)
        runCurrent()

        assertTrue(repository.submissions.single().attachSessionLog)
    }

    /**
     * The bug report form never read the preference at all, so it sent a session
     * log from players who had left the switch off. It is the same preference
     * and the same promise, on a second screen.
     */
    @Test
    fun `a bug report obeys the same switch`() = runUnitTest {
        val repository = RecordingFeedbackRepository()

        val off = bugReportViewModel(repository, optedIn = false)
        off.takeAction(BugReportAction.MessageChanged("it crashed on drop 4"))
        off.takeAction(BugReportAction.Submit)
        runCurrent()

        val on = bugReportViewModel(repository, optedIn = true)
        on.takeAction(BugReportAction.MessageChanged("it crashed on drop 4"))
        on.takeAction(BugReportAction.Submit)
        runCurrent()

        assertEquals(listOf(false, true), repository.submissions.map { it.attachSessionLog })
    }

    /**
     * The copy names two things. The build number was already there; the device
     * model was the half that had never been implemented.
     */
    @Test
    fun `the opted-in suffix carries the device model the copy promises`() {
        val plain = "the board flickers"

        assertEquals(plain, plain.withDiagnostics(optedIn = false))

        val annotated = plain.withDiagnostics(optedIn = true)
        assertTrue(annotated.startsWith(plain))
        assertTrue(annotated.contains(DeviceInfo.model))
    }

    private fun TestScope.feedbackViewModel(
        repository: RecordingFeedbackRepository,
        optedIn: Boolean,
    ) = FeedbackViewModel(
        repository = repository,
        router = NoRouter(),
        appCache = FakeAppCache(AppData(diagnosticsOptIn = optedIn)),
    ).also { runCurrent() }

    private fun TestScope.bugReportViewModel(
        repository: RecordingFeedbackRepository,
        optedIn: Boolean,
    ) = BugReportViewModel(
        repository = repository,
        router = NoRouter(),
        appCache = FakeAppCache(AppData(diagnosticsOptIn = optedIn)),
    ).also { runCurrent() }
}

private class RecordingFeedbackRepository : FeedbackRepository {
    data class Submission(val message: String, val isBugReport: Boolean, val attachSessionLog: Boolean)

    val submissions = mutableListOf<Submission>()

    override suspend fun submitFeedback(
        message: String,
        isBugReport: Boolean,
        logId: String?,
        errorCode: Int?,
        attachSessionLog: Boolean,
    ): Catching<Unit> {
        submissions += Submission(message, isBugReport, attachSessionLog)
        return Catching { }
    }
}

private class FakeAppCache(initial: AppData) : AppCache {
    private val stored = MutableStateFlow(initial)
    override val updates: Flow<AppData> = stored
    override suspend fun get(): AppData = stored.value
    override suspend fun set(value: AppData) {
        stored.value = value
    }

    override suspend fun clear() {
        stored.value = AppData()
    }
}

private class NoRouter : Router {
    override fun navigate(route: Route, options: NavigationOptions) = Unit
    override fun goBack() = Unit
    override fun popBackTo(route: Route, inclusive: Boolean) = Unit
    override fun openWebLink(url: String) = Unit
}
