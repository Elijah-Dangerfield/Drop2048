package com.dangerfield.drop2048.features.onboarding.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.libraries.identity.auth.OAuthProvider
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonGhost
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSecondary
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.libraries.ui.components.text.OutlinedTextField
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Two-step onboarding UI. [OnboardingStep.Welcome] offers the entry paths
 * (guest / OAuth / email); [OnboardingStep.PickIdentity] commits a display
 * name and finishes the flow. All routing decisions live in
 * [OnboardingViewModel] — this composable is a pure render of the state.
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
                .padding(padding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimension.D800),
            ) {
                when (state.step) {
                    OnboardingStep.Welcome -> WelcomeStep(state, onAction)
                    OnboardingStep.PickIdentity -> PickIdentityStep(state, onAction)
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val busy = state.oauthInFlight != null

    Spacer(modifier = Modifier.height(Dimension.D1200))
    Text(
        text = "Welcome",
        typography = AppTheme.typography.Heading.H800,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(Dimension.D400))
    Text(
        text = "Jump right in as a guest, or sign in to pick up where you left off.",
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Dimension.D1200))

    ButtonPrimary(
        onClick = { onAction(OnboardingAction.ContinueAsGuest) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Continue as guest")
    }

    Spacer(modifier = Modifier.height(Dimension.D500))

    ButtonSecondary(
        onClick = { onAction(OnboardingAction.SignInWithOAuth(OAuthProvider.Google)) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (state.oauthInFlight == OAuthProvider.Google) "Opening browser…"
            else "Continue with Google",
        )
    }

    if (state.appleEnabled) {
        Spacer(modifier = Modifier.height(Dimension.D400))
        ButtonSecondary(
            onClick = { onAction(OnboardingAction.SignInWithApple) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.oauthInFlight == OAuthProvider.Apple) "Waiting for Apple…"
                else "Sign in with Apple",
            )
        }
    }

    state.authError?.let {
        Spacer(modifier = Modifier.height(Dimension.D400))
        Text(
            text = when (it) {
                OnboardingAuthError.OAuthProviderNotEnabled ->
                    "That sign-in method isn't available right now."
                OnboardingAuthError.OAuthNetworkError ->
                    "Couldn't reach the server. Check your connection and try again."
                OnboardingAuthError.OAuthFailed ->
                    "Sign-in didn't go through. Please try again."
            },
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(modifier = Modifier.height(Dimension.D700))

    ButtonGhost(
        onClick = { onAction(OnboardingAction.SignIn) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Sign in with email")
    }
    ButtonGhost(
        onClick = { onAction(OnboardingAction.SignUp) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Create an account")
    }
    Spacer(modifier = Modifier.height(Dimension.D800))
}

@Composable
private fun PickIdentityStep(
    state: OnboardingState,
    onAction: (OnboardingAction) -> Unit,
) {
    val canGoBack = !state.creationStarted && !state.identityClaimed

    Spacer(modifier = Modifier.height(Dimension.D200))
    if (canGoBack) {
        ButtonGhost(
            onClick = { onAction(OnboardingAction.Back) },
            icon = Icons.ChevronLeft(null),
        ) {
            Text("Back")
        }
    }
    Spacer(modifier = Modifier.height(Dimension.D700))

    Text(
        text = "Pick a display name",
        typography = AppTheme.typography.Heading.H700,
    )
    Spacer(modifier = Modifier.height(Dimension.D300))
    Text(
        text = "This is how you'll show up in the app. You can change it later.",
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )

    Spacer(modifier = Modifier.height(Dimension.D700))

    OutlinedTextField(
        value = state.displayName,
        onValueChange = { onAction(OnboardingAction.DisplayNameChanged(it)) },
        enabled = !state.isFinishing,
        singleLine = true,
        isError = state.saveError != null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(
            onGo = { onAction(OnboardingAction.ContinueFromPickIdentity) },
        ),
        label = { Text("Display name") },
        supportingText = state.saveError?.let {
            {
                Text(
                    text = when (it) {
                        OnboardingSaveError.DisplayNameTaken -> "That name is taken — try another."
                        OnboardingSaveError.InvalidDisplayName -> "That name won't work — try another."
                    },
                    typography = AppTheme.typography.Body.B400,
                    color = AppTheme.colors.danger,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Dimension.D300))

    ButtonGhost(
        onClick = { onAction(OnboardingAction.RegenerateDisplayName) },
        enabled = !state.isFinishing,
    ) {
        Text("Suggest another")
    }

    Spacer(modifier = Modifier.height(Dimension.D800))

    ButtonPrimary(
        onClick = { onAction(OnboardingAction.ContinueFromPickIdentity) },
        enabled = !state.isFinishing,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (state.isFinishing) "Setting things up…" else "Continue")
    }
    Spacer(modifier = Modifier.height(Dimension.D800))
}

@Preview
@Composable
private fun OnboardingScreenPreview_Welcome() {
    PreviewContent {
        OnboardingScreen(
            state = OnboardingState(appleEnabled = true),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview_PickIdentity() {
    PreviewContent {
        OnboardingScreen(
            state = OnboardingState(
                step = OnboardingStep.PickIdentity,
                displayName = "QuietFox72",
            ),
            onAction = {},
        )
    }
}
