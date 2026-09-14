package com.dangerfield.drop2048.libraries.ads.fake

import androidx.compose.runtime.Composable
import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.AdShowOutcome
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ads.HouseAds
import com.dangerfield.drop2048.libraries.ads.NoHouseAds
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.logging.KLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * One house ad on screen: the format it is standing in for, and how long it runs.
 *
 * [id] exists so the surface's countdown restarts on a second ad of the same
 * format. Keying an effect on the format alone would leave the previous ad's
 * remaining seconds in place, which is the sort of thing only a tester at 3am
 * would ever notice and would then not believe.
 */
data class HouseAdShowing(
    val id: Long,
    val format: AdFormat,
    val totalSeconds: Int,
)

/**
 * An ad network that always fills, because it draws the ad itself.
 *
 * ## Why this exists
 *
 * Before it, nobody had seen this app's interstitial — not the owner, not the
 * agent that wrote it. SPEC 12.3 stacks a three-day install suppression, a
 * fourth-run minimum and a 180-second cooldown in front of it, and the only
 * way past the first was a `date` an emulator refuses on a non-userdebug build.
 * The rewarded path fared better only because AdMob's test units happen to fill,
 * which makes it a test of Play Services and a network rather than of this app.
 *
 * A house network removes every one of those variables. It always fills, it
 * never needs consent, it works on a plane, and what it draws says on its face
 * that it is not an advertisement.
 *
 * ## What makes it safe
 *
 * This class can answer [AdShowResult.Rewarded], which is precisely the thing
 * L66 says a stand-in must not be able to do — `NotWiredAdNetwork` was safe
 * *because its type could not express a grant*. That defence is unavailable
 * here, so the safety is structural in the build instead:
 *
 * 1. The module is a `debugImplementation` of `:apps:compose`, so no release
 *    Android artifact contains this class, the binding, or the drawing code.
 *    `:apps:compose:verifyNoHouseAdsInRelease` fails the build if that stops
 *    being true.
 * 2. iOS has no build-type source sets, so on that platform the class is linked
 *    in and [select] refuses instead — `BuildInfo.isDebug` is
 *    `Platform.isDebugBinary`, set by the Xcode configuration and not reachable
 *    from app code or config.
 *
 * Neither layer is a flag anybody has to remember.
 *
 * ## Every outcome, not just the happy one
 *
 * [HouseAds.forcedOutcome] short-circuits the draw and answers a chosen
 * [AdShowResult] directly, because the interesting half of the ad layer is what
 * it does when an ad does *not* play: a rewarded continue granted anyway on a
 * `NoFill` (`RealAdGate`), an interstitial that reports nothing shown on a
 * `NotShown`, a `Failed` carrying its kind into `ads.rewarded_result`. Closing
 * a rewarded placeholder early is the one outcome that has to be produced by
 * hand, and it is the one that withholds.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = HouseAds::class,
    replaces = [NoHouseAds::class],
)
@Inject
class HouseAdNetwork : AdNetwork, HouseAds {

    private val logger = KLog.withTag("HouseAds")

    private val _showing = MutableStateFlow<HouseAdShowing?>(null)

    /** The ad currently asking to be drawn. Read by [Surface] and by nothing else. */
    val showing: StateFlow<HouseAdShowing?> = _showing.asStateFlow()

    private var pending: CompletableDeferred<AdShowOutcome>? = null
    private var nextId: Long = 0

    /**
     * Selected by default on a debug build, because the whole point is that the
     * plumbing is visible without anyone configuring it first. QA switches to
     * the platform network when they want to check AdMob specifically.
     */
    private val _isSelected = MutableStateFlow(BuildInfo.isDebug)
    override val isSelected: StateFlow<Boolean> = _isSelected.asStateFlow()

    private val _forcedOutcome = MutableStateFlow<AdShowResult?>(null)
    override val forcedOutcome: StateFlow<AdShowResult?> = _forcedOutcome.asStateFlow()

    private val _reportsReady = MutableStateFlow(true)
    override val reportsReady: StateFlow<Boolean> = _reportsReady.asStateFlow()

    override val network: AdNetwork get() = this

    /**
     * The second layer, and the one iOS relies on.
     *
     * `&&` rather than an early return so that a release binary which somehow
     * contains this class still cannot be talked into selecting it, including by
     * a future caller that forgets to check.
     */
    override fun select(useHouseAds: Boolean) {
        _isSelected.value = useHouseAds && BuildInfo.isDebug
    }

    override fun forceOutcome(result: AdShowResult?) {
        _forcedOutcome.value = result
    }

    override fun reportReady(ready: Boolean) {
        _reportsReady.value = ready
    }

    override suspend fun prepare() = Unit

    override fun isReady(format: AdFormat): Boolean = _reportsReady.value

    override fun preload(format: AdFormat) = Unit

    /**
     * Suspends until the placeholder is closed, exactly as a real full-screen ad
     * does.
     *
     * That it suspends is not cosmetic. `RewardedClock` is held across this call
     * and it is the forward half of SPEC 12's 45-second rule; an implementation
     * that returned immediately would leave the one piece of ad timing that
     * cannot be tested with a clock — the window while an ad is actually on
     * screen — as unexercised as it was before.
     */
    override suspend fun show(format: AdFormat): AdShowOutcome {
        _forcedOutcome.value?.let { forced ->
            logger.i { "House ad for $format forced to $forced" }
            return AdShowOutcome(forced, errorKind = ForcedErrorKind)
        }

        val deferred = CompletableDeferred<AdShowOutcome>()
        pending = deferred
        nextId++
        _showing.value = HouseAdShowing(
            id = nextId,
            format = format,
            totalSeconds = when (format) {
                AdFormat.Rewarded -> RewardedSeconds
                AdFormat.Interstitial -> InterstitialSeconds
            },
        )
        return try {
            deferred.await()
        } finally {
            _showing.value = null
            pending = null
        }
    }

    /**
     * Ends the ad on screen with [result].
     *
     * Internal to the module: the only callers are [Surface]'s close button and
     * its countdown. Nothing outside can resolve an ad that is not being drawn,
     * which keeps "the network answered" and "something was on screen" the same
     * fact.
     */
    internal fun finish(result: AdShowResult) {
        val deferred = pending ?: return
        logger.i { "House ad finished: $result" }
        deferred.complete(AdShowOutcome(result, errorKind = null))
    }

    @Composable
    override fun Surface() {
        HouseAdHost(this)
    }

    internal companion object {
        /** Matches the 8-second continue countdown the stacked-out sheet already uses. */
        const val RewardedSeconds = 8

        const val InterstitialSeconds = 5

        /** Named so a forced failure is distinguishable from a real one in telemetry. */
        const val ForcedErrorKind = "house_forced"
    }
}
