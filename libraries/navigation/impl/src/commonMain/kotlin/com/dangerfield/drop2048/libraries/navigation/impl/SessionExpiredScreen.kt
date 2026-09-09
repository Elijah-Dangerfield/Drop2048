package com.dangerfield.drop2048.libraries.navigation.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.libraries.core.doNothing
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Blocking surface for a server-rejected session. Back is swallowed — the
 * stack beneath is unusable until the user recovers. Claimed accounts get
 * "Sign in again"; anonymous guests get "Start fresh" (their session is
 * unrecoverable, so a new guest account is minted in place).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SessionExpiredScreen(
    wasAnonymous: Boolean,
    working: Boolean,
    startFreshFailed: Boolean,
    onSignInAgain: () -> Unit,
    onStartFresh: () -> Unit,
) {
    BackHandler { doNothing() }

    Screen { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimension.D1000),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = if (wasAnonymous) "Session lost" else "Session expired",
                typography = AppTheme.typography.Display.D1000,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(Dimension.D500))

            Text(
                text = if (wasAnonymous) {
                    "Your guest session can't be recovered. Start fresh to keep using the app."
                } else {
                    "For your security you've been signed out. Sign in again to pick up where you left off."
                },
                typography = AppTheme.typography.Body.B400,
                textAlign = TextAlign.Center,
            )

            if (startFreshFailed) {
                Spacer(modifier = Modifier.height(Dimension.D400))
                Text(
                    text = "Couldn't start a new session. Check your connection and try again.",
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.status.warning,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.weight(2f))

            ButtonPrimary(
                enabled = !working,
                onClick = if (wasAnonymous) onStartFresh else onSignInAgain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = when {
                        working -> "Working…"
                        wasAnonymous -> "Start fresh"
                        else -> "Sign in again"
                    },
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview
@Composable
private fun SessionExpiredScreenPreview_Claimed() {
    PreviewContent {
        SessionExpiredScreen(
            wasAnonymous = false,
            working = false,
            startFreshFailed = false,
            onSignInAgain = {},
            onStartFresh = {},
        )
    }
}

@Preview
@Composable
private fun SessionExpiredScreenPreview_Guest_Failed() {
    PreviewContent {
        SessionExpiredScreen(
            wasAnonymous = true,
            working = false,
            startFreshFailed = true,
            onSignInAgain = {},
            onStartFresh = {},
        )
    }
}
