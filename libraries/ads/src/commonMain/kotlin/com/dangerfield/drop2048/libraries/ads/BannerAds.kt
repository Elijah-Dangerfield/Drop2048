package com.dangerfield.drop2048.libraries.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Whether a banner may be **asked for** at all, and the record that one was
 * seen. The drawing is [BannerSurface]'s; this is the policy half, and it is in
 * common code so both platforms answer it the same way.
 *
 * Three refusals, and the owner's ruling of 2026-09-20 names all three: the
 * master `ads.enabled` kill switch, the banner's own `ads.banner.enabled` key so
 * it can be turned off without a release, and Pro, consistent with Pro removing
 * interstitials.
 *
 * The fourth refusal is not here and deliberately so. **"The player has the
 * arrows switched on" is a layout fact, not an ad policy**, and it is answered
 * by `GameScreen` at the one place that knows: the banner is composed into the
 * slot the arrow row would have used, so a screen with an arrow row has no slot
 * to put it in. Encoding it here would mean a library reading a control scheme.
 *
 * Synchronous for the same reason [com.dangerfield.drop2048.libraries.billing.Entitlements]
 * is: the question is asked at one moment, and a value that changed mid-run must
 * not retroactively change what was on screen.
 */
interface BannerAds {

    /** Whether the game may put a banner slot on screen right now. */
    fun isAllowed(): Boolean

    /**
     * A banner reported a fill and is on the player's screen.
     *
     * Recorded because "has this player actually been shown an ad" is what the
     * Pro upsell card is gated on since D28, and a banner under the board is the
     * most-seen advertising the app has. Fire and forget: it must never make the
     * screen wait.
     */
    fun noteFilled()
}

/**
 * The answer on a build with no banner inventory: never.
 *
 * Replaced by `RealBannerAds`. The safe answer and the lazy answer are the same
 * one, which is the property [NoInterstitials] has and for the same reason. A
 * format the player did not ask for defaults to absent.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoBannerAds : BannerAds {
    override fun isAllowed(): Boolean = false

    override fun noteFilled() = Unit
}

/**
 * The platform's banner view, drawn in place.
 *
 * A composable on the seam for the same reason [HouseAds.Surface] is one: the
 * only thing that can draw an AdMob `AdView` is a module with the SDK on its
 * classpath, and the screen must be able to ask for a banner without naming it.
 *
 * ### The size contract is the whole design
 *
 * **[Banner] must occupy no layout space until an ad is really on screen.** That
 * is not a nicety, it is the owner's ruling of 2026-09-20 in one sentence: no
 * fill, a network error, ads disabled, Pro, or a platform with no SDK at all
 * must each collapse the strip and let the board have it back. A slot that
 * reserved 50dp for an ad that never arrived would be the "reserve the space
 * from launch so nothing moves" that SPEC 12.4 asked for under the old rule,
 * and under the new one it is exactly the dead space the owner is complaining
 * about.
 *
 * Because the caller puts this after a board that has `weight(1f)`, a zero-height
 * banner *is* the board taking the strip. There is no second rule for the absent
 * case and no `if` to keep in step with it.
 *
 * @param onFilled true when an ad is on screen, false when it goes away or never
 *   arrived. The caller uses it for telemetry and for the "ads seen" record; the
 *   layout does not need it, because the height already said so.
 */
interface BannerSurface {
    @Composable
    fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier)
}

/**
 * The surface on a platform with no ad SDK, which today is iOS and every
 * preview, golden and unit test.
 *
 * It draws nothing and measures zero, so the board takes the strip. That is the
 * iOS answer in full: there is no `IOSAdNetwork` yet (see [NotWiredAdNetwork])
 * and nothing binds a surface there, so the banner cannot appear however the
 * remote keys are set.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoBannerSurface : BannerSurface {
    @Composable
    override fun Banner(onFilled: (Boolean) -> Unit, modifier: Modifier) = Unit
}

/**
 * Defaults to [NoBannerSurface] rather than erroring, so a preview, a golden and
 * a Robolectric test all get a working no-op without providing anything, and
 * the no-op is the "no ad arrived" case, which is the one worth being the
 * default.
 */
val LocalBannerSurface = staticCompositionLocalOf<BannerSurface> { NoBannerSurface() }
