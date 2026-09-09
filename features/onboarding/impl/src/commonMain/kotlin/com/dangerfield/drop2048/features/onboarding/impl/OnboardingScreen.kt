package com.dangerfield.drop2048.features.onboarding.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.app_name
import drop2048.libraries.resources.generated.resources.onboarding_start
import drop2048.libraries.resources.generated.resources.onboarding_tagline
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * First-launch welcome. One way out, into the game. A placeholder until C5
 * replaces the body with the guided tutorial; all routing lives in
 * [OnboardingViewModel] so this composable stays a pure render of the state.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    Screen(
        contentWindowInsets = WindowInsets.systemBars,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimension.D800),
            ) {
                Spacer(modifier = Modifier.height(Dimension.D1200))
                Text(
                    text = stringResource(Res.string.app_name),
                    typography = AppTheme.typography.Heading.H800,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = stringResource(Res.string.onboarding_tagline),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(Dimension.D1200))

                ButtonPrimary(
                    onClick = { onAction(OnboardingAction.Start) },
                    enabled = !state.isFinishing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.onboarding_start))
                }
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    PreviewContent {
        OnboardingScreen(
            state = OnboardingState(),
            onAction = {},
        )
    }
}
