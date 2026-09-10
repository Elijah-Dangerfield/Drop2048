package com.dangerfield.drop2048.features.settings.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dangerfield.drop2048.libraries.ui.components.button.Button
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonType
import com.dangerfield.drop2048.libraries.ui.components.dialog.BasicDialog
import com.dangerfield.drop2048.libraries.ui.components.text.OutlinedTextField
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD500
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.settings_cancel
import drop2048.libraries.resources.generated.resources.settings_continue
import drop2048.libraries.resources.generated.resources.settings_delete_data
import drop2048.libraries.resources.generated.resources.settings_delete_warn_body
import drop2048.libraries.resources.generated.resources.settings_delete_warn_title
import drop2048.libraries.resources.generated.resources.settings_erase
import drop2048.libraries.resources.generated.resources.settings_reset_confirm_body
import drop2048.libraries.resources.generated.resources.settings_reset_confirm_label
import drop2048.libraries.resources.generated.resources.settings_reset_confirm_title
import drop2048.libraries.resources.generated.resources.settings_reset_confirm_word
import drop2048.libraries.resources.generated.resources.settings_reset_warn_body
import drop2048.libraries.resources.generated.resources.settings_reset_warn_title
import org.jetbrains.compose.resources.stringResource

/**
 * The two destructive flows.
 *
 * **Reset progress is two dialogs, and the second one asks for a typed word.**
 * This app has no account and no server copy of anything, so a reset here is
 * final in a way it is not in an app that can restore — the same asymmetry that
 * made L33 narrow `fallbackToDestructiveMigration`. Two taps of "yes" in the
 * same place on screen is one accidental double-tap; a typed word is not.
 *
 * The word is a translated string and the typed text never leaves this
 * composition. The comparison itself happens in the ViewModel, so the rule is
 * testable without a composition.
 */
@Composable
fun SettingsDialogs(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    when (state.dialog) {
        null -> Unit

        SettingsDialog.ResetProgressWarn -> BasicDialog(
            title = stringResource(Res.string.settings_reset_warn_title),
            description = stringResource(Res.string.settings_reset_warn_body),
            primaryButtonText = stringResource(Res.string.settings_continue),
            secondaryButtonText = stringResource(Res.string.settings_cancel),
            onDismissRequest = { onAction(SettingsAction.DismissDialog) },
            onPrimaryButtonClicked = {
                onAction(SettingsAction.ShowDialog(SettingsDialog.ResetProgressConfirm))
            },
            onSecondaryButtonClicked = { onAction(SettingsAction.DismissDialog) },
        )

        SettingsDialog.ResetProgressConfirm -> ResetConfirmDialog(onAction)

        SettingsDialog.DeleteLocalDataWarn -> BasicDialog(
            title = stringResource(Res.string.settings_delete_warn_title),
            description = stringResource(Res.string.settings_delete_warn_body),
            primaryButtonText = stringResource(Res.string.settings_delete_data),
            secondaryButtonText = stringResource(Res.string.settings_cancel),
            onDismissRequest = { onAction(SettingsAction.DismissDialog) },
            onPrimaryButtonClicked = { onAction(SettingsAction.ConfirmDeleteLocalData) },
            onSecondaryButtonClicked = { onAction(SettingsAction.DismissDialog) },
        )
    }
}

@Composable
private fun ResetConfirmDialog(onAction: (SettingsAction) -> Unit) {
    val required = stringResource(Res.string.settings_reset_confirm_word)
    var typed by remember { mutableStateOf("") }
    val armed = typed.trim().equals(required, ignoreCase = true)

    BasicDialog(
        onDismissRequest = { onAction(SettingsAction.DismissDialog) },
        topContent = {
            Text(
                text = stringResource(Res.string.settings_reset_confirm_title),
                typography = AppTheme.typography.Heading.H700,
            )
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.settings_reset_confirm_body),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                )
                VerticalSpacerD500()
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.settings_reset_confirm_label)) },
                    singleLine = true,
                )
            }
        },
        bottomContent = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    size = ButtonSize.Medium,
                    enabled = armed,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onAction(SettingsAction.ConfirmResetProgress(typed = typed, required = required))
                    },
                ) {
                    Text(stringResource(Res.string.settings_erase))
                }
                VerticalSpacerD500()
                Button(
                    size = ButtonSize.Medium,
                    type = ButtonType.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAction(SettingsAction.DismissDialog) },
                ) {
                    Text(stringResource(Res.string.settings_cancel))
                }
            }
        },
    )
}
