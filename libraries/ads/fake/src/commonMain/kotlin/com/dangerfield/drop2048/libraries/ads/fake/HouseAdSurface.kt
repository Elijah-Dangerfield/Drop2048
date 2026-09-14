package com.dangerfield.drop2048.libraries.ads.fake

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSecondary
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD800
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The house ad, on screen, above everything.
 *
 * Stateful half: it owns the countdown and resolves the network's suspended
 * `show` when the ad ends. Everything that decides what is *drawn* is in
 * [HouseAdSurface], which is a pure function of four values and is therefore the
 * thing the goldens hold.
 *
 * The countdown is the repo's rule 8 and L39's capture hazard in one place. A
 * ticking value is exactly where an animation read gets written into a
 * composition by accident, and a pending `delay` loop is what stops Compose
 * going idle — a screenshot of this would hang rather than fail. Under
 * [LocalInspectionMode] nothing ticks and the number stays whatever it was
 * seeded with, which is also why the goldens capture [HouseAdSurface] directly.
 *
 * Running out is [AdShowResult.Rewarded] for a rewarded ad and
 * [AdShowResult.Dismissed] for an interstitial, which has no reward to
 * withhold — the same event AdMob reports as
 * `onAdDismissedFullScreenContent`.
 */
@Composable
internal fun HouseAdHost(network: HouseAdNetwork) {
    val showing by network.showing.collectAsState()
    val ad = showing ?: return

    var remaining by remember(ad.id) { mutableStateOf(ad.totalSeconds) }

    val inspecting = LocalInspectionMode.current
    LaunchedEffect(ad.id, inspecting) {
        if (inspecting) return@LaunchedEffect
        while (remaining > 0) {
            delay(OneSecond)
            remaining--
        }
        network.finish(
            when (ad.format) {
                AdFormat.Rewarded -> AdShowResult.Rewarded
                AdFormat.Interstitial -> AdShowResult.Dismissed
            }
        )
    }

    HouseAdSurface(
        format = ad.format,
        secondsRemaining = remaining,
        onClose = { network.finish(AdShowResult.Dismissed) },
        onSkipToEnd = {
            network.finish(
                when (ad.format) {
                    AdFormat.Rewarded -> AdShowResult.Rewarded
                    AdFormat.Interstitial -> AdShowResult.Dismissed
                }
            )
        },
    )
}

/**
 * What a house ad looks like, and the two things it has to say.
 *
 * **That it is not an advertisement**, in the largest type on the screen. A
 * placeholder mistaken for a real ad in a bug report, a screen recording or a
 * store screenshot costs more than the placeholder is worth, and a tester who
 * has to work out which network served something has learned nothing.
 *
 * **Which placement asked for it.** The two formats reach the SDK through
 * completely different policy — one the player chose, one they did not — and the
 * failure this whole chunk exists to make visible is an interstitial appearing
 * where a rewarded ad was expected, or at a moment SPEC 12 forbids. A surface
 * that looked identical either way could not show that.
 *
 * Stateless on purpose: a countdown is the classic place to read an animating
 * value during composition, and the cheapest defence is for the composable that
 * draws it to have no clock at all.
 */
@Composable
internal fun HouseAdSurface(
    format: AdFormat,
    secondsRemaining: Int,
    onClose: () -> Unit,
    onSkipToEnd: () -> Unit,
) {
    val rewarded = format == AdFormat.Rewarded
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surfaceTertiary.color),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(EdgePadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = HouseAdCopy.Banner,
                typography = AppTheme.typography.Heading.H900,
                color = AppTheme.colors.accentPrimary,
                textAlign = TextAlign.Center,
            )
            VerticalSpacerD500()
            Text(
                text = HouseAdCopy.placement(format),
                typography = AppTheme.typography.Heading.H500,
                color = AppTheme.colors.text,
                textAlign = TextAlign.Center,
            )
            VerticalSpacerD1000()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = CountdownBorder,
                        color = AppTheme.colors.border.color,
                        shape = RoundedCornerShape(CountdownCorner),
                    )
                    .padding(CountdownPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = secondsRemaining.toString(),
                    typography = AppTheme.typography.Display.D1000,
                    color = AppTheme.colors.text,
                )
            }
            VerticalSpacerD800()
            Text(
                text = if (rewarded) HouseAdCopy.RewardedBody else HouseAdCopy.InterstitialBody,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            VerticalSpacerD1000()
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
                ButtonSecondary(onClick = onClose, size = ButtonSize.Small) {
                    Text(if (rewarded) HouseAdCopy.CloseRewarded else HouseAdCopy.CloseInterstitial)
                }
                ButtonPrimary(onClick = onSkipToEnd, size = ButtonSize.Small) {
                    Text(if (rewarded) HouseAdCopy.FinishRewarded else HouseAdCopy.FinishInterstitial)
                }
            }
        }
    }
}

/**
 * Debug-only copy, as plain constants.
 *
 * Same ruling as `DebugCopy`: this is unreachable from any build a player can
 * install, so there is nothing here to translate and the repo's string baseline
 * stays where it is. `VerifyStrings` fails on a literal handed straight to
 * `Text(...)`; these are named values.
 */
internal object HouseAdCopy {
    const val Banner = "NOT A REAL AD"
    const val RewardedBody =
        "House ad. Let it run out to earn the reward, or close it early to test the path " +
            "where nothing is granted."
    const val InterstitialBody =
        "House ad. This is the one the player did not ask for. It should only ever appear " +
            "after a stacked-out results sheet was dismissed."
    const val CloseRewarded = "Close early"
    const val CloseInterstitial = "Close"
    const val FinishRewarded = "Finish and reward"
    const val FinishInterstitial = "Skip to end"

    fun placement(format: AdFormat): String = when (format) {
        AdFormat.Rewarded -> "Rewarded video"
        AdFormat.Interstitial -> "Interstitial"
    }
}

private val EdgePadding = 24.dp
private val CountdownPadding = 20.dp
private val CountdownBorder = 2.dp
private val CountdownCorner = 16.dp
private val ButtonGap = 8.dp
private const val OneSecond = 1_000L

@Preview
@Composable
private fun HouseAdRewardedPreview() {
    PreviewContent {
        HouseAdSurface(
            format = AdFormat.Rewarded,
            secondsRemaining = 8,
            onClose = {},
            onSkipToEnd = {},
        )
    }
}

@Preview
@Composable
private fun HouseAdInterstitialPreview() {
    PreviewContent {
        HouseAdSurface(
            format = AdFormat.Interstitial,
            secondsRemaining = 5,
            onClose = {},
            onSkipToEnd = {},
        )
    }
}
