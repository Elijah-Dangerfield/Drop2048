package com.dangerfield.drop2048.libraries.ads.impl

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.AdUnits
import com.dangerfield.drop2048.libraries.ads.BannerSurface
import com.dangerfield.drop2048.libraries.ads.NoBannerSurface
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope

/**
 * An AdMob adaptive banner, drawn where the arrow row would be.
 *
 * ## It measures zero until there is something to measure
 *
 * The `AdView` is only composed once the SDK has reported a load, and that is
 * the entire mechanism behind D28's "if the banner fails to load, the space goes
 * back to the board". There is no reserved height, no placeholder, no `Spacer`
 * sized to `AdSize`: before a fill this composable emits nothing, so the board
 * above it, which has `weight(1f)`, takes the strip without being told to.
 *
 * The same is true in the other direction. `onAdFailedToLoad` clears the flag,
 * the view is removed, and the layout is byte-for-byte the layout of a build
 * with no banner at all. `BoardTakesTheStripTest` pins that.
 *
 * ## The one layout shift, and why it is at the front of the run
 *
 * SPEC 12.4's objection to banners was that one which shifts layout mid-run
 * reads as the game cheating, and that objection survives D28 intact. What
 * changed is the answer to it. The load is started when the game screen first
 * composes, which is while the start overlay is up and before the player has
 * dropped anything, so the single expansion happens on a board nobody is
 * playing. A banner that fails at first and fills later would move the board
 * mid-run; AdMob's own refresh replaces the creative inside a view that is
 * already the right size, so it does not.
 *
 * ## [prepare] before the request, always
 *
 * Same policy requirement as every other format: UMP first, then the SDK, then
 * the request. It is awaited off the composition on [AppCoroutineScope], because
 * a `LaunchedEffect` tied to this composable would cancel the consent flow if
 * the player rotated the phone while the form was up.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = BannerSurface::class,
    replaces = [NoBannerSurface::class],
)
@Inject
class AdMobBannerSurface(
    private val context: Context,
    private val network: AdNetwork,
    private val appScope: AppCoroutineScope,
) : BannerSurface {

    private val logger = KLog.withTag("Banner")

    private val prepared = MutableStateFlow(false)

    @Composable
    override fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier) {
        val report by rememberUpdatedState(onFilled)
        val inspecting = LocalInspectionMode.current
        val widthDp = LocalConfiguration.current.screenWidthDp
        var filled by remember { mutableStateOf(false) }

        DisposableEffect(inspecting) {
            if (!inspecting) appScope.launch { network.prepare(); prepared.value = true }
            onDispose { report(false) }
        }

        if (inspecting) return

        val view = remember(widthDp) {
            AdView(context).apply {
                adUnitId = AdUnits.android(AdFormat.Banner)
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        filled = true
                        report(true)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        logger.logEvent("ads.banner_failed", "code" to error.code)
                        filled = false
                        report(false)
                    }
                }
            }
        }

        DisposableEffect(view) {
            val job = appScope.launch {
                prepared.collect { ready -> if (ready) view.loadAd(AdRequest.Builder().build()) }
            }
            onDispose {
                job.cancel()
                view.destroy()
            }
        }

        // Emitted only once there is an ad in it. An `AdView` with nothing
        // loaded still measures its declared `AdSize`, which would reserve the
        // strip for an ad that may never come: the exact dead space D28
        // refuses.
        if (filled) {
            AndroidView(factory = { view }, modifier = modifier)
        }
    }
}
