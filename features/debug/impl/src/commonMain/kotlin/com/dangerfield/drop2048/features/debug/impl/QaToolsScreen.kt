package com.dangerfield.drop2048.features.debug.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.ListItemAccessory
import com.dangerfield.drop2048.libraries.ui.components.ListSection
import com.dangerfield.drop2048.libraries.ui.components.ListSectionItem
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD500
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Tools a tester can reach on any build they are allowed to hold, and nothing
 * that cannot be undone.
 *
 * Today that is one switch, and the switch is the reason the screen exists: it
 * is the one place reachable **without** the floating button it hides. See
 * `QaToolsRoute` for why it is not a section of the debug menu, which is where
 * everything destructive still lives.
 *
 * Dev-facing, so the copy is plain constants in [DebugCopy] beside the rest of
 * the menu's, rather than string resources. The `VerifyStrings` baseline stays
 * at 8.
 */
@Composable
fun QaToolsScreen(
    state: QaToolsState,
    onAction: (QaToolsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = DebugCopy.QaTitle,
                onNavigateBack = { onAction(QaToolsAction.Back) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(paddingValues = padding),
            verticalArrangement = Arrangement.Top,
        ) {
            VerticalSpacerD500()
            Text(
                text = DebugCopy.QaBody,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
            VerticalSpacerD500()
            ListSection(
                title = DebugCopy.QaFeedbackSection,
                items = listOf(
                    ListSectionItem(
                        headlineText = DebugCopy.QaFeedbackFab,
                        supportingText = DebugCopy.QaFeedbackFabHint,
                        accessory = ListItemAccessory.Switch(
                            checked = state.feedbackFabShown,
                            onCheckedChange = { onAction(QaToolsAction.ShowFeedbackFab(it)) },
                        ),
                    ),
                ),
            )
            VerticalSpacerD500()
        }
    }
}

@Preview
@Composable
private fun QaToolsScreenPreview() {
    PreviewContent {
        QaToolsScreen(state = QaToolsState(loaded = true), onAction = {})
    }
}

@Preview
@Composable
private fun QaToolsScreenHiddenPreview() {
    PreviewContent {
        QaToolsScreen(
            state = QaToolsState(loaded = true, feedbackFabShown = false),
            onAction = {},
        )
    }
}
