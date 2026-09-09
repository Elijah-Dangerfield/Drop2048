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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
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
 * Shared shell for the three email/password screens. Same vertical layout
 * everywhere: scrollable content, IME padding so the keyboard doesn't cover
 * the focused field.
 *
 * The intentional repetition between SignIn / SignUp screens (email +
 * password + a single big CTA) doesn't justify a deeper abstraction —
 * each screen's strings, validation, and footer link are different, and
 * extracting an `AuthFormScreen` would mostly move conditionals to a
 * config object.
 */
@Composable
private fun AuthShell(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
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
                Spacer(modifier = Modifier.height(Dimension.D200))
                ButtonGhost(
                    onClick = onBack,
                    icon = Icons.ChevronLeft(null),
                ) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.height(Dimension.D700))
                content()
                Spacer(modifier = Modifier.height(Dimension.D800))
            }
        }
    }
}

@Composable
fun SignInScreen(
    state: SignInState,
    onAction: (SignInAction) -> Unit,
    onBack: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val submit = {
        keyboardController?.hide()
        onAction(SignInAction.Submit)
    }
    AuthShell(onBack = onBack) {
        Text(
            text = "Welcome back",
            typography = AppTheme.typography.Heading.H700,
        )
        Spacer(modifier = Modifier.height(Dimension.D300))
        Text(
            text = "Sign in to pick up where you left off.",
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(Dimension.D800))
        ButtonSecondary(
            onClick = { onAction(SignInAction.SignInWithOAuth(OAuthProvider.Google)) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue with Google")
        }
        if (state.appleEnabled) {
            Spacer(modifier = Modifier.height(Dimension.D400))
            // Native sign-in — runs the system sheet via the coordinator, not
            // the web flow.
            ButtonSecondary(
                onClick = { onAction(SignInAction.SignInWithApple) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign in with Apple")
            }
        }
        Spacer(modifier = Modifier.height(Dimension.D700))
        Text(
            text = "or use your email",
            typography = AppTheme.typography.Body.B400,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Dimension.D700))

        EmailField(
            value = state.email,
            enabled = !state.isSubmitting,
            onChange = { onAction(SignInAction.EmailChanged(it)) },
        )
        Spacer(modifier = Modifier.height(Dimension.D500))
        PasswordField(
            value = state.password,
            onValueChange = { onAction(SignInAction.PasswordChanged(it)) },
            label = "Password",
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Go,
            onImeAction = { submit() },
        )

        state.error?.let {
            Spacer(modifier = Modifier.height(Dimension.D400))
            ErrorText(it.message())
        }

        Spacer(modifier = Modifier.height(Dimension.D300))

        ButtonGhost(
            onClick = onForgotPassword,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Forgot password?")
        }

        Spacer(modifier = Modifier.height(Dimension.D500))

        ButtonPrimary(
            onClick = { submit() },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSubmitting) "Signing in…" else "Sign in")
        }

        Spacer(modifier = Modifier.height(Dimension.D400))

        ButtonGhost(
            onClick = onCreateAccount,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Don't have an account? Create one")
        }
    }
}

