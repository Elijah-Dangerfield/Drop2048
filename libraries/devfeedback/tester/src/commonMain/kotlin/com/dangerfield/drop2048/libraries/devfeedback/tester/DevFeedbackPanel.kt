package com.dangerfield.drop2048.libraries.devfeedback.tester

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Surface
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonGhost
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.text.OutlinedTextField
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.toImageBitmap
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.VerticalSpacerD300
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD700
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The directive form, covering whatever the owner was looking at.
 *
 * An overlay rather than a nav destination on purpose: it has to be reachable
 * from a dialog, a bottom sheet or mid-cascade, and pushing a route would both
 * disturb the back stack it is meant to describe and change the very screen the
 * attached screenshot was taken of.
 *
 * Full screen rather than an inset panel. Nothing is drawn under it and
 * [Surface] swallows pointer events, so the board underneath cannot be nudged by
 * accident while the form is up — which matters here, because the board
 * underneath is a live run with a block falling on it.
 */
@Composable
fun DevFeedbackPanel(
    state: DevFeedbackState,
    onAction: (DevFeedbackAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.isOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                color = AppTheme.colors.background,
                contentColor = AppTheme.colors.onBackground,
                radius = Radii.None,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.sent) SentConfirmation(onAction) else DirectiveForm(state, onAction)
            }
        }
    }
}

@Composable
private fun DirectiveForm(
    state: DevFeedbackState,
    onAction: (DevFeedbackAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Dimension.D700),
    ) {
        Text(text = Copy.Title, typography = AppTheme.typography.Heading.H700)
        VerticalSpacerD300()
        Text(
            text = Copy.Subtitle,
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        VerticalSpacerD700()

        OutlinedTextField(
            value = state.message,
            onValueChange = { onAction(DevFeedbackAction.MessageChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimension.D1900)
                .semantics { contentDescription = Copy.MessageFieldDescription },
            placeholder = { Text(Copy.Placeholder) },
            singleLine = false,
            minLines = 5,
            maxLines = 12,
        )

        state.screenshot?.let { shot ->
            VerticalSpacerD500()
            ScreenshotRow(shot, onRemove = { onAction(DevFeedbackAction.RemoveScreenshot) })
        }

        VerticalSpacerD700()

        ButtonPrimary(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = Copy.SubmitDescription },
            size = ButtonSize.Large,
            enabled = state.canSubmit,
            onClick = { onAction(DevFeedbackAction.Submit) },
        ) {
            Text(if (state.isSubmitting) Copy.Sending else Copy.Submit)
        }

        VerticalSpacerD300()

        ButtonGhost(
            modifier = Modifier.fillMaxWidth(),
            size = ButtonSize.Large,
            onClick = { onAction(DevFeedbackAction.Dismiss) },
        ) {
            Text(Copy.Cancel)
        }
    }
}

@Composable
private fun ScreenshotRow(screenshot: Screenshot, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val bitmap = remember(screenshot) { screenshot.bytes.toImageBitmap() }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = Copy.ScreenshotDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(Dimension.D1500)
                    .height(Dimension.D1900),
            )
        } else {
            Text(text = Copy.ScreenshotAttached, typography = AppTheme.typography.Body.B500)
        }

        ButtonGhost(
            size = ButtonSize.Small,
            onClick = onRemove,
            modifier = Modifier.semantics { contentDescription = Copy.RemoveScreenshotDescription },
        ) {
            Text(Copy.RemoveScreenshot)
        }
    }
}

@Composable
private fun SentConfirmation(onAction: (DevFeedbackAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Dimension.D700),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = Copy.SentTitle, typography = AppTheme.typography.Heading.H700)
        VerticalSpacerD300()
        Text(
            text = Copy.SentBody,
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )
        VerticalSpacerD700()
        ButtonPrimary(
            size = ButtonSize.Large,
            onClick = { onAction(DevFeedbackAction.Dismiss) },
            modifier = Modifier.semantics { contentDescription = Copy.DoneDescription },
        ) {
            Text(Copy.Done)
        }
    }
}

/**
 * Plain constants and deliberately not in `:libraries:resources`, which is what
 * `DebugCopy` and the config-override screen already do and for the same reason:
 * nothing here is ever seen by a player. The panel only exists in a debug or
 * TestFlight build, so putting this in `strings.xml` would ask a translator to
 * localise developer tooling and would grow the shipped resource table for
 * nobody. The `VerifyStrings` baseline stays at 8.
 */
private object Copy {
    const val Title = "Leave a directive"
    const val Subtitle = "Files to Sentry as owner_directive, with the session log and the " +
        "screenshot. Triage turns it into a TODO."
    const val Placeholder = "What should change?"
    const val Submit = "File it"
    const val Sending = "Filing…"
    const val Cancel = "Cancel"
    const val RemoveScreenshot = "Remove"
    const val ScreenshotAttached = "Screenshot attached"
    const val SentTitle = "Filed"
    const val SentBody = "It will show up in the next triage pass."
    const val Done = "Done"

    const val MessageFieldDescription = "Directive message"
    const val SubmitDescription = "File directive"
    const val RemoveScreenshotDescription = "Remove screenshot"
    const val ScreenshotDescription = "Attached screenshot"
    const val DoneDescription = "Done"
}

@Preview
@Composable
private fun DevFeedbackPanelPreview() {
    PreviewContent {
        DevFeedbackPanel(
            state = DevFeedbackState(
                isOpen = true,
                message = "The chain callout should hold until the board settles, not with it.",
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun DevFeedbackPanelSentPreview() {
    PreviewContent {
        DevFeedbackPanel(state = DevFeedbackState(isOpen = true, sent = true), onAction = {})
    }
}
