package com.dangerfield.drop2048.features.home.impl.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.ListItemAccessory
import com.dangerfield.drop2048.libraries.ui.components.ListSection
import com.dangerfield.drop2048.libraries.ui.components.ListSectionItem
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.Button
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.OutlinedTextField
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.feedback_field_label
import drop2048.libraries.resources.generated.resources.feedback_intro
import drop2048.libraries.resources.generated.resources.feedback_placeholder
import drop2048.libraries.resources.generated.resources.feedback_send
import drop2048.libraries.resources.generated.resources.feedback_sending
import drop2048.libraries.resources.generated.resources.feedback_title
import drop2048.libraries.resources.generated.resources.settings_diagnostics
import drop2048.libraries.resources.generated.resources.settings_diagnostics_hint
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

private const val FEEDBACK_CHAR_LIMIT = 200

@Composable
fun FeedbackScreen(
    state: FeedbackState,
    onAction: (FeedbackAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val canSubmit = state.message.isNotBlank() && !state.isSubmitting

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.feedback_title),
                onNavigateBack = { onAction(FeedbackAction.Back) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(
                    paddingValues = paddingValues,
                    includeImePadding = true
                ),
            verticalArrangement = Arrangement.Top
        ) {
            VerticalSpacerD1000()

            Text(
                text = stringResource(Res.string.feedback_intro),
                typography = AppTheme.typography.Body.B700,
                color = AppTheme.colors.textSecondary
            )

            VerticalSpacerD500()

            OutlinedTextField(
                value = state.message,
                onValueChange = { newValue ->
                    val limited = newValue.take(FEEDBACK_CHAR_LIMIT)
                    onAction(FeedbackAction.MessageChanged(limited))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimension.D1900),
                label = { Text(stringResource(Res.string.feedback_field_label)) },
                placeholder = { Text(stringResource(Res.string.feedback_placeholder)) },
                singleLine = false,
                minLines = 6,
                maxLines = 10,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSubmit) {
                            focusManager.clearFocus(force = true)
                            onAction(FeedbackAction.Submit)
                        }
                    }
                )
            )

            VerticalSpacerD500()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val messageLength = state.message.length
                val counterColor = if (messageLength >= FEEDBACK_CHAR_LIMIT) {
                    AppTheme.colors.danger
                } else {
                    AppTheme.colors.textSecondary
                }

                Text(
                    text = "$messageLength/$FEEDBACK_CHAR_LIMIT",
                    color = counterColor,
                    typography = AppTheme.typography.Body.B500
                )
            }

            state.errorMessage?.let {
                VerticalSpacerD500()
                Text(
                    text = it,
                    color = AppTheme.colors.danger,
                    textAlign = TextAlign.Start
                )
            }

            VerticalSpacerD1000()

            ListSection(
                items = listOf(
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_diagnostics),
                        supportingText = stringResource(Res.string.settings_diagnostics_hint),
                        accessory = ListItemAccessory.Switch(
                            checked = state.diagnosticsOptIn,
                            onCheckedChange = { onAction(FeedbackAction.ToggleDiagnostics) },
                        ),
                        onClick = { onAction(FeedbackAction.ToggleDiagnostics) },
                    ),
                ),
            )

            VerticalSpacerD1000()

            Button(
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.Large,
                enabled = canSubmit,
                onClick = {
                    focusManager.clearFocus(force = true)
                    if (canSubmit) {
                        onAction(FeedbackAction.Submit)
                    }
                }
            ) {
                Text(
                    if (state.isSubmitting) {
                        stringResource(Res.string.feedback_sending)
                    } else {
                        stringResource(Res.string.feedback_send)
                    },
                )
            }

            VerticalSpacerD500()
        }
    }
}

@Preview
@Composable
private fun FeedbackScreenPreviewDisabled() {
    PreviewContent {
        FeedbackScreen(
            state = FeedbackState(
                message = ""
            ),
            onAction = {}
        )
    }
}

@Preview
@Composable
private fun FeedbackScreenPreview() {
    PreviewContent {
        FeedbackScreen(
            state = FeedbackState(
                message = "I love this app!"
            ),
            onAction = {}
        )
    }
}
