package com.dangerfield.drop2048

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.BannerSurface
import com.dangerfield.drop2048.libraries.ads.IosBannerListener
import com.dangerfield.drop2048.libraries.ads.IosBannerViewFactory
import com.dangerfield.drop2048.libraries.ads.NoBannerSurface
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.DispatcherProvider
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The iOS banner, drawn in place. The counterpart of `AdMobBannerSurface`.
 *
 * ### The size has to be given, not inferred
 *
 * `UIKitView` does **not** adopt the intrinsic size of the view inside it, the
 * way `AndroidView` adopts an `AdView`'s. It takes the constraints it is handed.
 * The first version of this passed the caller's modifier straight through, which
 * carries no height, so a loaded ad measured zero and nothing ever appeared.
 * From the outside that is indistinguishable from a no-fill, which is why the
 * height now comes back from Swift with the fill and is applied here.
 *
 * ### Zero height until an ad is really there
 *
 * [BannerSurface] requires the strip to collapse for no fill, a network error,
 * ads off, Pro, or a platform with no SDK. The view is composed only once Swift
 * reports a fill, because a `BannerView` with nothing in it still measures its
 * declared ad size and would reserve a strip for an ad that may never come. The
 * caller puts this after a board with `weight(1f)`, so a zero-height banner is
 * the board taking the space back.
 *
 * ### [AdNetwork.prepare] before the request, always
 *
 * UMP consent, then ATT, then `MobileAds.start()`, then the first request,
 * awaited on [AppCoroutineScope] so that rotating the phone while the consent
 * form is up cannot cancel it. The request starts inside `makeBanner`, so
 * building the view late is what keeps it behind consent.
 *
 * The hop to [DispatcherProvider.main] is load-bearing. [AppCoroutineScope] runs
 * on `DispatcherProvider.default`, and UIKit construction off the main thread is
 * undefined behaviour rather than an exception, which is the worse of the two.
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

    private val logger = KLog.withTag("Banner")

    @OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
    @Composable
    override fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier) {
        val report by rememberUpdatedState(onFilled)

        // Points, not pixels: the SDK's adaptive sizing is in points and so is
        // UIKit's coordinate space, so no scale conversion belongs here.
        val width = remember { UIScreen.mainScreen.bounds.useContents { size.width } }

        var view by remember { mutableStateOf<UIView?>(null) }
        var height by remember { mutableStateOf(0.0) }

        DisposableEffect(width) {
            val job = appScope.launch {
                network.prepare()
                withContext(dispatchers.main) {
                    val listener = object : IosBannerListener {
                        override fun onFilled(heightPoints: Double) {
                            // A zero height would compose a view nobody can
                            // see, which is the bug this class already had
                            // once. Treat it as a failure rather than a fill.
                            if (heightPoints <= 0.0) {
                                logger.logEvent("ads.banner_failed", "reason" to "zero_height")
                                height = 0.0
                                report(false)
                                return
                            }
                            logger.logEvent("ads.banner_filled", "height" to heightPoints.toInt())
                            height = heightPoints
                            report(true)
                        }

                        override fun onFailed(reason: String) {
                            logger.logEvent("ads.banner_failed", "reason" to reason)
                            height = 0.0
                            report(false)
                        }
                    }

                    val made = factory.makeBanner(width, listener)
                    if (made == null) {
                        // Null means the SDK is not in the binary at all. Worth
                        // its own reason: every other failure is a load that
                        // did not fill, and this one is a build problem.
                        logger.logEvent("ads.banner_failed", "reason" to "no_sdk")
                        report(false)
                    }
                    view = made
                }
            }
            onDispose {
                job.cancel()
                // Report the strip gone before releasing the view, so the
                // caller's "ads seen" record and the layout agree on the way
                // out as well as the way in.
                report(false)
                height = 0.0
                view?.let(factory::disposeBanner)
                view = null
            }
        }

        val current = view
        if (height > 0.0 && current != null) {
            UIKitView(
                factory = { current },
                modifier = modifier.width(width.dp).height(height.dp),
            )
        }
    }
}