@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    onAction: (ForgotPasswordAction) -> Unit,
    onBack: () -> Unit,
    onBackToSignIn: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val submit = {
        keyboardController?.hide()
        onAction(ForgotPasswordAction.Submit)
    }
    AuthShell(onBack = onBack) {
        Text(
            text = if (state.sent) "Check your inbox" else "Forgot your password?",
            typography = AppTheme.typography.Heading.H700,
        )
        Spacer(modifier = Modifier.height(Dimension.D300))
        Text(
            text = if (state.sent) {
                "We sent a password reset link to ${state.email.trim()}."
            } else {
                "Enter your email and we'll send you a reset link."
            },
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        if (!state.sent) {
            Spacer(modifier = Modifier.height(Dimension.D700))
            EmailField(
                value = state.email,
                enabled = !state.isSubmitting,
                onChange = { onAction(ForgotPasswordAction.EmailChanged(it)) },
                imeAction = ImeAction.Go,
                onSubmitImeAction = { submit() },
            )
        }

        state.banner?.let { banner ->
            Spacer(modifier = Modifier.height(Dimension.D500))
            ErrorText(
                when (banner) {
                    ForgotPasswordState.Banner.RateLimited ->
                        "Too many requests — give it a minute and try again."
                    ForgotPasswordState.Banner.NetworkError ->
                        "Couldn't reach the server. Check your connection and try again."
                    ForgotPasswordState.Banner.GenericError ->
                        "Something went wrong. Please try again."
                },
            )
        }

        Spacer(modifier = Modifier.height(Dimension.D800))

        if (state.sent) {
            ButtonPrimary(
                onClick = onBackToSignIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back to sign in")
            }
        } else {
            ButtonPrimary(
                onClick = { submit() },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "Sending…" else "Send reset link")
            }
        }
    }
}

@Composable
fun SignUpScreen(
    state: SignUpState,
    onAction: (SignUpAction) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val submit = {
        keyboardController?.hide()
        onAction(SignUpAction.Submit)
    }
    AuthShell(onBack = onBack) {
        Text(
            text = "Create your account",
            typography = AppTheme.typography.Heading.H700,
        )
        Spacer(modifier = Modifier.height(Dimension.D300))
        Text(
            text = "Save your progress and sign in from any device.",
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(Dimension.D700))

        EmailField(
            value = state.email,
            enabled = !state.isSubmitting,
            onChange = { onAction(SignUpAction.EmailChanged(it)) },
        )
        Spacer(modifier = Modifier.height(Dimension.D500))
        PasswordField(
            value = state.password,
            onValueChange = { onAction(SignUpAction.PasswordChanged(it)) },
            label = "Password",
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Next,
            helper = "At least ${SignUpState.MIN_PASSWORD_LENGTH} characters",
        )
        Spacer(modifier = Modifier.height(Dimension.D500))
        PasswordField(
            value = state.confirmPassword,
            onValueChange = { onAction(SignUpAction.ConfirmPasswordChanged(it)) },
            label = "Confirm password",
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Go,
            onImeAction = { submit() },
            helper = if (state.passwordMismatch) "Passwords don't match" else null,
            isError = state.passwordMismatch,
        )

        state.error?.let {
            Spacer(modifier = Modifier.height(Dimension.D400))
            ErrorText(it.message())
        }

        Spacer(modifier = Modifier.height(Dimension.D800))

        ButtonPrimary(
            onClick = { submit() },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSubmitting) "Creating account…" else "Create account")
        }

        Spacer(modifier = Modifier.height(Dimension.D400))

        ButtonGhost(
            onClick = onSignIn,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable
fun VerifyEmailScreen(
    state: VerifyEmailState,
    onAction: (VerifyEmailAction) -> Unit,
    onBack: () -> Unit,
) {
    LifecycleResumeEffect(Unit) {
        onAction(VerifyEmailAction.AppResumed)
        onPauseOrDispose { }
    }
    AuthShell(onBack = onBack) {
        Text(
            text = "Verify your email",
            typography = AppTheme.typography.Heading.H700,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(Dimension.D400))

        Text(
            text = if (state.email.isEmpty()) {
                "We sent you a verification link. Tap it, then come back here."
            } else {
                "We sent a verification link to ${state.email}. Tap it, then come back here."
            },
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Dimension.D800))

        state.banner?.let { banner ->
            VerifyEmailBanner(banner)
            Spacer(modifier = Modifier.height(Dimension.D400))
        }

        ButtonPrimary(
            onClick = { onAction(VerifyEmailAction.IClickedTheLink) },
            enabled = !state.isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isRefreshing) "Checking…" else "I clicked the link")
        }

        Spacer(modifier = Modifier.height(Dimension.D400))

        ButtonGhost(
            onClick = { onAction(VerifyEmailAction.Resend) },
            enabled = !state.isResending && !state.isRefreshing && state.email.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isResending) "Resending…" else "Resend email")
        }
    }
}

