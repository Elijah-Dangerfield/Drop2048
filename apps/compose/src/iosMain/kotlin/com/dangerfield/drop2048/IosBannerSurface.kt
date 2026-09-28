package com.dangerfield.drop2048

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.interop.UIKitView
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.BannerSurface
import com.dangerfield.drop2048.libraries.ads.IosBannerViewFactory
import com.dangerfield.drop2048.libraries.ads.NoBannerSurface
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.DispatcherProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import platform.UIKit.UIScreen
import platform.UIKit.UIView

/**
 * The iOS banner, drawn in place.
 *
 * The counterpart of `AdMobBannerSurface` on Android, and it keeps that file's
 * two rules because both are contracts rather than implementation details.
 *
 * ### Zero height until an ad is really there
 *
 * [BannerSurface] requires the strip to collapse for no fill, a network error,
 * ads off, Pro, or a platform with no SDK. The view is therefore composed only
 * once Swift has reported a fill. A `BannerView` with nothing in it still
 * measures its declared ad size, so putting it in the layout early would
 * reserve a strip for an ad that may never come, which is the dead space the
 * whole design refuses. The caller puts this after a board with `weight(1f)`,
 * so a zero-height banner *is* the board taking the space back.
 *
 * ### [AdNetwork.prepare] before the request, always
 *
 * UMP consent, then ATT, then `MobileAds.start()`, then the first request. It
 * is awaited on [AppCoroutineScope] rather than in a `LaunchedEffect` so that
 * rotating the phone while the consent form is up cannot cancel it. The view is
 * not built until that returns, which is the ordering guarantee: on iOS the
 * request starts inside `makeBanner`, so building late is how the request stays
 * behind consent.
 *
 * The hop to [DispatcherProvider.main] is load-bearing rather than tidiness.
 * `AppCoroutineScope` runs on `DispatcherProvider.default`, and UIKit view
 * construction off the main thread is undefined behaviour rather than an
 * exception, which is the worse of the two failures. Android has the same hop
 * for the same reason, learned from a crash that took the process down twenty
 * times in half an hour.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = BannerSurface::class,
    replaces = [NoBannerSurface::class],
)
@Inject
class IosBannerSurface(
    private val factory: IosBannerViewFactory,
    private val network: AdNetwork,
    private val appScope: AppCoroutineScope,
    private val dispatchers: DispatcherProvider,
) : BannerSurface {

    @OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
    @Composable
    override fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier) {
        val report by rememberUpdatedState(onFilled)

        // Points, not pixels: the SDK's adaptive sizing is in points and so is
        // UIKit's coordinate space, so no scale conversion belongs here.
        val width = remember { UIScreen.mainScreen.bounds.useContents { size.width } }

        var view by remember { mutableStateOf<UIView?>(null) }
        var filled by remember { mutableStateOf(false) }

        DisposableEffect(width) {
            val job = appScope.launch {
                network.prepare()
                withContext(dispatchers.main) {
                    view = factory.makeBanner(width) { didFill ->
                        filled = didFill
                        report(didFill)
                    }
                }
            }
            onDispose {
                job.cancel()
                // Report the strip gone before releasing the view, so the
                // caller's "ads seen" record and the layout agree on the way
                // out as well as the way in.
                report(false)
                filled = false
                view?.let(factory::disposeBanner)
                view = null
            }
        }

        val current = view
        if (filled && current != null) {
            UIKitView(factory = { current }, modifier = modifier)
        }
    }
}
