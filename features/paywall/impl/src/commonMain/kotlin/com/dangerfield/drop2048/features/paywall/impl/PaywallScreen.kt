package com.dangerfield.drop2048.features.paywall.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.Button
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonStyle
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.HorizontalSpacerD400
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import com.dangerfield.drop2048.system.VerticalSpacerD200
import com.dangerfield.drop2048.system.VerticalSpacerD500
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.paywall_buy
import drop2048.libraries.resources.generated.resources.paywall_buy_priced
import drop2048.libraries.resources.generated.resources.paywall_failed
import drop2048.libraries.resources.generated.resources.paywall_nothing_to_restore
import drop2048.libraries.resources.generated.resources.paywall_owned
import drop2048.libraries.resources.generated.resources.paywall_perk_ads
import drop2048.libraries.resources.generated.resources.paywall_perk_ads_detail
import drop2048.libraries.resources.generated.resources.paywall_perk_continues
import drop2048.libraries.resources.generated.resources.paywall_perk_continues_detail
import drop2048.libraries.resources.generated.resources.paywall_perk_daily
import drop2048.libraries.resources.generated.resources.paywall_perk_daily_detail
import drop2048.libraries.resources.generated.resources.paywall_perk_palettes
import drop2048.libraries.resources.generated.resources.paywall_perk_palettes_detail
import drop2048.libraries.resources.generated.resources.paywall_restore
import drop2048.libraries.resources.generated.resources.paywall_subtitle
import drop2048.libraries.resources.generated.resources.paywall_title
import drop2048.libraries.resources.generated.resources.paywall_unavailable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The one screen in the app that sells anything, and it is a list of four
 * sentences.
 *
 * ### It says what Pro is, not what the player is missing
 *
 * SPEC 2 cut coins and the five powerups, which makes Pro thinner than the
 * original spec's — no ads, every palette, two Daily attempts, two continues —
 * and the honest response is to price it at $2.99 and describe it plainly rather
 * than to dress four things up as eight. There is no countdown, no crossed-out
 * price, no "most popular" badge and no second tier to make this one look
 * cheaper. There is one product.
 *
 * ### Rewarded video is named as something Pro *keeps*
 *
 * The ads perk says so out loud, because it is the one thing on this list a
 * player could reasonably read backwards: "no ads" that also took away the
 * rewarded continue would make Pro worse at the moment it matters most (SPEC 12).
 *
 * A pure render of [PaywallState]. The price is whatever the store said, or
 * absent — see `PaywallViewModel`.
 */
@Composable
fun PaywallScreen(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.paywall_title),
                onNavigateBack = { onAction(PaywallAction.Close) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(paddingValues = padding),
        ) {
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.paywall_subtitle),
                typography = AppTheme.typography.Body.B600,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            VerticalSpacerD1000()
            Perks()
            VerticalSpacerD1000()

            state.message?.let { message ->
                Text(
                    text = messageText(message),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                VerticalSpacerD500()
            }

            if (state.isPro) {
                Text(
                    text = stringResource(Res.string.paywall_owned),
                    typography = AppTheme.typography.Heading.H700,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Buy(state = state, onAction = onAction)
            }
            VerticalSpacerD1000()
        }
    }
}

/**
 * The buy button carries the price when there is one and reads "Go Pro" when
 * there is not.
 *
 * A store that could not be reached is a real state on a plane, in a tunnel and
 * on a fresh emulator, and the two wrong answers are both worse than a button
 * with no number: a spinner that never resolves, or a hardcoded price that is
 * wrong everywhere outside one storefront.
 */
@Composable
private fun Buy(state: PaywallState, onAction: (PaywallAction) -> Unit) {
    Button(
        onClick = { onAction(PaywallAction.Buy) },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = state.price
                ?.let { stringResource(Res.string.paywall_buy_priced, it) }
                ?: stringResource(Res.string.paywall_buy),
        )
    }
    VerticalSpacerD200()
    Button(
        onClick = { onAction(PaywallAction.Restore) },
        style = ButtonStyle.Text,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(Res.string.paywall_restore))
    }
}

@Composable
private fun Perks() {
    Column(verticalArrangement = Arrangement.Top, modifier = Modifier.fillMaxWidth()) {
        Perk(
            title = stringResource(Res.string.paywall_perk_ads),
            detail = stringResource(Res.string.paywall_perk_ads_detail),
        )
        VerticalSpacerD500()
        Perk(
            title = stringResource(Res.string.paywall_perk_palettes),
            detail = stringResource(Res.string.paywall_perk_palettes_detail),
        )
        VerticalSpacerD500()
        Perk(
            title = stringResource(Res.string.paywall_perk_daily),
            detail = stringResource(Res.string.paywall_perk_daily_detail),
        )
        VerticalSpacerD500()
        Perk(
            title = stringResource(Res.string.paywall_perk_continues),
            detail = stringResource(Res.string.paywall_perk_continues_detail),
        )
    }
}

@Composable
private fun Perk(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(text = Bullet, typography = AppTheme.typography.Body.B700)
        HorizontalSpacerD400()
        Column {
            Text(text = title, typography = AppTheme.typography.Heading.H600)
            Text(
                text = detail,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun messageText(message: PaywallMessage): String = when (message) {
    PaywallMessage.NothingToRestore -> stringResource(Res.string.paywall_nothing_to_restore)
    PaywallMessage.Unavailable -> stringResource(Res.string.paywall_unavailable)
    PaywallMessage.Failed -> stringResource(Res.string.paywall_failed)
}

/**
 * A bullet rather than a check icon, and it is a glyph rather than a string
 * resource because it is punctuation: it is the same character in every
 * language, and `VerifyStrings` is about copy a translator has to see.
 */
private const val Bullet = "•"

@Preview
@Composable
private fun PaywallScreenPreview() {
    PreviewContent {
        PaywallScreen(
            state = PaywallState(price = "$2.99"),
            onAction = {},
        )
    }
}
