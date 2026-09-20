package com.dangerfield.drop2048.libraries.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.button.Button
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonStyle
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonType
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

// Debug-only CTA labels — dev-facing, so constants rather than string
// resources (this dialog never renders in release builds).
private const val NetworkInspectorCta = "Network inspector"
private const val ReportBugCta = "Report a bug"
private const val DismissCta = "Dismiss"

@Composable
fun ShakeDialog(
    headline: String,
    subtext: String?,
    onDismiss: () -> Unit,
    onReportBug: () -> Unit,
    modifier: Modifier = Modifier,
    state: DialogState = rememberDialogState(),
    // Debug-only: when non-null, an extra action opens the on-device network
    // inspector. Release callers leave this null so the button never shows.
    onOpenNetworkInspector: (() -> Unit)? = null,
) {
    BasicDialog(
        state = state,
        onDismissRequest = onDismiss,
        modifier = modifier,
        topContent = {
            Text(
                text = headline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (subtext != null) {
                    Spacer(modifier = Modifier.height(Dimension.D300))
                    Text(
                        text = subtext,
                        typography = AppTheme.typography.Body.B600,
                        color = AppTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VerticalSpacerD500()
                }
            }
        },
        bottomContent = {
            Column{
                Button(
                    onClick = {
                        state.dismiss()
                        onReportBug()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    type = ButtonType.Danger,
                ) {
                    Text(ReportBugCta)
                }

                if (onOpenNetworkInspector != null) {
                    Spacer(modifier = Modifier.height(Dimension.D500))
                    Button(
                        onClick = {
                            state.dismiss()
                            onOpenNetworkInspector()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        size = ButtonSize.Medium,
                        type = ButtonType.Secondary,
                    ) {
                        Text(NetworkInspectorCta)
                    }
                }

                Spacer(modifier = Modifier.height(Dimension.D500))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    size = ButtonSize.Medium,
                    style = ButtonStyle.Text
                ) {
                    Text(DismissCta)
                }
            }
        }
    )
}

@Preview
@Composable
private fun ShakeDialogPreview_WithSubtext() {
    PreviewContent {
        ShakeDialog(
            headline = "I felt that.",
            subtext = "Testing the waters?",
            onDismiss = {},
            onReportBug = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_NoSubtext() {
    PreviewContent {
        ShakeDialog(
            headline = "Whoa.",
            subtext = null,
            onDismiss = {},
            onReportBug = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_WithInspector() {
    PreviewContent {
        ShakeDialog(
            headline = "I felt that.",
            subtext = "Testing the waters?",
            onDismiss = {},
            onReportBug = {},
            onOpenNetworkInspector = {},
        )
    }
}

@Preview
@Composable
private fun ShakeDialogPreview_LongMessage() {
    PreviewContent {
        ShakeDialog(
            headline = "You really like shaking me.",
            subtext = "I've lost count.",
            onDismiss = {},
            onReportBug = {},
        )
    }
}
