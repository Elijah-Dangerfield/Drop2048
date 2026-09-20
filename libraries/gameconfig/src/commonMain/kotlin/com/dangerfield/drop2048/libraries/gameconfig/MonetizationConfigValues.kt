package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.config.FlagConfigValue
import com.dangerfield.drop2048.libraries.config.IntConfigValue
import com.dangerfield.drop2048.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 10's ad, Pro and kill-switch keys.
 *
 * Every default is the number SPEC 12 states, so the compiled-in behaviour with
 * the server unreachable is the behaviour SPEC 12 describes.
 *
 * ## Declaring a key ahead of its reader is over
 *
 * This file used to open by saying these keys had no consumer yet and that this
 * was the point: `:libraries:ads`, `:libraries:billing` and the leaderboards all
 * landed after C7, and declaring the paths early meant each chunk read a value
 * that already existed rather than inventing one under deadline. Every one of
 * those chunks has now landed.
 *
 * What the practice cost is recorded in D25. Two keys stayed unread after their
 * feature shipped, and neither could be seen: a `ConfiguredValue` with no
 * injection site compiles, passes its own unit test, appears in the QA menu, and
 * is editable in the admin console. `feature.leaderboards` was a kill switch
 * that killed nothing for a full release; `pro.price.tier` was deleted outright,
 * because by the time anyone read it the id it defaulted to had stopped being
 * the product id and wiring it would have revoked Pro from paying players.
 *
 * `ConfigValuesHaveReadersTest` in `androidUnitTest` now fails on a key with no
 * production reader. **Add the reader in the same change as the key**, or do not
 * add the key.
 */

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Ads enabled"
    override val description = "Master kill switch for all advertising, rewarded included."
    override val path = "ads.enabled"
    override val default = true
}

/**
 * SPEC 12.3's first gate: no interstitial before this many runs this session.
 * Four means the 4th run of a session is the earliest one that can carry an ad.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialMinSessionRuns(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: earliest run of a session"
    override val description = "SPEC 12: never before the 4th run of a session."
    override val path = "ads.interstitial.minSessionRuns"
    override val default = 4
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialCooldownSeconds(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: cooldown (seconds)"
    override val description = "Minimum gap since the last interstitial. SPEC 12: 180s."
    override val path = "ads.interstitial.cooldownSeconds"
    override val default = 180
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialRewardedGapSeconds(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: gap around a rewarded ad (seconds)"
    override val description = "Never within this of a rewarded ad, in either direction. SPEC 12: 45s."
    override val path = "ads.interstitial.rewardedGapSeconds"
    override val default = 45
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialSuppressDaysSinceInstall(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: suppress for days after install"
    override val description = "Suppressed entirely for this many days after install. SPEC 12: 3."
    override val path = "ads.interstitial.suppressDaysSinceInstall"
    override val default = 3
}

/**
 * The banner under the board, and the reason it has its own switch.
 *
 * It is the newest and least proven surface in the app, it overturns a rule SPEC
 * 12.4 called load-bearing (D28), and it is the only advertising a player meets
 * without having finished anything. `ads.enabled` would take the rewarded
 * continue down with it, which is a thing players *want*, so killing the banner
 * needs a key that kills only the banner.
 *
 * Defaults on, because the compiled-in behaviour has to be the behaviour the
 * owner asked for with the server unreachable. Off leaves no gap: the strip is
 * the board's again, which is what it is when a banner fails to fill.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BannerEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Banner enabled"
    override val description = "The banner below the board, shown only when the arrow row is not (D28)."
    override val path = "ads.banner.enabled"
    override val default = true
}

/** SPEC 12's Continue placement: 1 free per run, a 2nd at higher friction, hard cap 2. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class RewardedContinuesPerRun(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Rewarded cap: continues per run"
    override val description = "Hard cap on rewarded continues in one run. SPEC 12: 2."
    override val path = "ads.rewarded.continuesPerRun"
    override val default = 2
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ProUpsellEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Pro upsell enabled"
    override val description = "The Settings entry and the once-per-session stacked-out card. SPEC 12."
    override val path = "pro.upsell.enabled"
    override val default = true
}

/**
 * SPEC 10's kill switch for the one feature with a platform dependency.
 *
 * Off means the entry point is not offered, not that a broken screen is shown.
 * It defaults **on**: an unreachable server must leave the game exactly as the
 * binary ships it, and a fallback that silently disabled features would make the
 * offline path a different product.
 *
 * `feature.dailyChallenge` sat beside it until D27 deleted the feature, and the
 * key went with it rather than staying as a switch over nothing.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LeaderboardsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Leaderboards enabled"
    override val description = "Kill switch for Game Center / Play Games boards. Off hides the entry point."
    override val path = "feature.leaderboards"
    override val default = true
}
