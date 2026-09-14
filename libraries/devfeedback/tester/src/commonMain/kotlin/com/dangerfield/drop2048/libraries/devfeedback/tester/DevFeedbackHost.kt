package com.dangerfield.drop2048.libraries.devfeedback.tester

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.isTesterBuild
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.devfeedback.DevFeedback
import com.dangerfield.drop2048.libraries.devfeedback.DevFeedbackFabCache
import com.dangerfield.drop2048.libraries.devfeedback.FabPlacement
import com.dangerfield.drop2048.libraries.devfeedback.NoDevFeedback
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Wraps the app so the owner can reach the directive form from any screen.
 *
 * One trigger: a small button floating over everything, draggable, which stays
 * where it is put. See [DevFeedbackFab] for why it has to move and how it avoids
 * taking touches meant for the board.
 *
 * ### The three layers that keep this out of a player's build
 *
 * It `replaces` `NoDevFeedback`, and it only exists at all in a compilation that
 * has `:libraries:devfeedback:tester` on its classpath — which on Android is the
 * debug variant and nothing else (`verifyNoDevFeedbackInRelease` checks, rather
 * than trusting). The [BuildInfo.isTesterBuild] check below is the third layer
 * and the only one iOS gets, because Kotlin/Native has no build-type source
 * sets. It is also what makes a TestFlight build work: TestFlight ships the
 * release binary, so nothing structural can tell it apart from the App Store,
 * and `isTestFlight` is a runtime read of the receipt.
 *
 * When that check says no, the wrapper draws [content] and nothing else. No
 * pointer handler, no per-frame layer recording, no panel in the tree.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DevFeedback::class, replaces = [NoDevFeedback::class])
@Inject
class DevFeedbackHost(
    private val viewModel: DevFeedbackViewModel,
    private val fabCache: DevFeedbackFabCache,
) : DevFeedback {

    @Composable
    override fun Host(content: @Composable () -> Unit) {
        if (!BuildInfo.isTesterBuild) {
            content()
            return
        }

        val state by viewModel.stateFlow.collectAsState()
        val scope = rememberCoroutineScope()
        // Recording the content into a layer is what makes the screenshot
        // possible without a platform capture API and without a permission. It
        // costs a draw indirection on every frame, which is exactly why it only
        // exists in a tester build.
        val captureLayer = rememberGraphicsLayer()

        fun open() {
            if (state.isOpen) return
            scope.launch {
                // Grab the frame before the panel covers it. A failed capture is
                // not worth blocking the report over; the form opens either way.
                val screenshot = Catching { captureLayer.toImageBitmap().encodeToJpeg() }
                    .logOnFailure { "Could not capture the directive screenshot" }
                    .getOrNull()
                    ?.let(::Screenshot)
                viewModel.takeAction(DevFeedbackAction.Open(screenshot))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        captureLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(captureLayer)
                    },
            ) {
                content()
            }

            FabLayer(cache = fabCache, onClick = ::open)

            // Last, so the panel covers the button rather than the other way
            // round. Nothing hides the button while the form is open because
            // nothing needs to: the panel is full screen and opaque.
            DevFeedbackPanel(state = state, onAction = viewModel::takeAction)
        }
    }
}

/**
 * The button's own state read, kept in a composable of its own.
 *
 * Deliberately not read in [DevFeedbackHost.Host]: a state read there recomposes
 * the host, and the host's content lambda is the entire app. Dropping the button
 * in a new place would re-run the whole tree's composition for a position change
 * nothing above this node cares about — on a screen that is already running a
 * cascade animation.
 *
 * Nothing is drawn until the first value arrives from disk. The alternative is
 * to start at the default and jump once the read lands, which also flashes a
 * button the owner has switched off.
 */
@Composable
private fun FabLayer(cache: DevFeedbackFabCache, onClick: () -> Unit) {
    val stored by cache.updates.collectAsState(initial = null)
    val settings = stored ?: return
    if (settings.hidden) return

    val scope = rememberCoroutineScope()
    // Remembered so its identity is stable, because `pointerInput` is keyed on
    // it: a fresh lambda each composition would tear down and rebuild the
    // gesture detector, cancelling a drag in progress.
    val onSettled: (FabPlacement) -> Unit = remember(cache, scope) {
        { placement ->
            scope.launch {
                Catching { cache.update { it.withPlacement(placement) } }
                    .logOnFailure { "Could not remember where the directive button was put" }
            }
        }
    }

    DevFeedbackFab(
        placement = settings.placement,
        onSettled = onSettled,
        onClick = onClick,
    )
}