// ---- Field helpers ----

@Composable
private fun EmailField(
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    onSubmitImeAction: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onGo = { onSubmitImeAction?.invoke() },
            onNext = { onSubmitImeAction?.invoke() },
        ),
        label = { Text("Email") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)? = null,
    helper: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onGo = { onImeAction?.invoke() },
            onNext = { onImeAction?.invoke() },
        ),
        label = { Text(label) },
        supportingText = helper?.let {
            {
                Text(
                    text = it,
                    typography = AppTheme.typography.Body.B400,
                    color = if (isError) AppTheme.colors.danger else AppTheme.colors.textSecondary,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(
        text = text,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.danger,
    )
}

@Composable
private fun VerifyEmailBanner(banner: VerifyEmailState.Banner) {
    val (text, color) = when (banner) {
        VerifyEmailState.Banner.StillPending ->
            "Not confirmed yet — check your inbox and tap the link." to AppTheme.colors.textSecondary
        VerifyEmailState.Banner.ResendSent ->
            "Verification email sent." to AppTheme.colors.textSecondary
        VerifyEmailState.Banner.ResendRateLimited ->
            "Too many requests — give it a minute and try again." to AppTheme.colors.danger
        VerifyEmailState.Banner.NetworkError ->
            "Couldn't reach the server. Check your connection and try again." to AppTheme.colors.danger
        VerifyEmailState.Banner.GenericError ->
            "Something went wrong. Please try again." to AppTheme.colors.danger
    }
    Text(
        text = text,
        typography = AppTheme.typography.Body.B500,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun SignInError.message(): String = when (this) {
    SignInError.InvalidCredentials -> "That email and password don't match. Try again."
    SignInError.NetworkError -> "Couldn't reach the server. Check your connection and try again."
    SignInError.ProviderNotEnabled -> "That sign-in method isn't available right now."
    SignInError.Unknown -> "Something went wrong. Please try again."
}

private fun SignUpError.message(): String = when (this) {
    SignUpError.EmailAlreadyRegistered -> "That email is already registered. Try signing in instead."
    is SignUpError.WeakPassword -> "Password must be at least $minLength characters."
    SignUpError.InvalidEmail -> "That doesn't look like a valid email address."
    SignUpError.NetworkError -> "Couldn't reach the server. Check your connection and try again."
    SignUpError.Timeout -> "That's taking longer than usual. Please try again."
    SignUpError.Unknown -> "Something went wrong. Please try again."
}

@Preview
@Composable
private fun SignInScreenPreview() {
    PreviewContent {
        SignInScreen(
            state = SignInState(email = "person@example.com", password = "••••••••"),
            onAction = {},
            onBack = {},
            onCreateAccount = {},
            onForgotPassword = {},
        )
    }
}

@Preview
@Composable
private fun SignUpScreenPreview_PasswordMismatch() {
    PreviewContent {
        SignUpScreen(
            state = SignUpState(
                email = "person@example.com",
                password = "hunter22ish",
                confirmPassword = "hunter22is",
            ),
            onAction = {},
            onBack = {},
            onSignIn = {},
        )
    }
}

@Preview
@Composable
private fun ForgotPasswordScreenPreview_Sent() {
    PreviewContent {
        ForgotPasswordScreen(
            state = ForgotPasswordState(email = "person@example.com", sent = true),
            onAction = {},
            onBack = {},
            onBackToSignIn = {},
        )
    }
}

@Preview
@Composable
private fun VerifyEmailScreenPreview() {
    PreviewContent {
        VerifyEmailScreen(
            state = VerifyEmailState(
                email = "person@example.com",
                banner = VerifyEmailState.Banner.StillPending,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
