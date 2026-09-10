package com.dangerfield.drop2048.features.gate.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.features.gate.BlockingGate
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.Button
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonGhost
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.gate_force_update_action
import drop2048.libraries.resources.generated.resources.gate_force_update_body
import drop2048.libraries.resources.generated.resources.gate_force_update_title
import drop2048.libraries.resources.generated.resources.gate_legal_accept
import drop2048.libraries.resources.generated.resources.gate_legal_body
import drop2048.libraries.resources.generated.resources.gate_legal_title
import drop2048.libraries.resources.generated.resources.gate_maintenance_title
import drop2048.libraries.resources.generated.resources.settings_privacy_policy
import drop2048.libraries.resources.generated.resources.settings_terms
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The wall.
 *
 * One screen for all three blocking gates, because they are the same shape: a
 * headline, a sentence and at most one thing to do about it. There is no back
 * arrow and no dismiss anywhere on it, and there is nothing underneath it — the
 * host renders this *instead of* the nav host rather than over it.
 *
 * The maintenance gate's body is the operator's own text, verbatim. That is why
 * `resolveLaunchGates` refuses to raise it with an empty message: this screen
 * would otherwise be a blank apology.
 */
@Composable
fun LaunchGateScreen(
    gate: BlockingGate,
    onAction: (LaunchGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Screen(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenContentPadding(paddingValues = padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (gate) {
                BlockingGate.ForceUpdate -> {
                    GateHeadline(stringResource(Res.string.gate_force_update_title))
                    GateBody(stringResource(Res.string.gate_force_update_body))
                    VerticalSpacerD1000()
                    Button(
                        size = ButtonSize.Large,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onAction(LaunchGateAction.OpenStore) },
                    ) {
                        Text(stringResource(Res.string.gate_force_update_action))
                    }
                }

                is BlockingGate.Maintenance -> {
                    GateHeadline(stringResource(Res.string.gate_maintenance_title))
                    // No button. There is genuinely nothing the player can do,
                    // and a control that does nothing is worse than none.
                    GateBody(gate.message)
                }

                is BlockingGate.ReacceptLegal -> {
                    GateHeadline(stringResource(Res.string.gate_legal_title))
                    GateBody(stringResource(Res.string.gate_legal_body))
                    VerticalSpacerD500()
                    ButtonGhost(
                        size = ButtonSize.Small,
                        onClick = { onAction(LaunchGateAction.OpenTerms) },
                    ) {
                        Text(stringResource(Res.string.settings_terms))
                    }
                    ButtonGhost(
                        size = ButtonSize.Small,
                        onClick = { onAction(LaunchGateAction.OpenPrivacy) },
                    ) {
                        Text(stringResource(Res.string.settings_privacy_policy))
                    }
                    VerticalSpacerD1000()
                    Button(
                        size = ButtonSize.Large,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onAction(
                                LaunchGateAction.AcceptLegal(
                                    termsVersion = gate.termsVersion,
                                    privacyVersion = gate.privacyVersion,
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(Res.string.gate_legal_accept))
                    }
                }
            }
        }
    }
}

@Composable
private fun GateHeadline(text: String) {
    Text(
        text = text,
        typography = AppTheme.typography.Heading.H900,
        color = AppTheme.colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    VerticalSpacerD500()
}

@Composable
private fun GateBody(text: String) {
    Text(
        text = text,
        typography = AppTheme.typography.Body.B600,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview
@Composable
private fun ForceUpdateGatePreview() {
    PreviewContent {
        LaunchGateScreen(gate = BlockingGate.ForceUpdate, onAction = {})
    }
}

@Preview
@Composable
private fun MaintenanceGatePreview() {
    PreviewContent {
        LaunchGateScreen(
            gate = BlockingGate.Maintenance("We are moving some furniture. Back within the hour."),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun LegalGatePreview() {
    PreviewContent {
        LaunchGateScreen(
            gate = BlockingGate.ReacceptLegal(termsVersion = 2, privacyVersion = 2),
            onAction = {},
        )
    }
}
