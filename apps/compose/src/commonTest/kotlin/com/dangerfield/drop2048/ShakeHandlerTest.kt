package com.dangerfield.drop2048

import com.dangerfield.drop2048.libraries.core.ShakeDetector
import com.dangerfield.drop2048.libraries.core.ShakeEvent
import com.dangerfield.drop2048.libraries.core.ShakeMessage
import com.dangerfield.drop2048.libraries.core.ShakeMessageContext
import com.dangerfield.drop2048.libraries.core.ShakeMessageProvider
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.navigation.Route
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.ShakeDialogRoute
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a shake reaches the report dialog, and, the part that was broken,
 * whether it still does the second time.
 *
 * Ported from Sodogku, which hit all of this first. The suppression flag used
 * to be set the moment the handler asked to navigate and cleared by an
 * `onDialogDismissed()` that nothing called, so shake-to-report worked exactly
 * once per process and then never again.
 *
 * NOT covered here: the accelerometer maths, which lives in `:libraries:core`,
 * and the binding of start/stop to app visibility, which is a
 * `LifecycleStartEffect` and needs a real lifecycle to mean anything.
 */
class ShakeHandlerTest : CoroutineTest() {

    @Test
    fun aShakeOpensTheDialog() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        handler(detector, router).start()

        detector.shake()

        assertEquals(1, router.navigations.size)
        assertTrue(router.navigations.single() is ShakeDialogRoute)
    }

    @Test
    fun theGestureStillWorksAfterTheDialogHasBeenDismissedOnce() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = handler(detector, router)
        handler.start()

        detector.shake()
        handler.onDialogShown()
        handler.onDialogDismissed()

        detector.shake()

        assertEquals(2, router.navigations.size, "shake-to-report is not a one-shot")
    }

    @Test
    fun aShakeWhileTheDialogIsUpIsSwallowed() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = handler(detector, router)
        handler.start()

        detector.shake()
        handler.onDialogShown()
        detector.shake()
        detector.shake()

        assertEquals(1, router.navigations.size)
    }

    @Test
    fun aNavigationThatNeverBecomesADialogDoesNotDisableTheGesture() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        handler(detector, router).start()

        // The router drops navigation while a blocking error screen is up, so
        // the dialog is never shown and `onDialogShown` never runs. A flag set
        // at navigate time would latch on here and stay on forever.
        detector.shake()
        detector.shake()

        assertEquals(2, router.navigations.size)
    }

    @Test
    fun stoppingEndsTheSensorAndTheCollection() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = handler(detector, router)
        handler.start()

        handler.stop()
        detector.shake()

        assertFalse(detector.isRunning)
        assertEquals(0, router.navigations.size)
    }

    @Test
    fun restartingDoesNotStackASecondCollector() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = handler(detector, router)

        // One backgrounding and one resume. Before `stop` cancelled the
        // collection this left two collectors on the stream, so one shake
        // opened two dialogs.
        handler.start()
        handler.stop()
        handler.start()

        detector.shake()

        assertTrue(detector.isRunning, "the sensor is listening again after a resume")
        assertEquals(1, router.navigations.size, "one shake is one dialog")
    }

    @Test
    fun startingTwiceDoesNotStackASecondCollector() = runUnitTest {
        val detector = FakeShakeDetector()
        val router = RecordingRouter()
        val handler = handler(detector, router)

        handler.start()
        handler.start()

        detector.shake()

        assertEquals(1, router.navigations.size)
    }

    private fun handler(detector: FakeShakeDetector, router: RecordingRouter) =
        ShakeHandler(detector, FixedMessages, router)
}

/** The copy is not what these tests are about, so it is one fixed line. */
private object FixedMessages : ShakeMessageProvider {
    override fun getMessage(context: ShakeMessageContext) =
        ShakeMessage("I felt that.", null)
}

/**
 * Mirrors the shape both real detectors use: broadcast, no replay, and an event
 * emitted with no subscriber is dropped rather than held for the next start.
 */
private class FakeShakeDetector : ShakeDetector {

    private val events = MutableSharedFlow<ShakeEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val shakeEvents: SharedFlow<ShakeEvent> = events

    private var starts = 0
    private var stops = 0

    val isRunning: Boolean get() = starts > stops

    override fun start() {
        starts++
    }

    override fun stop() {
        stops++
    }

    fun shake() {
        events.tryEmit(ShakeEvent(timestampMs = 0))
    }
}

private class RecordingRouter : Router {

    val navigations = mutableListOf<Route>()

    override fun navigate(route: Route, options: NavigationOptions) {
        navigations += route
    }

    override fun goBack() = Unit

    override fun popBackTo(route: Route, inclusive: Boolean) = Unit

    override fun openWebLink(url: String) = Unit
}
