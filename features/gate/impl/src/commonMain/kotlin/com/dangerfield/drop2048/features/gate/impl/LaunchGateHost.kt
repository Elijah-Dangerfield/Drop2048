package com.dangerfield.drop2048.features.gate.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dangerfield.drop2048.features.gate.NoticeGate
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.ui.components.NoticeBanner
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.notice_dismiss
import drop2048.libraries.resources.generated.resources.notice_legal
import drop2048.libraries.resources.generated.resources.notice_legal_action
import drop2048.libraries.resources.generated.resources.notice_soft_update
import drop2048.libraries.resources.generated.resources.notice_soft_update_action
import org.jetbrains.compose.resources.stringResource

/**
 * Wraps the app in its launch gates.
 *
 * Two shapes, and the difference between them is the whole feature:
 *
 * - **A blocking gate replaces [content] entirely.** The nav host is not composed
 *   at all, so there is no destination to go back to and nothing for a deep link
 *   to land on. The cost, stated plainly: a block lifting mid-session rebuilds
 *   the nav graph and returns the player to the start destination. An in-progress
 *   board survives that — it is in `AppData` — and blocks lifting mid-session are
 *   rare enough to pay for a gate that genuinely gates.
 * - **A notice is drawn over [content]**, in a `Box`, content first and banner
 *   overlaid. Deliberately not a `Column` with the banner above: an inserted
 *   sibling *before* the content changes its slot, which throws away the nav host
 *   and every screen under it every time a banner appears.
 *
 * The gate state is read here rather than in `App`, for the same reason
 * `SplashGate` exists: a state read in `App` recomposes the root, and the root
 * rebuilding the nav graph has already pushed a duplicate start destination once.
 */
@Composable
fun LaunchGateHost(
    viewModel: LaunchGateViewModel,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    viewModel.ObserveEvents { event ->
        when (event) {
            is LaunchGateEvent.OpenLink -> onOpenLink(event.url)
        }
    }

    val blocking = state.blocking
    if (blocking != null) {
        LaunchGateScreen(gate = blocking, onAction = viewModel::takeAction, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        state.notice?.let { notice ->
            LaunchNotice(
                notice = notice,
                onAction = viewModel::takeAction,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/**
 * Internal rather than private so the screenshot harness can capture the three
 * banners without standing up a ViewModel. The banner is the thing being
 * checked; what it floats over is not.
 */
@Composable
internal fun LaunchNotice(
    notice: NoticeGate,
    onAction: (LaunchGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismiss = { onAction(LaunchGateAction.DismissNotice(notice)) }
    val dismissLabel = stringResource(Res.string.notice_dismiss)

    when (notice) {
        // The operator's own words, not a resource. An incident message is
        // written during the incident.
        is NoticeGate.Maintenance -> NoticeBanner(
            text = notice.message,
            dismissLabel = dismissLabel,
            onDismiss = dismiss,
            modifier = modifier,
            icon = Icons.Tools(null),
        )

        is NoticeGate.LegalUpdated -> NoticeBanner(
            text = stringResource(Res.string.notice_legal),
            dismissLabel = dismissLabel,
            // Closing it *is* the acceptance, which is why the copy says so.
            onDismiss = dismiss,
            modifier = modifier,
            icon = Icons.Info(null),
            actionLabel = stringResource(Res.string.notice_legal_action),
            onAction = { onAction(LaunchGateAction.OpenTerms) },
        )

        is NoticeGate.SoftUpdate -> NoticeBanner(
            text = stringResource(Res.string.notice_soft_update),
            dismissLabel = dismissLabel,
            onDismiss = dismiss,
            modifier = modifier,
            icon = Icons.Info(null),
            actionLabel = stringResource(Res.string.notice_soft_update_action),
            onAction = { onAction(LaunchGateAction.OpenStore) },
        )
    }
}
