package com.dangerfield.drop2048

import com.dangerfield.drop2048.libraries.core.ShakeDetector
import com.dangerfield.drop2048.libraries.core.ShakeEvent
import com.dangerfield.drop2048.libraries.core.ShakeMessageContext
import com.dangerfield.drop2048.libraries.core.ShakeMessageProvider
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.ShakeDialogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Turns a shake into the report dialog.
 *
 * Ported from Sodogku, which found and fixed all of this first. The version
 * here set a suppression flag the moment it asked to navigate and cleared it in
 * an `onDialogDismissed()` that nothing called, so the gesture worked exactly
 * once per process.
 *
 * [isDialogOnScreen] is now set by the dialog destination itself, in
 * `ShakeDialogEntryPoint`, and never by the act of asking to navigate. A flag
 * set at navigate time is a latch: a navigation that gets dropped, which the
 * router does while a blocking error screen is up, would leave it stuck on with
 * no dialog to turn it back off.
 *
 * [start] and [stop] follow the app's visibility rather than its process
 * lifetime. [stop] cancels the collection rather than only stopping the sensor,
 * and [start] refuses to stack a second collector, so a background-and-resume
 * cycle does not leave two collectors opening two dialogs per shake.
 */
@Inject
@SingleIn(AppScope::class)
class ShakeHandler(
    private val shakeDetector: ShakeDetector,
    private val shakeMessageProvider: ShakeMessageProvider,
    private val router: Router,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collection: Job? = null
    private var isDialogOnScreen = false

    /** Process-local flavour counter for the shake easter-egg copy. */
    private var shakeCount = 0

    fun start() {
        if (collection?.isActive == true) return
        collection = scope.launch {
            shakeDetector.shakeEvents.collect { event -> onShake(event) }
        }
        shakeDetector.start()
    }

    fun stop() {
        shakeDetector.stop()
        collection?.cancel()
        collection = null
    }

    fun onDialogShown() {
        isDialogOnScreen = true
    }

    fun onDialogDismissed() {
        isDialogOnScreen = false
    }

    private fun onShake(event: ShakeEvent) {
        if (isDialogOnScreen) return

        val message = shakeMessageProvider.getMessage(
            ShakeMessageContext(
                shakeCount = shakeCount,
                intensity = event.intensity,
                isLateNight = false,
                isFirstSession = false,
                userName = null,
            ),
        )

        router.navigate(
            ShakeDialogRoute(
                headline = message.headline,
                subtext = message.subtext,
            )
        )

        shakeCount++
    }
}
