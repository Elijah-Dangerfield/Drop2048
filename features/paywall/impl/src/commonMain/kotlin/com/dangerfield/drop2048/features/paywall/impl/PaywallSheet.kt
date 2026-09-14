package com.dangerfield.drop2048.features.paywall.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.dialog.bottomsheet.BottomSheet
import com.dangerfield.drop2048.libraries.ui.components.dialog.bottomsheet.BottomSheetState
import com.dangerfield.drop2048.libraries.ui.components.dialog.bottomsheet.rememberBottomSheetState
import com.dangerfield.drop2048.libraries.ui.components.game.GamePanel
import com.dangerfield.drop2048.libraries.ui.components.game.GamePrimaryButton
import com.dangerfield.drop2048.libraries.ui.components.game.GameQuietButton
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.libraries.ui.system.deepFace
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.VerticalSpacerD200
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD800
import com.dangerfield.drop2048.system.typography.FredokaFontFamily
import com.dangerfield.drop2048.system.typography.NunitoFontFamily
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
 * SPEC 12's Pro offer, as a bottom sheet over whatever the player was doing.
 *
 * ### Why a sheet and not a screen (C14)
 *
 * It was a pushed `screen<>` until the owner asked for this directly, and the
 * ask is right for a reason the backstack makes concrete: the two things that
 * open it — the Settings row and the card on the stacked-out sheet — are both
 * places the player expects to come straight back to. A sheet stays one entry
 * deep, leaves the screen underneath visible through the scrim, and dismisses on
 * a drag, a back press or a tap outside without any of those being a navigation
 * the app has to reason about.
 *
 * It also removes the last thing on this surface that read as a settings page: a
 * title bar with a back chevron. There is nothing to go back *to*; there is only
 * something to put down.
 *
 * @param onDismissRequest run once the sheet has finished animating out. This is
 *   where the route is popped, which is why it is the caller's rather than the
 *   view model's — the sheet has to be gone before the entry leaves the stack or
 *   the exit animation is cut off mid-slide.
 */
@Composable
fun PaywallSheet(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: BottomSheetState = rememberBottomSheetState(),
) {
    BottomSheet(
        onDismissRequest = onDismissRequest,
        state = sheetState,
        showDragHandle = true,
        backgroundColor = AppTheme.colors.background,
        modifier = modifier,
    ) {
        PaywallSheetContent(state = state, onAction = onAction)
    }
}

/**
 * The sheet's contents, with no sheet around them.
 *
 * Split out because Material's modal sheet draws into its own window, which a
 * screenshot harness capturing a tagged node in the composition cannot see. The
 * goldens capture this; the wrapper above is what ships.
 *
 * ### What it sells is exactly what v1 has
 *
 * SPEC 2 cut coins and the five powerups, which makes Pro thinner than the
 * original spec's — no interstitials, every palette, two Daily attempts, two
 * continues — and the honest response is to describe those four plainly rather
 * than to dress four things up as eight. There are no coins, no powerups, no Zen
 * mode and no board variants to promise, and nothing on this sheet promises
 * them. There is also no countdown, no crossed-out price, no "most popular"
 * badge and no second tier to make this one look cheaper. There is one product.
 *
 * **Rewarded video is named as something Pro keeps.** It is the one item on the
 * list a player could reasonably read backwards: "no ads" that also took away the
 * rewarded continue would make Pro worse at the moment it matters most (SPEC 12).
 *
 * A pure render of [PaywallState]. The price is whatever the store said, or
 * absent — see `PaywallViewModel`.
 */
@Composable
fun PaywallSheetContent(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimension.D800, vertical = Dimension.D500),
    ) {
        BasicText(
            text = stringResource(Res.string.paywall_title),
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = TitleSize,
                color = GameColors.Ink,
                textAlign = TextAlign.Center,
            ),
        )
        VerticalSpacerD200()
        BasicText(
            text = stringResource(Res.string.paywall_subtitle),
            style = TextStyle(
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = SubtitleSize,
                color = GameColors.InkMuted,
                textAlign = TextAlign.Center,
            ),
        )

        VerticalSpacerD800()
        Perks()
        VerticalSpacerD800()

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
            BasicText(
                text = stringResource(Res.string.paywall_owned),
                style = TextStyle(
                    fontFamily = FredokaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = OwnedSize,
                    color = GameColors.AccentYellow,
                    textAlign = TextAlign.Center,
                ),
            )
        } else {
            Buy(state = state, onAction = onAction)
        }
        VerticalSpacerD500()
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
 *
 * It is the game's own primary button — the accent yellow on its hard shadow,
 * Fredoka, pressing four pixels down — because this is the same act as `Drop
 * again`. Drawing the one paid action in the template's flat button was the
 * loudest thing on the old screen that said "different app".
 */
@Composable
private fun Buy(state: PaywallState, onAction: (PaywallAction) -> Unit) {
    GamePrimaryButton(
        label = state.price
            ?.let { stringResource(Res.string.paywall_buy_priced, it) }
            ?: stringResource(Res.string.paywall_buy),
        onClick = { onAction(PaywallAction.Buy) },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    )
    VerticalSpacerD500()
    GameQuietButton(
        text = stringResource(Res.string.paywall_restore),
        onClick = { if (!state.busy) onAction(PaywallAction.Restore) },
    )
}

@Composable
private fun Perks() {
    GamePanel(spacing = Dimension.D600) {
        Perk(
            title = stringResource(Res.string.paywall_perk_ads),
            detail = stringResource(Res.string.paywall_perk_ads_detail),
        )
        Perk(
            title = stringResource(Res.string.paywall_perk_palettes),
            detail = stringResource(Res.string.paywall_perk_palettes_detail),
        )
        Perk(
            title = stringResource(Res.string.paywall_perk_daily),
            detail = stringResource(Res.string.paywall_perk_daily_detail),
        )
        Perk(
            title = stringResource(Res.string.paywall_perk_continues),
            detail = stringResource(Res.string.paywall_perk_continues_detail),
        )
    }
}

/**
 * One perk, marked by a chunky accent chip rather than a bullet or a check icon.
 *
 * Geometry rather than a glyph, for the reason `SpecialTile` gives: a check
 * character is a bet that every font on every target carries that codepoint, and
 * losing it puts a tofu box down the side of the one screen that takes money. A
 * rounded square at the tile's own 22% radius cannot fail to render, and it says
 * "block" to anyone who has been looking at the board.
 */
@Composable
private fun Perk(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = ChipTopAlign)
                .size(ChipSize)
                .deepFace(
                    color = GameColors.AccentYellow,
                    shape = Radii.Tile,
                    depth = ChipDepth,
                    shadow = GameColors.AccentYellowShadow,
                    highlight = GameColors.AccentHighlight,
                ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimension.D100),
            modifier = Modifier.padding(start = Dimension.D500),
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    fontFamily = FredokaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = PerkTitleSize,
                    color = GameColors.Ink,
                ),
            )
            BasicText(
                text = detail,
                style = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = PerkDetailSize,
                    color = GameColors.InkMuted,
                ),
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

private val TitleSize = 26.sp
private val SubtitleSize = 14.sp
private val OwnedSize = 18.sp
private val PerkTitleSize = 16.sp
private val PerkDetailSize = 13.sp

private val ChipSize: Dp = 14.dp
private val ChipDepth: Dp = 2.dp
private val ChipTopAlign: Dp = 4.dp

@Preview
@Composable
private fun PaywallSheetPreview() {
    PreviewContent {
        PaywallSheetContent(
            state = PaywallState(price = "$2.99"),
            onAction = {},
        )
    }
}
